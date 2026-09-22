# Changelog

## 0.2.0-alpha.2

- Show localized Curios slot types, original accessory descriptions and current native removal conditions in component details. Keep internal editor slot IDs behind advanced tooltips.
- Add per-accessory native attribute modifier sections to the attribute details page, using the installed type/index and original tooltip hooks. Keep equipment values separate; this display never applies gameplay modifiers or ticks.
- Render original item models for armor accessories without a wearable renderer. Reuse the same fallback in the editor and native world layer, including saved poses, selection, visibility and native render toggles. Preserve registered Curios renderers and external-layer adapters.
- Filter the Eternal Covenant panel to actual player bindings and free stable indices that accept declared bindings. Ordinary armor accessories no longer appear there; transient capacity remains on the corresponding equipment template.

## 0.2.0-alpha.1

- Add optional Curios 9.5.x integration for NeoForge 1.21.1. Armor stores the original accessory items and provides the worn contents to the native Curios inventory, tick, attribute, event and synchronization paths.
- Use Curios and addon-defined slot types on managed armor, replacing empty built-in armor slot presets. Preserve template boards/body shapes and addon-authored slots. Keep the standalone API's presets when Curios is absent.
- Route manual accessory installation through the assembly screen. Disable native right-click quick-equip without canceling unrelated item use; preserve explicit mandatory player-binding grants.
- Support datapack and Java routing, per-type default footprints, individual-item footprint overrides, excluded equipment and explicit template restrictions. Reuse native slot validators, names and icons.
- Preserve one storage owner through native rebuild, save, copy, unequip, slot shrink and death. Default death drops stay in armor; explicit native keep/drop/destroy rules remain effective.
- Add shared durability-break recovery for ordinary and Curios components, with `EquipmentBreakEvent` policies. Return parts to inventory/drop them when full; preserve the host at one durability when restoration is unavailable or the event cancels.
- Reuse registered Curios renderers in the 3D editor and standard world layer. Preserve renderer identity and animations; provide client frame calibration for custom attachments.
- Cache generated definitions, per-tick template preparation and visible-slot input inspection. Invalidate Curios query caches when armor changes within a tick.
- Keep each native slot type on one armor category as capacity grows; retain deterministic slot order when items are installed. Validate unworn armor against a read-only hypothetical wearer, preserving addon duplicate-item and prerequisite rules.
- Add a paged player-binding panel inside the assembly screen, usable without equipment. Share native capacity, accept multiple declared bindings, and reject new permanent bindings in transient slots. Seven Curses and Redemption remain in native player storage across armor swaps and breakage, with original removal, wear-time and death rules.
- Adapt Enigmatic Legacy+ elytra previews/world offsets using its own render layer, and prevent its forced-equip paths from consuming rejected items. Local third-party test JARs are not redistributed.
- Add tests with and without Curios and a real client/server Curios editor acceptance fixture. Update the network protocol to reject mixed 0.1/0.2 clients.
- Preserve native slot visibility/validation queries while hiding only managed menu entries. Respect native ordering and opt out of private hidden types unless explicitly routed.
- Bridge slot-extension display, empty tooltips and creative cloning; expose native inactive state and render toggles. Preview dynamic extra indices using native capacity and render flags.
- Preserve native replacement/consumption and explicit inventory backup/restore; recover incompatible transformed items without duplicating the consumed original. Verify native callback/attribute counts, cosmetic semantics and inactive-slot queries.

## 0.1.2

- Fix the 3D placement editor crash when a component is selected. Resource-location IDs are now passed to translated UI text as strings, which is the type accepted by Minecraft's translation component system.

## 0.1.1

- Resize the component sidebar to its actual row count (four columns, up to six visible rows), with matching hit areas and scrolling for larger lists. Component details follow the resized panel.
- Redraw all 15 empty-slot icons as transparent 16px monochrome outlines based on the supplied silhouettes.
- Reuse sidebar geometry, render only visible slots, resolve the carried component once per frame, refresh idle display callbacks once per client tick, and avoid per-panel sizing-array allocations.
- Keep the equipment input panel and its visible slot frame when the equipment is removed; show the empty-input hint.
- Move the actual container slot into the left preview panel so item drawing, hover highlighting, clicks, and vanilla inventory gestures share one location.
- Extend real client/server acceptance to equipment pickup, reinsertion, the inactive former center position, and Shift transfers without item loss or duplication.

## 0.1.0

First public baseline for Minecraft 1.21.1 and NeoForge 21.1.244.

- Load all template boards before server login synchronization; send the current grid definition snapshot before opening the assembly screen.
- Keep data registry boards separate from Java registrations. Replacing the active registry removes stale template boards and preserves code defaults.
- Use the grid workspace as the assembly UI. Remove the ring layout, old node pagination, UI position APIs, and deprecated renderer providers.
- Synchronize complete menu state before grid and component-space acknowledgements, preventing a cursor update from making the next action appear stale.
- Include all 15 supplied empty-slot icons, with transparent backgrounds and no image labels.
- Preserve the 21 equipment template presets, 15 installation-point categories, and the equipment-specific body silhouettes.
- Require explicit component interface IDs in persisted data and current appearance resource format 3. Reject populated grid snapshots without saved coordinates instead of automatically repacking old parts.
- Keep the server's install, replace, remove, attribute, behavior, appearance, custom component-space, and spatial-rule extension APIs.
- Remove the retired Demo dependency and its acceptance harness. Test fixtures are opt-in and cannot be packaged as release JARs.
- Add an isolated client/server smoke test for the built-in sword template, three-cell component installation, removal, and cursor synchronization.
- Publish Java 21 build instructions, MIT license, sources JAR, and GitHub CI.

Development releases are not a supported upgrade path. Start a new test world and build content mods against the 0.1.0 API. Custom schema migrations remain an explicit extension point for content authors; the API does not automatically migrate development saves.
