# 公共方法详解

## DownloadActivity.kt

```kotlin
class DownloadActivity : AppCompatActivity()

fun onCreate(savedInstanceState: Bundle?)               // 初始化布局、夜间模式、状态栏
fun onResume()                                             // 检查模型状态：跳转/更新按钮
fun download(view: View)                                   // 下载按钮：显示进度条，下载模型
fun startMain(view: View)                                  // 开始按钮：跳转 MainActivity
fun updateModels(view: View)                               // 更新按钮：删除旧模型并重新下载
```

## MainActivity.kt

```kotlin
class MainActivity : AppCompatActivity(), Whisper.WhisperListener

override fun onCreate(savedInstanceState: Bundle?)         // 设置布局、Toolbar、模型/语言/模式选项
// WhisperListener 回调
override fun onResult(result: WhisperResult)               // 接收识别/翻译结果并显示
override fun onEnglishResult(isEnglish: Boolean)           // 接收语言检测结果（英文/非英文）
```

## WhisperInputMethodService.kt

```kotlin
class WhisperInputMethodService : InputMethodService()

override fun onCreate()                                    // 初始化录音器、缓冲区、协调器
override fun onCreateInputView(): View                     // 创建 IME 虚拟键盘视图

// 内部监听器
inner class WhisperListenerImpl : Whisper.WhisperListener
    override fun onResult(result: WhisperResult)           // 将识别结果提交到当前输入框
    override fun onEnglishResult(isEnglish: Boolean)       // 处理语言检测结果
```

## WhisperRecognizeActivity.kt

```kotlin
class WhisperRecognizeActivity : Activity()

override fun onCreate(savedInstanceState: Bundle?)         // 设置布局、初始化录音传感器
```

## WhisperRecognitionService.kt

```kotlin
class WhisperRecognitionService : RecognitionService()

override fun onStartListening(intent: Intent, callback: Callback)  // 开始监听语音
override fun onCancel(callback: Callback)                          // 取消监听
override fun onStopListening(callback: Callback)                   // 停止监听
```

## Recorder.kt

```kotlin
class Recorder

constructor(useSco: Boolean, useVad: Boolean, context: Context)    // useSco=蓝牙, useVad=语音检测
fun startRecording()                                                // 开始录音
fun stopRecording()                                                 // 停止录音
fun isRecording(): Boolean                                          // 是否正在录音
fun release()                                                       // 释放资源

// 内部回调
fun interface RecordingListener
    fun onRecordingChunk(audioChunk: ByteArray)                     // 录音数据块回调
```

## RecordBuffer.kt

```kotlin
object RecordBuffer

fun putData(data: ByteArray, length: Int)                          // 将 PCM 数据写入缓冲区
fun pcmToFloat(audioData: ByteArray): FloatArray                   // PCM字节→float归一化数组
fun getFloatData(): FloatArray                                     // 获取缓冲区全部 float 数据
```

## Whisper.kt

```kotlin
class Whisper

constructor(context: Context, useSco: Boolean, useVad: Boolean)
fun loadModel(modelName: String, language: String)                 // 加载模型并指定语言
fun unloadModel()                                                  // 卸载模型释放内存
fun startTranscription(callback: WhisperListener)                  // 开始转写（异步）
fun startTranslation(callback: WhisperListener)                    // 开始翻译（异步→英语）
fun stopTranscription()                                            // 停止转写/翻译
fun isTranscribing(): Boolean                                      // 是否正在转写中
fun release()                                                      // 释放所有资源

// 监听器接口
fun interface WhisperListener
    fun onResult(result: WhisperResult)                            // 识别/翻译结果
    fun onEnglishResult(isEnglish: Boolean)                        // 语言检测结果
```

## WhisperResult.kt

```kotlin
class WhisperResult

val text: String                   // 识别/翻译文字结果
val detectedLanguage: String       // 检测到的语言代码
val type: ResultType               // TRANSCRIBE 或 TRANSLATE

enum class ResultType { TRANSCRIBE, TRANSLATE }
```

## WhisperEngine.kt (接口)

```kotlin
interface WhisperEngine

fun init(context: Context, modelName: String, language: String): Boolean  // 初始化引擎
fun deinit()                                                              // 反初始化
fun processSamples(samples: FloatArray): String                           // 处理音频样本并返回文字
```

## WhisperEngineJava.kt

```kotlin
class WhisperEngineJava : WhisperEngine

override fun init(context: Context, modelName: String, language: String): Boolean
override fun deinit()
override fun processSamples(samples: FloatArray): String

// 内部流程: Mel频谱→TFLite推理→Token解码→文字
```

## WhisperUtil.kt

```kotlin
object WhisperUtil

fun loadVocabulary(vocabularyPath: String): String                      // 加载词汇表→字符串
fun loadFilter(filterPath: String, filterLength: Int): FloatArray       // 加载滤波器系数
fun melSpectrogram(samples: FloatArray, sampleRate: Int,
    fftLength: Int, hopLength: Int, nMelFilters: Int,
    filter: FloatArray, filterLength: Int): Array<FloatArray>           // PCM→Mel频谱图
fun performFFT(real: DoubleArray, imag: DoubleArray)                    // 就地执行FFT
```

## Downloader.kt

```kotlin
object Downloader

fun downloadModels(context: Context, activity: DownloadActivity, progressBar: ProgressBar)
fun modelsExist(context: Context): Boolean
fun isUpdateAvailable(context: Context): Boolean
fun deleteOldModels(context: Context)
fun copyBinFiles(context: Context)
```

## InputLang.kt

```kotlin
object InputLang

fun getLanguageCode(tokenId: Int): String                   // Token ID → ISO 语言代码
fun getTokenId(languageCode: String): Int                   // ISO 语言代码 → Token ID
fun getAllLanguageCodes(): Array<String>                    // 获取所有支持的语言代码数组
fun getTokenIdFromIndex(index: Int): Int                    // 列表索引 → Token ID
```

## HapticFeedback.kt

```kotlin
object HapticFeedback

fun performHapticClick(context: Context)                    // 执行触觉反馈点击震动
```

## ThemeUtils.kt

```kotlin
object ThemeUtils

fun setStatusBarIconMode(activity: AppCompatActivity)       // 设置状态栏图标颜色
```
