package com.whispertflite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.whispertflite.R
import com.whispertflite.ui.theme.WhisperTheme
import com.whispertflite.viewmodel.IMEViewModel
import com.whispertflite.viewmodel.ProcessingState
import com.whispertflite.viewmodel.RecordingState
import kotlinx.coroutines.delay

interface IMECallback {
    fun switchKeyboard()
    fun switchAway()
}

@Composable
fun IMEScreen(viewModel: IMEViewModel, callback: IMECallback) {
    val context = LocalContext.current
    val autoMode by viewModel.autoMode.collectAsState()
    val translateMode by viewModel.translateMode.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val showStatus by viewModel.showStatus.collectAsState()
    val isProcessing by viewModel.processingState.collectAsState()
    val isIndeterminate by viewModel.isIndeterminate.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()

    WhisperTheme {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isProcessing != ProcessingState.IDLE) {
                    if (isIndeterminate) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (showStatus && statusText.isNotEmpty()) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                if (autoMode) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                                color = if (recordingState == RecordingState.RECORDING)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
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
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { callback.switchKeyboard() }) {
                            Icon(
                                painterResource(R.drawable.ic_keyboard_36dp),
                                contentDescription = context.getString(R.string.return_button)
                            )
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
                                                viewModel.onRecordPressed()
                                            } else {
                                                viewModel.onRecordReleased()
                                            }
                                        }
                                    }
                                }
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.extraLarge,
                            color = if (recordingState == RecordingState.RECORDING)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
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

                        IconButton(
                            onClick = { viewModel.toggleTranslate() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                painterResource(
                                    if (translateMode) R.drawable.ic_english_on_36dp
                                    else R.drawable.ic_english_off_36dp
                                ),
                                contentDescription = context.getString(R.string.translate)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleAutoMode() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                painterResource(
                                    if (autoMode) R.drawable.ic_auto_on_36dp
                                    else R.drawable.ic_auto_off_36dp
                                ),
                                contentDescription = context.getString(R.string.auto_button)
                            )
                        }

                        DeleteButton(
                            context = context,
                            onDelete = { viewModel.commitDelete() },
                            onLongDeleteStart = { /* no-op for now */ },
                            onLongDeleteEnd = { /* no-op for now */ }
                        )

                        IconButton(
                            onClick = { viewModel.sendEnter() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_keyboard_return_48dp),
                                contentDescription = context.getString(R.string.return_button)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteButton(
    context: android.content.Context = LocalContext.current,
    onDelete: () -> Unit,
    onLongDeleteStart: () -> Unit,
    onLongDeleteEnd: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(500)
            while (isPressed) {
                onDelete()
                delay(100)
            }
        }
    }

    IconButton(
        onClick = { onDelete() },
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            painterResource(R.drawable.ic_keyboard_del_48dp),
            contentDescription = context.getString(R.string.delete_button)
        )
    }
}
