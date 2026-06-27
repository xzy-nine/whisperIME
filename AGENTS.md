## 项目概述

whisperIME 是一个基于 OpenAI Whisper 引擎的 Android 输入法（IME）项目，支持离线语音识别。

## 构建命令

- **Debug 构建**: `gradlew assembleDebug`
- **Release 构建**: `gradlew assembleRelease`
- **Lint 检查**: `gradlew lint`
- **运行测试**: `gradlew test`
- **安装到设备**: `gradlew installDebug`

## 技术栈

- Kotlin + Android
- 目标 SDK: 35, 最小 SDK: 28
- TensorFlow Lite 2.15.0 (语音识别)
- Android VAD (语音活动检测)
- OpenCC4J (中文转换)
- ViewBinding, Material Design

> 详细项目架构参考见 [`doc/README.md`](doc/README.md)（索引）→ [`ARCHITECTURE.md`](doc/ARCHITECTURE.md) | [`FILES.md`](doc/FILES.md) | [`METHODS.md`](doc/METHODS.md) | [`BUILD.md`](doc/BUILD.md）
