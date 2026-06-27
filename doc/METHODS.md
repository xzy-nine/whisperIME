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

## MainActivity.java

```java
public class MainActivity extends AppCompatActivity implements Whisper.WhisperListener

protected void onCreate(Bundle savedInstanceState)         // 设置布局、Toolbar、模型/语言/模式选项
// WhisperListener 回调
void onResult(WhisperResult result)                        // 接收识别/翻译结果并显示
void onEnglishResult(boolean isEnglish)                    // 接收语言检测结果（英文/非英文）
```

## WhisperInputMethodService.java

```java
public class WhisperInputMethodService extends InputMethodService

public void onCreate()                                     // 初始化录音器、缓冲区、协调器
public View onCreateInputView()                            // 创建 IME 虚拟键盘视图

// 内部监听器
class WhisperListenerImpl implements Whisper.WhisperListener
    void onResult(WhisperResult result)                    // 将识别结果提交到当前输入框
    void onEnglishResult(boolean isEnglish)                // 处理语言检测结果
```

## WhisperRecognizeActivity.java

```java
public class WhisperRecognizeActivity extends Activity

protected void onCreate(Bundle savedInstanceState)         // 设置布局、初始化录音传感器
```

## WhisperRecognitionService.java

```java
public class WhisperRecognitionService extends RecognitionService

protected void onStartListening(Intent intent, Callback callback)  // 开始监听语音
protected void onCancel(Callback callback)                         // 取消监听
protected void onStopListening(Callback callback)                  // 停止监听
```

## Recorder.java

```java
public class Recorder

public Recorder(boolean useSco, boolean useVad, Context context)   // useSco=蓝牙, useVad=语音检测
public void startRecording()                                       // 开始录音
public void stopRecording()                                        // 停止录音
public boolean isRecording()                                       // 是否正在录音
public void release()                                              // 释放资源

// 内部回调
public interface RecordingListener
    void onRecordingChunk(byte[] audioChunk)                       // 录音数据块回调
```

## RecordBuffer.java

```java
public class RecordBuffer

public static void putData(byte[] data, int length)                // 将 PCM 数据写入缓冲区
public static float[] pcmToFloat(byte[] audioData)                // PCM字节→float归一化数组
public static float[] getFloatData()                               // 获取缓冲区全部 float 数据
```

## Whisper.java

```java
public class Whisper

public Whisper(Context context, boolean useSco, boolean useVad)
public void loadModel(String modelName, String language)           // 加载模型并指定语言
public void unloadModel()                                          // 卸载模型释放内存
public void startTranscription(WhisperListener callback)           // 开始转写（异步）
public void startTranslation(WhisperListener callback)             // 开始翻译（异步→英语）
public void stopTranscription()                                    // 停止转写/翻译
public boolean isTranscribing()                                    // 是否正在转写中
public void release()                                              // 释放所有资源

// 监听器接口
public interface WhisperListener
    void onResult(WhisperResult result)                            // 识别/翻译结果
    void onEnglishResult(boolean isEnglish)                        // 语言检测结果
```

## WhisperResult.java

```java
public class WhisperResult

public String text;                // 识别/翻译文字结果
public String detectedLanguage;    // 检测到的语言代码
public ResultType type;            // TRANSCRIBE 或 TRANSLATE

public enum ResultType { TRANSCRIBE, TRANSLATE }
```

## WhisperEngine.java (接口)

```java
public interface WhisperEngine

boolean init(Context context, String modelName, String language)    // 初始化引擎
void deinit()                                                       // 反初始化
String processSamples(float[] samples)                             // 处理音频样本并返回文字
```

## WhisperEngineJava.java

```java
public class WhisperEngineJava implements WhisperEngine

public boolean init(Context context, String modelName, String language)
public void deinit()
public String processSamples(float[] samples)

// 内部流程: Mel频谱→TFLite推理→Token解码→文字
```

## WhisperUtil.java

```java
public class WhisperUtil

public static String loadVocabulary(String vocabularyPath)              // 加载词汇表→字符串
public static float[] loadFilter(String filterPath, int filterLength)   // 加载滤波器系数
public static float[][] melSpectrogram(float[] samples, int sampleRate,
    int fftLength, int hopLength, int nMelFilters,
    float[] filter, int filterLength)                                   // PCM→Mel频谱图
public static void performFFT(double[] real, double[] imag)             // 就地执行FFT
```

## Downloader.java

```java
public class Downloader

public static void downloadModels(Context context, DownloadActivity activity, ProgressBar progressBar)
public static boolean modelsExist(Context context)
public static boolean isUpdateAvailable(Context context)
public static void deleteOldModels(Context context)
public static void copyBinFiles(Context context)
```

## InputLang.java

```java
public class InputLang

public static String getLanguageCode(int tokenId)           // Token ID → ISO 语言代码
public static int getTokenId(String languageCode)           // ISO 语言代码 → Token ID
public static String[] getAllLanguageCodes()                // 获取所有支持的语言代码数组
public static int getTokenIdFromIndex(int index)            // 列表索引 → Token ID
```

## HapticFeedback.java

```java
public class HapticFeedback

public static void performHapticClick(Context context)      // 执行触觉反馈点击震动
```

## ThemeUtils.java

```java
public class ThemeUtils

public static void setStatusBarIconMode(AppCompatActivity activity)  // 设置状态栏图标颜色
```
