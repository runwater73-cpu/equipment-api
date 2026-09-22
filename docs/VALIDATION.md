# 0.2.0-alpha.1 validation

Minecraft 1.21.1, Java 21, Windows. Core and synthetic Curios tests use NeoForge 21.1.244; original Enigmatic Legacy+ tests use 21.1.249.

## Automated regression matrix

- With Curios 9.5.1+1.21.1: all 192 required GameTests passed.
- Without Curios: all 166 required GameTests passed.
- A normal clean release build passes all 325 JUnit tests with zero failures, errors or skips. A separate no-Curios client/server run confirms grid installation/removal, equipment transfer and item counts without optional-class loading failures.
- The added coverage includes shared player-binding capacity, multiple simultaneous bindings, refusal to bind in transient slots, stale/replayed panel requests, five slots on one template, native overflow returning exactly one item, and refreshing grid metadata after transfer/removal.
- Existing coverage includes custom types/footprints, native validation, ownership through save/copy/rebuild/death, cosmetic overflow, query invalidation and durability-break recovery. Unit coverage includes sidebar layouts from 1 through 256 slots.
- Slot-contract tests exercise public validator queries and equip-event vetoes, native visibility metadata, inactive queries/reactivation, exact functional callback/attribute counts versus cosmetic slots, binding UI `onEquipFromUse`, ordinary and bound-item transformations, explicit native backup/restore with absent armor, private hidden slot opt-out, and slot-extension creative cloning with survival rejection.
- Real synthetic client/server acceptance verifies that only the old menu's managed entries are hidden while native metadata stays unchanged. Native rendering toggles synchronize off/on and actually stop/resume the original renderer; the editor retains the original renderer and saved pose.

## Original addon acceptance

Tests load these unmodified local JARs; none is redistributed:

- Curios 9.5.1+1.21.1
- Enigmatic Legacy+ 1.21.1-1.1.1
- Patchouli 1.21.1-93-NEOFORGE
- Caelus 7.0.1+1.21.1

An actual development client connects to an isolated dedicated server. Explicit server and client PASS markers confirm assertions; successful process exit alone is insufficient.

- Phase 1: UI installation of seven original accessories; original armor/luck/Caelus modifiers; effect removal and restoration; native scroll/spellstone advancement unlocks; original wing-layer preview and server-saved 3D offset.
- Phase 2: actual process restart restores seven accessories; real durability destruction returns each exactly once; Seven Curses ownership survives armor swap, break, naked state, death and respawn.
- Binding acceptance (`PHASE4_PASSED`): the integrated empty-hand assembly UI installs Seven Curses and a second declared binding; ordinary capacity remains shared. The second binding uses the original iron ring with a fixture-only policy, simulating another author opting in. This does not make the iron ring a permanent binding in the release.
- The same run exercises the addon's real `autoEquip` inventory Tick and `ultraHardcore` starter listener without armor, native curse-time progression, survival removal veto, and original Seven Curses/Redemption mutual exclusion.
- Dynamic acceptance uses the real Ascension Amulet and Enchanter Pearl. A fixture-only configuration routes all `charm` slots to the helmet. A chest amulet grants a helmet slot; the helmet pearl adds another. Counts progress 1 → 2 → 3 → 2 → 1 → 2 → 1 across installation, remote-source removal, unequip, re-equip and break. Overflow returns once; the source item and bindings retain the correct owner. The release defaults still route `charm` to the chest unless configured otherwise.
- A subsequent true server/client restart (`PHASE3_PASSED`) restores the naked bound player and original curse time, then removes the ring through the personal panel under the original creative-mode permission, returning exactly one original item.
- Survival unbinding (`PHASE5_PASSED`) uses the addon's real Bless Stone death conversion into Redemption and its Nether lava-pool ritual with Cursed Stone. It checks single player ownership of the replacement without armor, its original removal lock, stopped curse time, native ejection of incompatible cursed accessories, destruction of the binding, and the synchronized empty panel. EnigmaticEye consumes the ritual marker during respawn, so the fixture verifies it before that native dialogue handler. The hardcore direct-use Bless Stone path was source-inspected but not separately exercised.
- Screenshots were inspected for two visible player bindings without equipment, the bound ring after death, and the addon's original elytra model in the 3D editor.
- The final binding/dynamic scenario and true-restart scenario were repeated after the slot-contract fixes. Final Chinese UI checks show the “永恒之契” tab, “「永恒之契，诸界不可断」” panel title, full author-supplied text and non-overlapping slot geometry.

## Reproduction and packaging

Run core tests with `-PincludeGameTests=true runGameTestServer`; add `-PwithoutCurios=true` for the independent API. The ordinary client fixture uses `-PincludeClientTest=true -PwithoutCurios=true`; the synthetic renderer fixture uses `-PincludeClientTest=true -PcuriosSmoke=true`.

For the original-addon fixture add `-PincludeClientTest=true -PenigmaticSmoke=true -Pneo_version=21.1.249 -PcompatTestMods=<local-jar-directory>`. Run phase 1 without a resume flag, then restart both processes with `-PenigmaticResume=true` for phase 2. For the expanded binding/dynamic scenario start both with `-PbindingSmoke=true`; subsequently restart both with that flag plus `-PboundResume=true` to verify saved bindings and release them. Use `-PunbindSmoke=true` for survival conversion/destruction. These scenarios reset their test player and are independent of phases 1/2.

The fixtures expect disposable `run-smoke-server` and `run-smoke-client` directories, a loopback server on `127.0.0.1:25586`, and development authentication settings. Follow Minecraft's server EULA. Start `runSmokeServer`, wait for `Done`, then start `runSmokeClient` with identical properties. Fixtures reset the test player's inventory, may kill/respawn it, and stop the server after logout. Never run them on a real world. Some native addon missing-model warnings also occur with these original development JARs; assertion results and verified screenshots establish the tested behavior.

Use a normal `clean build` without fixture flags or local addon JARs for distribution. Fixture-enabled builds reject JAR packaging. The normal artifact must contain only API code/resources, the license and 0.2.0-alpha.1 metadata; inspect both binary and sources JARs for accidental test or third-party contents.

## Limits

This is an alpha compatibility release, not a claim to cover every addon or long-running multiplayer workload. Special render layers require adapters. Player bindings have no equipment grid or equipment 3D editing. Temporary capacity cannot accept new permanent bindings. Assemblies do not pretend that unworn equipment is active, and native cyclic capacity dependencies are not replaced by a second solver. External grave mods, arbitrary forced insertion, slot-type removal and other custom lifecycle overrides need separate integration tests. Development-save migration is outside the supported upgrade path.
