[日本語](https://github.com/Nek0cha/ScreenshotToClipboard/blob/main/README.md "Japanese README")

# ScreenshotToClipboard

A client-side MOD for **Minecraft 1.21.11 / Fabric**.

When you take a screenshot (F2), the saved image is **automatically copied to your OS clipboard**.
Works on Windows / macOS / Linux.

---

## Features

* Automatically copies screenshots to the clipboard just by taking them
* **Multi-language support**: Japanese / English
* Toggle notification message on/off (**Default: OFF**)

    * If ModMenu + Cloth Config are installed, you can open the settings screen from ModMenu
    * Even without them, you can change the setting by editing `config/screenshottoclipboard.json`

---

## Supported Environment

* Minecraft: 1.21.11
* Mod Loader: Fabric
* Supported OS: Windows / macOS / Linux

---

## How to Use

1. Install the mod and launch the game
2. Press F2 to take a screenshot
3. The image is copied to your clipboard, so you can immediately paste it into Discord / Twitter / image editing software, etc.

---

## Configuration

### Using ModMenu

* Open **ScreenshotToClipboard** from the Mod list in ModMenu and change the settings from the configuration screen.

### Editing the JSON Directly

`config/screenshottoclipboard.json`

* `showMessage`: Whether to display a chat notification after copying the screenshot (true/false)

Example:

```json
{
  "showMessage": true
}
```

---

## About Linux (Important)

On Linux, external tools may be required for clipboard integration depending on your environment.

* Wayland: `wl-copy` (package: `wl-clipboard`)
* X11: `xclip`

If neither is installed, copying may fail depending on your setup.

---

## For Developers

### Build

```powershell
./gradlew build
```

Output:

* `build/libs/ScreenshotToClipboard-<version>.jar`

### How It Works (Overview)

* Hooks into the internal processing of `ScreenshotRecorder` using **Mixin**
* Loads the saved PNG on a background thread and sends it to the OS clipboard

---

## License

MIT
