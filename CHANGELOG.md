# Changelog

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
