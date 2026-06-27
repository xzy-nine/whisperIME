# 文件功能介绍

## 入口层 (java/com/whispertflite/)

| 文件 | 类型 | 功能描述 |
|------|------|----------|
| `DownloadActivity.kt` | Activity | 首次启动时下载/更新 Whisper TFLite 模型，下载完成后跳转 MainActivity |
| `MainActivity.kt` | Activity | 主界面：模型选择(英文/多语言)、语言选择(99种)、录音按钮、转写/翻译结果显示、中文简繁转换、TTS朗读 |
| `WhisperInputMethodService.kt` | InputMethodService | **核心 IME 实现**：虚拟键盘布局、录音控制、自动模式(VAD)、转写/翻译、退格/回车/复制等操作 |
| `WhisperRecognizeActivity.kt` | Activity | 系统语音输入弹窗（底部弹出），实现 RecognizerIntent.ACTION_RECOGNIZE_SPEECH |
| `WhisperRecognitionService.kt` | RecognitionService | Android 系统级语音识别服务，可在系统设置中选为默认语音输入 |
| `WhisperRecognitionServiceSettingsActivity.kt` | Activity | 识别服务的设置页：模型选择、语言选择、中文模式 |
| `GithubStar.kt` | Dialog | 版本升级后弹出一次对话框，邀请用户到 GitHub 加星 |

## ASR 层 (asr/)

| 文件 | 类型 | 功能描述 |
|------|------|----------|
| `Recorder.kt` | 录音器 | 使用 `AudioRecord` 录制 16kHz/16bit 单声道 PCM，支持 VAD（语音活动检测）、蓝牙 SCO 免提模式 |
| `RecordBuffer.kt` | 缓冲区 | 线程安全的字节数组静态缓冲区，提供 `pcmToFloat()` PCM→float 归一化方法 |
| `Whisper.kt` | 协调器 | 管理模型生命周期（加载/卸载），启动后台线程执行转写或翻译，通过监听器回调结果 |
| `WhisperResult.kt` | 数据类 | 封装：`text`(文字)、`detectedLanguage`(检测到的语言)、`type`(TRANSCRIBE/TRANSLATE) |

## 引擎层 (engine/)

| 文件 | 类型 | 功能描述 |
|------|------|----------|
| `WhisperEngine.kt` | 接口 | 定义三个方法：`init()`, `deinit()`, `processSamples()` |
| `WhisperEngineJava.kt` | 实现 | 基于 TFLite Interpreter，加载 .tflite 模型，计算 Mel 频谱图，执行推理并解码 Token ID→文字 |

## 工具层 (utils/)

| 文件 | 类型 | 功能描述 |
|------|------|----------|
| `WhisperUtil.kt` | 工具 | 加载词汇表/滤波器 .bin 文件、计算 Mel 频谱图（含 FFT 和 Hanning 窗）、提供 FFT 实现 |
| `Downloader.kt` | 下载 | 从 HuggingFace 下载 3 个模型文件（.tflite），验证 MD5，复制 assets 中 .bin 文件，删除旧模型 |
| `InputLang.kt` | 映射 | 99 种语言的 ISO 代码 ↔ Whisper Token ID 映射表，提供查找方法 |
| `LanguagePairAdapter.kt` | 适配器 | 继承 `ArrayAdapter`，在 Spinner 中展示"语言代码 - 显示名称"格式 |
| `HapticFeedback.kt` | 反馈 | 根据设备是否支持 `VibratorManager` 触发 HEAVY_CLICK 震动 |
| `ThemeUtils.kt` | 主题 | 设置状态栏图标为深色或浅色（根据日夜模式） |
