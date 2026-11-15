# Group Storage Layouts

Saved visual positions and placeholders for OSRS Group Ironman storage, with
manual sharing and optional RuneLite Party synchronization.

**This changes the view, not the server's storage order.** Each teammate needs
this plugin and the same layout to see the same positions. Native clients keep
showing native order. Install **Group Storage Layouts** from RuneLite's Plugin Hub.

## Arrange storage

1. Open group storage with search cleared and at least two populated rows so the
   plugin can learn the grid spacing.
2. Open the gold grid sidebar panel and click **Capture current arrangement**.
   Capturing again replaces the saved layout.
3. Check **Arrange layout**. Drag items or faded placeholders. Occupied positions
   swap; blank positions preserve gaps. Two extra rows provide expansion space.
4. Turn Arrange off to withdraw/deposit normally. Use the game's **Save** button
   for actual storage transactions. Layout edits save automatically.

Removing the final item leaves a faded zero placeholder. Returning that exact item
ID shows it at the saved position, even if its native index changed. New item types
append after the layout. Different charges, doses and degradation states use
separate positions. Layouts save per RuneScape character profile.

**Show saved layout** off restores the native view without deleting your layout.
Search temporarily uses the native view; clear it to resume the layout.

## Manual sharing

Click **Export layout into text box** and share the complete `GSL1:...` string.
Teammates paste it and click **Import layout from text box** while logged in.
Repeat after changing the shared arrangement. The export contains item IDs and
positions only, with no account credentials.

## Automatic sharing

1. Choose one member as the layout host. Their saved layout is authoritative.
   **The host needs a saved layout:** open group storage and capture its arrangement
   once, or import the desired layout from a teammate. Skip this if already saved;
   capturing again replaces the saved arrangement. Followers do not need to capture.
2. Enable RuneLite's built-in **Party** plugin and join the same private party.
3. Enter the same host character name on every client. Check **Automatically sync
   with party** and click **Apply sync settings**. Sync is off by default; the host
   field initially starts blank.
4. The host should see **Hosting shared layout**; everyone else should see
   **Synced with ...**. GIM membership alone does not connect clients: all must be
   in the same RuneLite Party and have this plugin installed and enabled.

If a follower is waiting, check the host's panel. A host with no saved layout must
capture or import one first. Version 0.2.3 explicitly reports this on both sides
when both clients are updated. Older hosts may remain silent, so a generic waiting
status does not identify the cause. Followers retry every five seconds; no sync
toggle is needed. Revision numbers are internal and are not shown in the panel.

Everyone can drag while connected. Follower moves wait for host acknowledgement.
Conflicting moves are rejected and refreshed instead of overwriting another edit.
Only the host can capture/import a replacement while sync is enabled.

The host can edit outside the party without toggling sync off. Those edits save
locally and are sent when the group joins a valid party. Followers' older copies
do not merge with or overwrite the host. Disconnected followers retain their last
layout and normal item transactions work with Arrange off, but shared editing is
paused. New items may temporarily appear at the end until the host assigns their
shared positions; temporary items cannot be rearranged yet.

Enabling sync backs up the current layout. **Restore pre-sync layout (sync off)**
restores that local backup and disables sync. Re-enabling replaces the backup with
the then-current layout. Simply disabling sync keeps the latest received layout.
There is no automatic host election or queue for disconnected follower edits.
Changing hosts adopts the new host's saved layout.

### Connection and privacy

- Uses RuneLite's existing Party service; no separate server. The plugin does not
  create parties or change membership.
- Every party member must be logged into a character on the same GIM roster.
  Unknown, logged-out or unrelated members pause sharing. Use a private group party.
- Party names are client-reported, not Jagex identity authentication. Keep the
  passphrase private and invite only trusted teammates.
- Messages contain the GIM group name, item IDs, positions and sync metadata, not
  account credentials. RuneLite Party independently shares its usual character data.
- Followers poll every five seconds and pause edits after a 15-second host-response
  timeout. The relay can retain disconnected members temporarily. Duplicate hosts
  or unknown members pause sharing until the roster clears; joining a fresh private
  party together is an alternative.
- New host sessions require a fresh request/response. Older revisions cannot roll
  back newer positions.

## Development

Build target: RuneLite 1.13.6 and Java 11. Hub packaging uses `build=standard`, with
no additional production dependencies.

```sh
./gradlew test jar
```

On Windows, install a Java 11 JDK, set `JAVA_HOME` to it, and double-click
**Launch Group Storage.cmd**. It builds/tests and launches a development client
using normal RuneLite settings/plugins. The launcher also recognizes the original
developer's SDK in the sibling `setup-export-plugin` project. Log in yourself.
Avoid running multiple development builds against the same profile simultaneously.

`scripts/Start-GroupStorage.ps1 -Isolated` uses `.dev-home/.runelite` instead;
`-BuildOnly` builds without opening a client.

The real-network smoke test is opt-in, never part of normal tests or client launch:

```sh
./gradlew partyRelaySmoke
```

It uses three independent RuneLite Party connections in disposable private parties,
synthetic names/items and a mocked GIM roster. It exercises the real relay and
production sync adapter without a game login or real storage transactions. Test
files stay under `build/`; sessions close afterward. See [TESTING.md](TESTING.md).

## Interface safeguards

Native widgets retain item IDs, quantities, indices, actions and listeners. Only
positions, visibility and drag timing change; original values are restored before
storage rebuilds or when disabled. Placeholders are overlays without withdrawal
actions. Arrange mode consumes item input locally and never generates game actions
or storage reorder operations. Native dragging is disabled in the saved view.

Widget IDs/quantities are checked against storage before placement. Filtered or
mismatched interfaces fall back to native view. Stale withdrawal menus are rejected
when their item no longer matches the native index.

If an item/menu looks incorrect, turn **Show saved layout** off and click **Log
storage diagnostics**. Logs are in `%USERPROFILE%/.runelite/logs/client.log` (or the
isolated home). Review logs before sharing: other plugins may include unrelated
account information. Our diagnostics include item IDs and widget geometry.

[BSD 2-Clause license](LICENSE). API references:
[RuneLite Bank Tags](https://github.com/runelite/runelite/tree/master/runelite-client/src/main/java/net/runelite/client/plugins/banktags)
and [RuneLite Party](https://github.com/runelite/runelite/tree/master/runelite-client/src/main/java/net/runelite/client/party).
