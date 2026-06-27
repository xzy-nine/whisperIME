# 构建 & 运行

## 构建命令

| 命令 | 说明 |
|------|------|
| `gradlew assembleDebug` | Debug 构建 |
| `gradlew assembleRelease` | Release 构建 |
| `gradlew lint` | Lint 检查 |
| `gradlew test` | 运行测试 |
| `gradlew installDebug` | 安装 Debug APK 到设备 |

## 技术栈

| 类别 | 内容 |
|------|------|
| 语言 | Kotlin + Java (混合开发) |
| 最低 SDK | 28 |
| 目标 SDK | 35 |
| 构建工具 | Gradle 8.2.2 + Kotlin 1.9.22 |
| UI | Material Design, ViewBinding, ConstraintLayout |
| 语音识别 | TensorFlow Lite 2.15.0 (TFLite Interpreter) |
| VAD | Android-VAD (WebRTC VAD) |
| 中文转换 | OpenCC4J 1.8.1 (简繁转换) |
| 模型来源 | HuggingFace (首次启动下载 ~435MB) |
