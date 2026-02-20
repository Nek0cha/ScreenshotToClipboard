[日本語] [English](https://github.com/Nek0cha/ScreenshotToClipboard/blob/main/README_EN.md "English README")

# ScreenshotToClipboard

Minecraft **1.21.11** / **Fabric** クライアントMOD。

スクリーンショット（F2）を撮影したときに、保存された画像を **自動でOSのクリップボードへコピー** します。
Windows / macOS / Linux で動作します。

## 機能

- スクリーンショットを撮るだけで自動的にクリップボードへコピー
- **多言語対応**：日本語 / English
- 通知メッセージの表示/非表示を切り替え可能（**デフォルト: OFF**）
  - ModMenu + Cloth Config が入っている場合、ModMenu から設定画面を開けます
  - ない場合でも `config/screenshottoclipboard.json` を編集することで切り替えできます

## 対応環境

- Minecraft: 1.21.11
- Mod Loader: Fabric
- 対応OS: Windows / macOS / Linux

## 使い方

1. MODを導入してゲームを起動
2. F2 でスクリーンショットを撮影
3. 画像がクリップボードに入るので、そのまま Discord / Twitter / 画像編集ソフトなどへ貼り付けできます

## 設定

### ModMenu を使用する場合

- ModMenu のMOD一覧から **ScreenshotToClipboard** を開き、設定画面で変更できます。

### JSON を直接編集する場合

`config/screenshottoclipboard.json`

- `showMessage`: スクリーンショットコピー後のチャット通知を表示するか (true/false)

例：

```json
{
  "showMessage": true
}
```

## Linux について（重要）

Linuxでは環境によってクリップボード連携に外部ツールが必要です。

- Wayland: `wl-copy`（パッケージ: `wl-clipboard`）
- X11: `xclip`

どちらも無い場合、環境によってはコピーに失敗することがあります。

## 開発者向け

### ビルド

```powershell
./gradlew build
```

生成物：

- `build/libs/ScreenshotToClipboard-<version>.jar`

### 仕組み（ざっくり）

- `ScreenshotRecorder` の内部処理を **Mixin** でフック
- 保存されたPNGをバックグラウンドスレッドで読み込み、OSクリップボードに投入

## ライセンス

MIT

