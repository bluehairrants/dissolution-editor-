# Third-Party Notices

This prototype calls or depends on the following projects. Their source code and UI are not copied into this repository unless explicitly stated.

- [FadCam](https://github.com/anonfaded/FadCam), GPL-3.0. Optional external recording app; Dissolution Editor imports its saved recordings through Android's document picker.
- [Seal](https://github.com/JunkFood02/Seal), GPL-3.0. Optional external downloader; AI `download_video` actions use Seal's exported `ACTION_SEND` quick-download activity. The user confirms the download in Seal.
- [FFmpegKit](https://github.com/arthenica/ffmpeg-kit), LGPL-3.0-or-later / GPL-3.0-or-later depending on package configuration. Used for local H.264 export.
- [Retrofit](https://github.com/square/retrofit), Apache-2.0. Used for the BossAI-compatible HTTP client.

BossAI is configured as an external API service. No BossAI source repository, API host, or license was identified in the starter project; no BossAI implementation is included here.