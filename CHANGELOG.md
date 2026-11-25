# Changelog

All notable changes to PureShot are documented here.

## 5.0 — 2026-11-25

### Changed

- Migrated from the legacy `de.robv.android.xposed` API to **libxposed API 102**.
  LSPosed/Vector 2.2+ is required; legacy Xposed builds are no longer supported.
- Module scope is now **System Framework (`system`)** instead of the legacy `android` scope.
  The first install or a scope change still requires one reboot.
- Minimum Android version is now **Android 12 / API 31**.
- Settings are stored in a local mirror and synchronized to `system_server` through
  `RemotePreferences`. Changes apply immediately to existing windows.
- `SKIP_SCREENSHOT` is applied through `SurfaceControl.Transaction` and can be toggled in both
  directions without recreating the affected window.

### Added

- Per-app policy for all six feature groups: **Inherit / Hide / Don’t hide**.
- Per-app master switch: **Inherit / Force on / Force off**.
- App picker with case-insensitive search and three visibility levels:
  launcher apps, user-installed apps, and all apps (disabled apps are marked).
- Per-feature app lists from the Features tab and a dedicated app detail screen.
- Automatic hot reload for module updates and a manual **Reload system hooks** action in
  **Features → About**.
- Sync state reporting and a local-only mode while the framework service is unavailable.
- `QUERY_ALL_PACKAGES` is declared so the app picker can enumerate installed apps.

### Fixed

- Status bar, navigation bar, IME, toast, overlay and other system windows now follow a consistent
  foreground-app policy, including lock screen/AOD fallback to the System UI policy.
- Lock screen and AOD transitions are detected on the main looper, so the System UI policy is
  applied immediately instead of waiting for the next foreground or settings change.
- Reset to defaults clears every per-app override and restores the default app-list filter.

## Earlier releases

See the `v1`–`v4` tags for the previous release history.
