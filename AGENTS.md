# Build and account-manager requirements

- Build the manager as `AccountManager.exe` and the client as `dist/KnightOnline_PhucHso_v2.jar`. Overwrite these canonical outputs; do not create `_fixed`, `_escort`, `_portable`, or other version-suffixed releases.
- If an output is locked, finish validation in `build/` and report that the running application must close before replacement. Do not silently switch release filenames or terminate active games.
- Keep saved account passwords out of edit controls and process command lines. An empty password edit preserves the saved credential; an explicit new value replaces it. Do not add a reveal-password control.
- Validate manager changes with `test-manager.ps1` and game changes with `test.ps1`. Build the manager launcher with `build-manager.ps1`.
- Map travel computes complete routes over `WorldTopology`/`KnownRoutes`. If the origin or expected exit is missing, evaluate live exits by their full remaining route to the goal, excluding a return through the current map. Never choose unknown frontiers for exploration or trust arbitrary old cached shortcuts. Extend the topology from evidence rather than inventing connections.
