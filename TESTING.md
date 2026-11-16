# Verification

## Version 0.2.6 — Release placeholders

86 JUnit tests pass with `gradlew test jar`. New coverage verifies the Release
menu and callback, exclusion of live items and cell gaps, stale menus and items
returned before a rebuild, right-click/menu selection during Arrange mode,
preserving other positions, re-deposit placement, host/follower acknowledgement,
offline-host behavior, stale/duplicate release requests and stale observation
reports that must not resurrect a released placeholder. Initial item reports now
wait for a current host session/revision before changing its layout.

On 2026-11-16, `gradlew partyRelaySmoke` passed through the real RuneLite Party
service with four independent synthetic connections, including follower release
acknowledgement and subsequently re-adding a genuinely observed item. Normal
storage menu rendering has not yet been verified in a logged-in game client.
Everyone should use 0.2.6 or newer for shared releases; older hosts ignore the new
request type.

## Automated tests

86 JUnit tests cover the model, geometry, native widget integration, input handling,
Party membership adapter and revision protocol. Game APIs and containers are mocked;
tests do not log in or perform game actions.

Cases include withdrawal/return into a different native index, dragging placeholders,
intentional gaps, sparse/empty storage, search fallback, stale withdrawal menus,
character changes, restoring native widgets, bounded imports and consuming Arrange
mode input. Sync tests include concurrent/duplicate drags, dropped broadcasts,
delayed acknowledgements, stale revisions/sessions, offline hosts, saved offline
edits, new items, capacity limits, invalid messages/IDs, party/roster changes,
transport failures and native click-index preservation after received layouts.

Version 0.2.3 adds coverage for missing host layouts, preserving follower copies,
recovery after capture/import, stale or untrusted no-layout replies, restarted hosts,
host/follower setup messages and Arrange checkbox feedback after rejected clicks or
applying sync settings with storage closed. `gradlew test jar` passed for this update.
The earlier real relay smoke test below was not rerun for 0.2.3.

Version 0.2.4 adds next-row placement for newly observed items, ignoring trailing
empty rows while preserving internal gaps and missing-item placeholders. Tests
cover moving the last item down and back up, different grid widths, an idempotent
manual rescan, updates without the storage build event, withdrawal/return index
safety, and three protocol participants converging after a follower discovers new
items. The host's grid width controls shared placement. `gradlew test jar` passed
with 66 tests. This version has not been tested in the live game or on the real
Party relay; the relay evidence below predates this change.

Version 0.2.5 regression tests reproduce a fourth member joining with SneezerDwarf
online as host and Doc Sultan retained in the party but logged out. They also
check that a non-host logout preserves the host session, offline senders are
rejected, rejoining members resume, a logged-out configured host still pauses
followers, and unknown party entries cannot bypass roster checks. The regression
failed on 0.2.4 before the fix. Additional restart coverage checks loading a saved
layout after login without recapture, a new host member ID replacing a retained
logged-out entry, and pausing for two apparently online hosts. All 76 tests and
`gradlew test jar` pass after the fix. These are mocked regression tests, not
verification of the teammates' reported live restart issue.

The fresh-client audit additionally reproduced and fixed two persistence defects:
geometry learned after receiving a shared layout was not always saved, and a
malformed geometry string discarded an otherwise valid layout in memory. Tests
now verify first sync without capture, saved geometry surviving a fresh plugin
instance with sparse storage, malformed geometry preserving layout positions,
and continued native withdrawals after the host leaves. The initial first-sync
test reads saved sync settings automatically without pressing Apply again.

## Version 0.2.5 real relay rerun — 2026-11-15

`gradlew partyRelaySmoke` passed using four independent real Party connections
with synthetic names/items and a mocked GIM roster. A fourth connection with no
saved layout joined, received its first layout while another member was logged
out, and saved it. Restarting the follower and host sync adapters from persisted
layouts completed fresh handshakes; a subsequent fourth-member drag converged
across the online clients. These adapter restarts retained their transports;
they do not prove full OS-process restart behavior or real game UI behavior.

The initial extended test moved before verifying acceptance of the restarted
host's session and correctly encountered stale-edit rejection. The final test
waits for both followers to accept/persist the new session before exercising a
new drag; it does not treat a cached ready status as proof of reconnection.

## Real relay smoke test — 2026-11-15

`./gradlew partyRelaySmoke` passed with three independent instances of RuneLite's
real `WSClient` and `PartyService` connected to the RuneLite Party relay in
disposable private parties. Tested:

- Initial layout reaches both followers.
- Host moves broadcast; follower moves are acknowledged and saved by all three.
- Simultaneous drags produce one accepted change and one conflict rejection.
- Host response timeout pauses follower edits even with a retained roster entry.
- A host edit loaded from a test file replaces old follower layouts on reconnect.
- The production `PartyLayoutSync` adapter accepts synthetic names delivered by
  the real `PartyDataService` against a mocked GIM roster.
- Host and follower moves work through that adapter.
- An offline host edit syncs through the adapter when all join a new shared party,
  without toggling settings.

The test uses synthetic names/items, mocked game APIs and isolated test files.
It does **not** prove behavior with three real logged-in GIM accounts or their UI.
The relay retains disconnected members temporarily; duplicate/unknown entries
intentionally pause the adapter until resolved. Immediate rejoining of the same
party may therefore pause longer than joining a fresh shared party.

## Live game evidence

The user reported that withdrawing the Shadow left a placeholder and moving it
preserved its chosen local position. Teammates without the plugin continued to see
native order, as expected. Local layout dragging was also observed in development
client logs. This is user-reported/manual evidence, not automated coverage of all
storage transactions.

On 2026-11-15, the user confirmed that Party sync with their teammate SneezerDwarf
was working as intended in the published version, with revisions advancing as they
moved items. This does not establish that the earlier waiting status was caused by
a missing host layout. Version 0.2.3 removes visible revision counters and clarifies
setup and missing-layout states; its new UI has not yet been tested live by them.

## Remaining manual acceptance coverage

- Full withdraw-last / deposit-return cycle with moved placeholders on multiple
  real GIM accounts running the plugin.
- Withdraw-X, noted withdrawal, scrolling, search/clear and reopen/restart in the
  current live game interface.
- Three logged-in GIM accounts syncing with real roster data.
- Reconnection after a client crash, game logout or retained duplicate host.
- Interactions with other plugins that modify group storage widgets.

These limits accompany the update submission. The original release was accepted
into Plugin Hub; this does not imply approval of an unreviewed update or complete
live multi-account verification.
