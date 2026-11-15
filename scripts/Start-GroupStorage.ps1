param([switch]$BuildOnly, [switch]$Isolated)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$localSdk = Join-Path (Split-Path -Parent $project) 'setup-export-plugin\.tools\jdk\jdk-11.0.33+10'
$jdk = $null
foreach ($candidate in @($localSdk, $env:JAVA_HOME)) {
    if (-not $candidate) { continue }
    $releaseFile = Join-Path $candidate 'release'
    if ((Test-Path -LiteralPath $releaseFile) -and
        (Test-Path -LiteralPath (Join-Path $candidate 'bin\javac.exe')) -and
        (Test-Path -LiteralPath (Join-Path $candidate 'bin\javaw.exe')) -and
        ((Get-Content -LiteralPath $releaseFile -Raw) -match 'JAVA_VERSION="11\.')) {
        $jdk = $candidate
        break
    }
}
if (-not $jdk) { throw 'Install a Java 11 JDK and set JAVA_HOME to its installation folder, then relaunch. See README.md.' }
$env:JAVA_HOME = $jdk
Push-Location -LiteralPath $project
try {
    & .\gradlew.bat test jar writeRuntimeClasspath --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Build/tests failed; client was not launched.' }
    if ($BuildOnly) { return }
    $classPath = (Get-Content -LiteralPath 'build\runtime-classpath.txt' -Raw).Trim()
    $arguments = @('-ea')
    if ($Isolated) {
        $testHome = Join-Path $project '.dev-home'
        New-Item -ItemType Directory -Path $testHome -Force | Out-Null
        $arguments += ('-Duser.home="' + $testHome + '"')
    }
    $arguments += @('-cp', ('"' + $classPath + '"'), 'com.docsultan.groupstorage.GroupStorageLauncher', '--developer-mode')
    $process = Start-Process -FilePath (Join-Path $jdk 'bin\javaw.exe') -ArgumentList $arguments -WorkingDirectory $project -WindowStyle Hidden -PassThru
    Set-Content -LiteralPath 'build\dev-client.pid' -Value $process.Id
    "Started Group Storage Layouts development client (process $($process.Id), isolated settings: $Isolated)."
    'Log in yourself, open group storage, then select the gold grid icon in the RuneLite sidebar.'
} finally { Pop-Location }
