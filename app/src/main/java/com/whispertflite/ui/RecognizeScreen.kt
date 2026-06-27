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
import com.whispertflite.viewmodel.ProcessingState
import com.whispertflite.viewmodel.RecognizeViewModel
import com.whispertflite.viewmodel.RecordingState

@Composable
fun RecognizeScreen(viewModel: RecognizeViewModel) {
    val context = LocalContext.current
    val autoMode by viewModel.autoMode.collectAsState()
    val isProcessing by viewModel.processingState.collectAsState()
    val isIndeterminate by viewModel.isIndeterminate.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()

    WhisperTheme {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                    Spacer(Modifier.height(12.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.cancel() }) {
                        Icon(
                            painterResource(R.drawable.ic_cancel_36dp),
                            contentDescription = context.getString(R.string.exit_button)
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

                    IconButton(onClick = { viewModel.toggleAutoMode() }) {
                        Icon(
                            painterResource(
                                if (autoMode) R.drawable.ic_auto_on_36dp
                                else R.drawable.ic_auto_off_36dp
                            ),
                            contentDescription = context.getString(R.string.auto_button)
                        )
                    }
                }
            }
        }
    }
}
