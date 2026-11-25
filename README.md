# PureShot

> Hide status bar, navigation bar, popups, PiP, toast & more. Capture pure app screenshots on
> Android.

![Anti-996 License](https://img.shields.io/badge/license-Anti--996%20License-blue)

`Issue`、`Pull Requests`、`Star`、`Fork` are all welcomed.

Tired of messy screenshots cluttered with status bars, navigation bars, floating windows and overlay
elements?

PureShot is the ultimate plugin that removes **_ALL_** floating/overlay UI during screenshot,
leaving you with a clean, pure app shot only.

> [!IMPORTANT]
> PureShot controls what appears in **screenshots and screen recordings** through
> `SKIP_SCREENSHOT`. The live screen keeps its system bars; PureShot does not immersive-hide them.

## 🆕 What's new

* **libxposed API 102 only** — the legacy Xposed API and `XSharedPreferences` are gone. LSPosed or
  Vector 2.2+ is required and the module scope is now **System Framework (`system`)**.
* **Android 12+** — API 31 is the new minimum; Android 10/11 paths were removed.
* **Real-time settings** — changes are mirrored through `RemotePreferences` and applied to existing
  system windows immediately. No System UI restart and no reboot.
* **Per-app configuration** — every feature has an **Inherit / Hide / Don’t hide** override, plus a
  per-app **Inherit / Force on / Force off** master switch.
* **App picker** — case-insensitive search, three visibility levels, disabled-app markers and
  PureShot excluded from the list.
* **Hot reload** — installing an updated APK swaps the module code inside `system_server`; the same
  swap is available from **Features → About → System hooks**.
* **Dynamic `SKIP_SCREENSHOT`** — system windows are re-evaluated both ways, so a “Don’t hide”
  override works without recreating the window.

See [CHANGELOG.md](CHANGELOG.md) for the full release notes.

## 🌟 Core Feature

No extra UI, no overlays, just the pure app content you want.

Ideal for app previews, UI design, tutorials and clean screenshot sharing!

## ✨ What it hides

* Status Bar
* Navigation Bar
* Notification Popups & Floating Notifications
* Mini Floating Windows
* Dynamic Island Capsule
* Picture-in-Picture (PiP)
* Toast
* All other overlay/floating elements

## 📱 Requirements

* Android 12+ (API 31). Android 10/11 are no longer supported.
* LSPosed or Vector 2.2+ with **libxposed API 102**.
* Module scope: **System Framework (`system`)**, not the legacy `android` scope.

After installing or changing the scope, reboot once so the module is injected into `system_server`.
After that, changing settings does **not** require a System UI restart or a reboot.

Once the module is running in `system_server`, updating the APK also does **not** require a reboot:
`autoHotReload` replaces the module code in place. The same swap can be triggered manually from
**Features → About → System hooks → Reload system hooks**.

## ⚙️ Settings

PureShot has two tabs.

### Features

* **Enable PureShot** — global master switch and hard gate. When it is off, no app hides anything.
* Six global switches: status bar, navigation bar, input method, toast, floating windows, other
  system UI.
* Every feature row has **Configure per app**, which opens a filtered app list for that feature.

### Apps

* Search by app name or package name, case-insensitive.
* Visibility levels:
  1. **Apps with a launcher icon** (default)
  2. **User-installed apps** (excludes system and updated-system apps)
  3. **All apps** (includes system and disabled apps; disabled apps are marked)
* PureShot itself is always excluded.
* Each row has an app-level three-state switch: **Inherit / Force on / Force off**.
* Tap an app to open its detail page:
  * app-level master switch;
  * six per-feature overrides (**Inherit / Hide / Don’t hide**);
  * **Clear app overrides**.

### How overrides combine

* The global master is a hard gate: `Enable PureShot` off means everything is off.
* **Force off** on an app disables all hiding for that app.
* **Force on** only lifts an app-level stop; it does not turn on features that are globally off.
* Feature overrides win over the global feature switch in both directions.
* Apps without overrides follow the global switches.
* Lock screen/AOD and other no-foreground states use the `com.android.systemui` per-app policy.

### Reset

**Reset to defaults** resets all global switches, clears every per-app override and restores the
default app-list filter. The app detail page also has a scoped **Clear app overrides** action.

## 🔄 Real-time settings

Settings are stored locally and mirrored to the framework through libxposed `RemotePreferences`.
When the framework service is connected, changes are applied to existing system windows
immediately. If the service is unavailable, edits remain in the local mirror and are pushed the next
time it connects (the UI shows “Waiting for framework sync”).

## ⚡ Hot reload

* `module.prop` declares `autoHotReload=true`. Installing an updated APK swaps the module code
  inside `system_server` without restarting the process or the device.
* **Features → About → System hooks → Reload system hooks** performs the same swap on demand and
  shows the framework result (success, failed, unsupported, already running, target missing, etc.).
* The button stays disabled with an explanation when the framework service is not connected, the
  framework API is older than 102, or no `system` target is running.
* Existing `SKIP_SCREENSHOT` flags are left untouched during the swap. After reload, every existing
  window is re-registered from `WindowManagerService.mWindowMap`, so status/navigation bars keep
  following the current per-app policy.
* Hot reload only replaces code inside an already injected `system_server`. The first install or a
  scope change still requires one reboot.

## 🔒 Privacy

Settings never leave the device. PureShot has no network access. It declares only
`QUERY_ALL_PACKAGES` so the app picker can list installed applications.

## ❤️ Final

Enjoy :)
