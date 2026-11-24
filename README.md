# PureShot

> Hide status bar, navigation bar, popups, PiP, toast & more. Capture pure app screenshots on
> Android.

![Anti-996 License](https://img.shields.io/badge/license-Anti--996%20License-blue)

`Issue`、`Pull Requests`、`Star`、`Fork` are all welcomed.

Tired of messy screenshots cluttered with status bars, navigation bars, floating windows and overlay
elements?

PureShot is the ultimate plugin that removes **_ALL_** floating/overlay UI
during screenshot, leaving you with a clean, pure app shot only.

> [!note]
> On Android 10-11, PureShot can hide the status bar and navigation bar, but it cannot hide floating
> windows or some overlay elements due to system
> limitations ([issues#2](https://github.com/kazutoiris/PureShot/issues/2)). For Android 12 and
> above, PureShot can hide all floating/overlay elements.

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

## ⚙️ Settings

PureShot includes a small settings app (launcher icon) for choosing what gets hidden:

* **Enable PureShot** — master switch; turn it off to disable all hiding.
* **Status bar & notifications** — status bar, heads-up notifications and dynamic island.
* **Navigation bar** — navigation/gesture bar.
* **Input method** — on-screen keyboard.
* **Toast** — toast messages.
* **Floating windows & dialogs** — floating windows, PiP/mini windows and system dialogs.
* **Other system UI** — volume panel, power menu, screenshot animation and similar.

All switches are **on by default**, so existing behavior is unchanged. Toggles apply to windows
created after the change; the status bar and navigation bar need a System UI restart (or a reboot)
before screenshots reflect the new state. If a bar still appears in a screenshot right after a
reboot, restart System UI once.

There is no separate PiP switch: PiP and mini floating windows are controlled by the
**Floating windows & dialogs** toggle. Notification popups and the dynamic island are controlled by
**Status bar & notifications**.

Settings are stored only on the device. PureShot requests no permissions and has no network access.

## ❤️ Final

Enjoy :)
