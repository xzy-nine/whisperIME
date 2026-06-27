# 项目架构

## 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                       入口层 (UI/Service)                        │
│                                                                   │
│  DownloadActivity   MainActivity   WhisperInputMethodService    │
│  (#模型下载/更新)   (#主界面)       (#IME键盘输入法)            │
│                                                                   │
│  WhisperRecognizeActivity   WhisperRecognitionService            │
│  (#系统语音弹窗)           (#系统级语音识别服务)                 │
│                                                                   │
│  WhisperRecognitionServiceSettingsActivity                       │
│  (#识别服务设置)                                                  │
└──────────────────────────┬──────────────────────────────────────┘
                           │ 调用
┌──────────────────────────▼──────────────────────────────────────┐
│                      ASR 层 (语音识别)                           │
│                                                                   │
│  Recorder ──▶  AudioRecord(16kHz/16bit) + VAD + Bluetooth SCO   │
│     │                                                             │
│     ▼                                                             │
│  RecordBuffer ──▶ 线程安全PCM缓冲区 + pcmToFloat()               │
│     │                                                             │
│     ▼                                                             │
│  Whisper ──▶ 协调模型加载/卸载、异步转写/翻译、监听器回调      │
│     │                                                             │
│     ▼                                                             │
│  WhisperResult ──▶ text + detectedLanguage + type                │
└──────────────────────────┬──────────────────────────────────────┘
                           │ 调用
┌──────────────────────────▼──────────────────────────────────────┐
│                   引擎层 (TFLite 推理)                           │
│                                                                   │
│  WhisperEngine (接口) ──── WhisperEngineJava (实现)             │
│  ├── init()                  ├── 加载 .tflite → Interpreter      │
│  ├── deinit()                ├── 关闭 Interpreter               │
│  └── processSamples()        └── Mel频谱→推理→Token解码→文字  │
└──────────────────────────┬──────────────────────────────────────┘
                           │ 依赖
┌──────────────────────────▼──────────────────────────────────────┐
│                    工具层 (Utils)                                 │
│                                                                   │
│  WhisperUtil ──▶ 词汇加载/滤波器加载/Mel频谱图/FFT              │
│  Downloader  ──▶ 模型下载/MD5校验/assets文件复制                │
│  InputLang   ──▶ 99种语言代码↔TokenID映射                       │
│  LanguagePairAdapter ──▶ Spinner语言列表适配器                   │
│  HapticFeedback ──▶ 触觉反馈震动                                │
│  ThemeUtils  ──▶ 状态栏主题设置                                 │
└─────────────────────────────────────────────────────────────────┘
```

## 完整文件层级

```
app/src/main/
├── AndroidManifest.xml
├── assets/
│   ├── filters_vocab_en.bin              # 英文滤波器词汇表
│   └── filters_vocab_multilingual.bin    # 多语言滤波器词汇表
│
├── java/com/whispertflite/
│   ├── DownloadActivity.kt               # 模型下载/更新页
│   ├── MainActivity.java                 # 主界面（录音+转写/翻译）
│   ├── GithubStar.java                   # GitHub 加星提示
│   ├── WhisperInputMethodService.java    # IME 输入法服务
│   ├── WhisperRecognizeActivity.java     # 语音识别弹窗 Activity
│   ├── WhisperRecognitionService.java    # Android RecognitionService
│   ├── WhisperRecognitionServiceSettingsActivity.java  # 识别服务设置
│   │
│   ├── asr/                              # 语音识别核心层
│   │   ├── Recorder.java                 # 音频录制器
│   │   ├── RecordBuffer.java             # PCM 缓冲区
│   │   ├── Whisper.java                  # 识别协调类
│   │   └── WhisperResult.java            # 识别结果数据类
│   │
│   ├── engine/                           # 推理引擎层
│   │   ├── WhisperEngine.java            # 引擎接口
│   │   └── WhisperEngineJava.java        # TFLite 引擎实现
│   │
│   └── utils/                            # 工具层
│       ├── Downloader.java               # 模型下载/MD5校验
│       ├── HapticFeedback.java           # 触觉反馈
│       ├── InputLang.java                # 语言代码↔TokenID 映射
│       ├── LanguagePairAdapter.java      # Spinner 语言列表适配器
│       ├── ThemeUtils.java               # 状态栏主题工具
│       └── WhisperUtil.java              # 频谱分析/FFT/词汇加载
│
└── res/
    ├── drawable/                         # 矢量图标 (20个)
    ├── layout/                           # 布局文件 (5个)
    ├── xml/                              # 配置元数据 (3个)
    ├── mipmap-anydpi-v26/                # 启动图标
    ├── values/                           # 默认资源 (4个)
    ├── values-night/                     # 夜间模式颜色
    └── values-*/                         # 多语言翻译 (14种语言)
```
