package com.whispertflite.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.whispertflite.R
import com.whispertflite.ui.theme.WhisperTheme
import com.whispertflite.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val modelFiles by viewModel.modelFiles.collectAsState()
    val selectedModelIndex by viewModel.selectedModelIndex.collectAsState()
    val languagePairs by viewModel.languagePairs.collectAsState()
    val selectedLangIndex by viewModel.selectedLanguageIndex.collectAsState()
    val simpleChinese by viewModel.simpleChinese.collectAsState()

    var modelExpanded by remember { mutableStateOf(false) }
    var langExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadModelFiles()
        viewModel.loadLanguagePairs(context)
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
                ExposedDropdownMenuBox(
                    expanded = modelExpanded,
                    onExpandedChange = { modelExpanded = it }
                ) {
                    OutlinedTextField(
                        value = if (selectedModelIndex < modelFiles.size)
                            modelFiles[selectedModelIndex].name.removeSuffix(".tflite")
                        else "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(context.getString(R.string.select_model)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        modelFiles.forEachIndexed { index, file ->
                            DropdownMenuItem(
                                text = { Text(file.name.removeSuffix(".tflite")) },
                                onClick = {
                                    viewModel.onModelSelected(index)
                                    modelExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = langExpanded,
                    onExpandedChange = { langExpanded = it }
                ) {
                    OutlinedTextField(
                        value = if (selectedLangIndex < languagePairs.size)
                            languagePairs[selectedLangIndex].second
                        else "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(context.getString(R.string.language)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
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
                                    viewModel.onLanguageSelected(index)
                                    langExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = simpleChinese,
                        onCheckedChange = { viewModel.toggleSimpleChinese(it) }
                    )
                    Text(context.getString(R.string.simple_chinese))
                }
            }
        }
    }
}
