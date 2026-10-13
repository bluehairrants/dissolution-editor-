# Dissolution Editor

An Android video-editing prototype that brings together clip capture/import, an AI assistant, and a downloader handoff.

## Included

- A Jetpack Compose clip workspace with video preview, import, and FFmpegKit export.
- A Retrofit chat client with an OpenAI-compatible BossAI endpoint configuration. The actual BossAI host and request contract were not present in the starter repository; configure them with Gradle properties.
- AI `download_video` tool calls are handed to Seal's exported quick-download activity. Seal opens its own download/format confirmation screen; it does not silently download in the background.
- An optional FadCam launcher. Record in FadCam, return, then import the recording into the workspace.
- Third-party project and library credits in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Configure BossAI

Pass the endpoint, model, and API key when building. The endpoint must be a base URL ending in `/`, and the service must accept an OpenAI-compatible `/chat/completions` request with tool calling enabled.

```sh
gradle :app:assembleDebug \
	-PbossAiBaseUrl=https://your-bossai-host/v1/ \
	-PbossAiModel=your-model \
	-PbossAiApiKey=your-key
```

Do not commit API keys or put them in a distributed client build. A production app should proxy AI requests through a server that owns the secret.

## Build

Open this directory in Android Studio or run `gradle :app:assembleDebug` with a compatible JDK and Android SDK installed.

This is a starter project, not a production editor. Video downloads and edits must respect each source site's terms and the rights attached to the content.
