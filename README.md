# Dissolution Editor

An Android video-editing prototype that brings together clip capture/import, an AI assistant, and a downloader handoff.

## Included

- A Jetpack Compose clip workspace with video preview, import, trim, and Media3 hardware-accelerated export.
- A Retrofit chat client with an OpenAI-compatible BossAI endpoint configuration. Configure its HTTPS base URL, model, and API key in the app's settings; the key is encrypted with Android Keystore.
- AI `download_video` tool calls are handed to Seal's exported quick-download activity. Seal opens its own download/format confirmation screen; it does not silently download in the background.
- AI trim and export-profile tool calls apply to the current edit. The app checks the source clip and Android hardware encoder before enabling a profile.
- An optional FadCam launcher. Record in FadCam, return, then import the recording into the workspace.
- Android export profiles support source size, 1080p, and 4K; H.264 or HEVC; and up to 150 Mbps where the device encoder supports the exact profile. FPS is limited to the imported source's frame rate, so 120 fps requires 120 fps source footage.
- Third-party project and library credits in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Configure BossAI

Open the gear icon in the app and enter the HTTPS API base URL, model, and API key. The server must accept an OpenAI-compatible `/chat/completions` request with function calling. The key is stored encrypted on-device. For production services, a server-side proxy remains preferable to distributing client credentials.

## Build

Install Android Studio or use JDK 17+ and Android SDK 35, then run `./gradlew :app:assembleDebug`. The installable testing APK is also copied to [DissolutionEditor-arm64-debug.apk](DissolutionEditor-arm64-debug.apk) and targets ARM64 Android 10 and newer.

Pushing to `main` builds a downloadable APK artifact in GitHub Actions. Pushing a tag such as `v0.1.0` also attaches the APK to a GitHub Release. These APKs are debug-signed for testing, not production distribution.

The Android APK is not an iOS app. An iPhone/iPad build requires a separate Apple app target, Xcode on macOS, and Apple signing/provisioning for an installable IPA.

This is a starter project, not a production editor. Video downloads and edits must respect each source site's terms and the rights attached to the content.
