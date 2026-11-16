# Verification

## Automated tests

66 JUnit tests cover the model, geometry, native widget integration, input handling,
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
