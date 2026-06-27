package com.whispertflite.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispertflite.R
import com.whispertflite.ui.theme.WhisperTheme
import com.whispertflite.viewmodel.MainViewModel
import com.whispertflite.viewmodel.ProcessingState
import com.whispertflite.viewmodel.RecordingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val modelFiles by viewModel.modelFiles.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val languagePairs by viewModel.languagePairs.collectAsState()
    val selectedLangIndex by viewModel.selectedLanguageIndex.collectAsState()
    val appendMode by viewModel.appendMode.collectAsState()
    val translateMode by viewModel.translateMode.collectAsState()
    val ttsMode by viewModel.ttsMode.collectAsState()
    val simpleChinese by viewModel.simpleChinese.collectAsState()
    val muteMode by viewModel.muteMode.collectAsState()
    val resultText by viewModel.resultText.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val showChinese by viewModel.showChineseLayout.collectAsState()
    val processingState by viewModel.processingState.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val isIndeterminate by viewModel.isIndeterminate.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadLanguagePairs(context)
        viewModel.loadModelFiles()
        viewModel.restoreSettings()
    }

    WhisperTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                ModelAndLanguageSelectors(
                    context = context,
                    modelFiles = modelFiles,
                    selectedModel = selectedModel,
                    onModelSelected = { viewModel.onModelSelected(it) },
                    languagePairs = languagePairs,
                    selectedLangIndex = selectedLangIndex,
                    onLanguageSelected = { viewModel.onLanguageSelected(it) }
                )

                Spacer(Modifier.height(8.dp))

                StatusSection(statusText)

                Spacer(Modifier.height(8.dp))

                CheckBoxes(
                    context = context,
                    appendMode = appendMode,
                    onAppendChange = { viewModel.appendMode.value = it },
                    translateMode = translateMode,
                    onTranslateChange = { viewModel.translateMode.value = it },
                    ttsMode = ttsMode,
                    onTtsChange = { viewModel.toggleTTS(it) },
                    showTts = translateMode,
                    simpleChinese = simpleChinese,
                    onSimpleChineseChange = { viewModel.toggleSimpleChinese(it) },
                    showChineseLayout = showChinese,
                    muteMode = muteMode,
                    onMuteChange = { viewModel.toggleMute(it) }
                )

                Spacer(Modifier.height(8.dp))

                ResultField(
                    text = resultText,
                    modifier = Modifier.weight(1f)
                )

                Spacer(Modifier.height(8.dp))

                BottomBar(
                    context = context,
                    isProcessing = processingState != ProcessingState.IDLE,
                    isIndeterminate = isIndeterminate,
                    progress = progress,
                    onRecordPress = { viewModel.onRecordPressed() },
                    onRecordRelease = { viewModel.onRecordReleased() },
                    onCopy = { viewModel.copyToClipboard() },
                    onInfo = { viewModel.openInfo() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelAndLanguageSelectors(
    context: android.content.Context,
    modelFiles: List<java.io.File>,
    selectedModel: java.io.File?,
    onModelSelected: (java.io.File) -> Unit,
    languagePairs: List<Pair<String, String>>,
    selectedLangIndex: Int,
    onLanguageSelected: (Int) -> Unit
) {
    var modelExpanded by remember { mutableStateOf(false) }
    var langExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = modelExpanded,
            onExpandedChange = { modelExpanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = getModelDisplayName(context, selectedModel),
                onValueChange = {},
                readOnly = true,
                label = { Text(context.getString(R.string.select_model)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                modifier = Modifier.menuAnchor(),
                singleLine = true
            )
            ExposedDropdownMenu(
                expanded = modelExpanded,
                onDismissRequest = { modelExpanded = false }
            ) {
                modelFiles.forEach { file ->
                    DropdownMenuItem(
                        text = { Text(getModelDisplayName(context, file)) },
                        onClick = {
                            onModelSelected(file)
                            modelExpanded = false
                        }
                    )
                }
            }
        }

        ExposedDropdownMenuBox(
            expanded = langExpanded,
            onExpandedChange = { langExpanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = if (selectedLangIndex < languagePairs.size) languagePairs[selectedLangIndex].second else "",
                onValueChange = {},
                readOnly = true,
                label = { Text(context.getString(R.string.language)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                modifier = Modifier.menuAnchor(),
                singleLine = true
            )
            ExposedDropdownMenu(
                expanded = langExpanded,
                onDismissRequest = { langExpanded = false }
            ) {
                languagePairs.forEachIndexed { index, pair ->
                    DropdownMenuItem(
                        text = { Text(pair.second) },
                        onClick = {
                            onLanguageSelected(index)
                            langExpanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun getModelDisplayName(context: android.content.Context, file: java.io.File?): String {
    if (file == null) return ""
    return when (file.name) {
        "whisper-small.tflite", "whisper-small.TOP_WORLD.tflite" ->
            context.getString(R.string.multi_lingual_slow)
        "whisper-tiny.en.tflite" ->
            context.getString(R.string.english_only_fast)
        "whisper-base.tflite", "whisper-base.TOP_WORLD.tflite", "whisper-base.EUROPEAN_UNION.tflite" ->
            context.getString(R.string.multi_lingual_fast)
        else -> file.name.removeSuffix(".tflite")
    }
}

@Composable
private fun StatusSection(statusText: String) {
    Text(
        text = statusText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CheckBoxes(
    context: android.content.Context,
    appendMode: Boolean,
    onAppendChange: (Boolean) -> Unit,
    translateMode: Boolean,
    onTranslateChange: (Boolean) -> Unit,
    ttsMode: Boolean,
    onTtsChange: (Boolean) -> Unit,
    showTts: Boolean,
    simpleChinese: Boolean,
    onSimpleChineseChange: (Boolean) -> Unit,
    showChineseLayout: Boolean,
    muteMode: Boolean,
    onMuteChange: (Boolean) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = appendMode, onCheckedChange = onAppendChange)
            Text(context.getString(R.string.append))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = translateMode, onCheckedChange = onTranslateChange)
            Text(context.getString(R.string.translate))
        }
        if (showTts) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = ttsMode, onCheckedChange = onTtsChange)
                Text(context.getString(R.string.text_to_speech))
            }
        }
        if (showChineseLayout) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = simpleChinese, onCheckedChange = onSimpleChineseChange)
                Text(context.getString(R.string.simple_chinese))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = muteMode, onCheckedChange = onMuteChange)
            Text(context.getString(R.string.mute_during_recording))
        }
    }
}

@Composable
private fun ResultField(text: String, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        BasicTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .verticalScroll(scrollState),
            textStyle = TextStyle(fontSize = 16.sp),
            enabled = false
        )
    }
}

@Composable
private fun BottomBar(
    context: android.content.Context,
    isProcessing: Boolean,
    isIndeterminate: Boolean,
    progress: Int,
    onRecordPress: () -> Unit,
    onRecordRelease: () -> Unit,
    onCopy: () -> Unit,
    onInfo: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (isProcessing || progress > 0) {
            if (isIndeterminate) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onInfo) {
                Icon(painterResource(R.drawable.ic_info_48dp),
                    contentDescription = context.getString(R.string.info))
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val press = event.changes.any { it.pressed }
                                if (press) {
                                    onRecordPress()
                                } else {
                                    onRecordRelease()
                                }
                            }
                        }
                    }
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_mic_48dp),
                        contentDescription = context.getString(R.string.record_button),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            FloatingActionButton(
                onClick = onCopy,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(painterResource(R.drawable.ic_copy),
                    contentDescription = context.getString(R.string.copy_to_clipboard))
            }
        }
    }
}
