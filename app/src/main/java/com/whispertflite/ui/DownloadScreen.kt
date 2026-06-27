package com.whispertflite.ui

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.whispertflite.R
import com.whispertflite.ui.theme.WhisperTheme
import com.whispertflite.utils.Downloader
import com.whispertflite.viewmodel.DownloadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel,
    onStartMain: () -> Unit
) {
    val context = LocalContext.current
    val progress by viewModel.downloadProgress.collectAsState()
    val sizeText by viewModel.downloadSizeText.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val showStart by viewModel.showStartButton.collectAsState()
    val showUpdate by viewModel.showUpdateButton.collectAsState()
    val downloadEnabled by viewModel.downloadEnabled.collectAsState()
    val selectedMirror by viewModel.selectedMirror.collectAsState()
    var mirrorExpanded by remember { mutableStateOf(false) }

    WhisperTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_mic_48dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(80.dp)
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = context.getString(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    text = context.getString(R.string.download_model_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(24.dp))

                ExposedDropdownMenuBox(
                    expanded = mirrorExpanded,
                    onExpandedChange = { mirrorExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedMirror.getDisplayName(context),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(context.getString(R.string.mirror_source)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mirrorExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = mirrorExpanded,
                        onDismissRequest = { mirrorExpanded = false }
                    ) {
                        Downloader.MirrorSource.entries.forEach { source ->
                            DropdownMenuItem(
                                text = { Text(source.getDisplayName(context)) },
                                onClick = {
                                    viewModel.onMirrorSelected(source)
                                    mirrorExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (sizeText.isNotEmpty()) {
                    Text(
                        text = sizeText,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (isDownloading || progress > 0) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                }

                if (showStart) {
                    Button(
                        onClick = onStartMain,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(context.getString(R.string.start))
                    }
                    if (showUpdate) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.startUpdate(context as Activity) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(context.getString(R.string.update))
                        }
                    }
                } else {
                    Button(
                        onClick = { viewModel.startDownload(context as Activity) },
                        enabled = downloadEnabled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(context.getString(R.string.download_model))
                    }
                }
            }
        }
    }
}
