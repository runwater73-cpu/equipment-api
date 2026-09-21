# Changelog

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
