# 0.1.1 validation

Validated on Minecraft 1.21.1, NeoForge 21.1.244, and Java 21 on Windows.

- `gradlew.bat clean build`: 325 unit tests passed, no failures or skips.
- `gradlew.bat -PincludeGameTests=true runGameTestServer`: all 162 required GameTests passed.
- A real client connected to an isolated dedicated server: the sword board was synchronized on first open; a three-cell L-shaped component installed through the sidebar, removed through the sidebar, cleared its stale detail selection, and returned to inventory. The equipment was also picked up, reinserted, Shift-transferred in both directions, and checked for loss or duplication. Clicking the retired center position did not insert the carried equipment. The client logged `RELEASE_SMOKE_PASSED`.
- Sidebar geometry tests cover every slot count from 1 through 256, including the final scroll row, shrinking panels, and inactive blank space. Icon files were verified to have native 16×16 dimensions, a single gray, and binary alpha.
- Release JAR and sources JAR were inspected for test-fixture and retired UI classes. The normal JAR contains the 21 template presets, 15 slot icon PNGs, MIT license, and version 0.1.1 metadata.

The smoke fixture lives in `src/clientTest` and is enabled only with `-PincludeClientTest=true`. It expects an isolated server in `run-smoke-server` bound to `127.0.0.1:25586`, with online authentication disabled for the development client. Follow Minecraft's server EULA setup before running it. Start `runSmokeServer`, wait until ready, then start `runSmokeClient`. The fixture resets its test player's inventory and stops the test server after logout; use only a disposable test world. The scripted screen blocks physical mouse/key events in that isolated test window. Check the explicit PASS/FAILED log marker; a Minecraft exit code alone is not an assertion result.

After fixture runs, use a normal `clean build` without either fixture property to produce release artifacts. Builds with fixture properties refuse JAR packaging.

These results do not establish compatibility with every third-party mod or prolonged multiplayer load. Development saves are outside the supported upgrade path.
