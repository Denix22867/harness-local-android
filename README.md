# Harness Local for Android

**English** · [Русский](README.ru.md)

An experimental, unofficial Android port of [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness). Published with source code, Android patches, and build instructions. No root access or separate Termux installation is required.

**[Download the APK from Releases](https://github.com/Denix22867/harness-local-android/releases/latest)** · [Build instructions](docs/BUILD.md) · [Third-party licenses](THIRD_PARTY.md)

## Features

- Ubuntu ARM64, Node.js 24.9.0, and Harness 0.2.0-rc.2 running locally under PRoot.
- File tools, Bash, Git, Python, ripgrep, and pnpm.
- DeepSeek account sign-in using the upstream authentication flow.
- Custom OpenAI-compatible providers: enter a Base URL, API key, and Model ID. Harness sends tools to models that support tool calling.
- SSH through Bash: `ssh`, `scp`, `sftp`, `ssh-keygen`, and `sshpass`.
- A mobile interface and file import/export through Android's system picker.
- Open HTML files with one tap in the Harness file tree, chat file links, or the app's **Files** menu. The preview runs JavaScript and loads relative CSS/JS assets in a separate WebView.
- An agent-controlled browser using Android WebView: open pages, inspect the DOM, click, type, send keys, run JavaScript, collect console errors, and capture the rendered viewport plus separate canvas pixel images.
- Browse and export files from both `/workspace` and `/root`, including projects created in the home directory.
- A PRoot directory-entry fix that makes files created by the write tool visible to glob and grep without forcing searches to follow genuine symbolic links.

## Installation and updates

1. Download `Harness-Local-Mobile.apk` from the latest release and install it.
2. On first launch, the app downloads the runtime archive (about 393 MiB) from GitHub Releases, verifies its SHA-256, and extracts it. An internet connection and at least 2 GB of free storage are required.
3. Open **«Модели» (Models)** to add a provider, or sign in to your DeepSeek account. Cloud models require internet access; language model weights are not bundled with the app.

To update, install the new APK over the existing app. APKs published in this repository use the same signing key, preserving the installed runtime, files, account, and settings. **Do not uninstall the app or clear its data** if you want to keep them. A self-built APK signed with a different key cannot be installed over the published APK.

RouterAPI example: Base URL `https://routerapi.ru/api/v1`, Model ID `z-ai/glm-5.3`, and your API key from the provider's dashboard. Tool calling support depends on the model and provider.

If you see **Output token limit reached**, open **Models → Edit → «Лимит ответа (токены)» (Output token limit)**. The default is 8192; try 32768 for long coding tasks if your model supports that limit, then save. Some models count reasoning toward the output budget. You pay for actual token usage, so raising the limit can increase costs. Provider limits still apply.

To browse projects, choose **⋮ → «Файлы» (Files)** and select `/root` or `/workspace`. Tap an HTML file to run it. To save a file to your phone, choose **⋮ → «Экспортировать файл» (Export file)**, select either directory, then choose a destination in Android's system picker. HTML previews serve files from the HTML file's directory and its subdirectories; they do not start a Node/Python backend or a development server.

To let a model inspect screenshots, enable **Models → Edit → «Модель принимает изображения» (Model accepts images)** for a model whose API supports image input, then save and restart. Ask it to use the `browser` tool to open a local HTML path or an HTTP(S) URL. The agent browser renders on a private virtual display and does not cover the chat or take its input focus. Keep Harness running during checks. Text-only models can inspect the DOM and console, but cannot see the image; Canvas images are read back automatically because Android WebView can omit accelerated canvas layers from viewport captures; these separate images show canvas pixels, not proof of their final composition into the page. PNG files are also saved under `/root/.harness-browser/screenshots/`. Use **⋮ → «Сбросить браузер агента» (Reset agent browser)** to release a stuck session. The browser uses the installed Android WebView; no separate Chromium download is needed.

## Limitations

- Android 9+ (min SDK 28), ARM64 only. The app targets SDK 28 to allow execution of installed Linux tools; Android may show an outdated target SDK warning.
- This is a partial port of the desktop app. The system tray, desktop computer-use, capture of other apps or the system screen, and control of other apps are not implemented.
- bwrap/Landlock availability depends on the phone's kernel. Harness permission settings are not automatically disabled.
- HyperOS may stop background processes. Allow background activity in battery settings if needed.
- A user has confirmed startup on their device. Automated checks used Linux/QEMU and an emulator for the Android interface. Not all models or devices have been tested.

## Source code and licensing

Our Android code and patches are MIT licensed. Third-party components retain their own licenses, including GPL for PRoot and sshpass. The modified PRoot source, patch, and build recipe are available in `third-party-sources/` and `scripts/build_proot_android.py`. The upstream Harness license is preserved.

API keys, user data, and the private signing key are excluded from the repository. The app's open-source license does not make external APIs free or change the license of your chosen language model.

## Development

See [docs/BUILD.md](docs/BUILD.md) for build instructions and [docs/HISTORY.ru.md](docs/HISTORY.ru.md) for the change history; these documents are currently in Russian. The APK uses AAPT2, ECJ, D8, zipalign, and apksigner without Gradle.

Issues and pull requests are welcome. When reporting a bug, include your device model, Android version, and relevant logs with tokens removed.
