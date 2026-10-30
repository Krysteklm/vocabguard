package com.vocabguard.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocabguard.domain.ImportPreview
import com.vocabguard.ui.components.PrimaryButton
import com.vocabguard.ui.components.SecondaryButton
import com.vocabguard.ui.components.SettingRow
import com.vocabguard.ui.components.TopBackBar
import com.vocabguard.ui.components.VGCard
import com.vocabguard.ui.components.VGSwitch
import com.vocabguard.ui.components.VGTextField
import com.vocabguard.ui.theme.VGColors

private val IMPORT_MIME_TYPES = arrayOf(
    "text/*",
    "text/csv",
    "text/comma-separated-values",
    "text/tab-separated-values",
    "application/csv",
    "application/vnd.ms-excel",
    "application/octet-stream",
)

private const val PREVIEW_ROWS = 6

@Composable
fun ImportScreen(viewModel: WordsViewModel, onBack: () -> Unit) {
    val state by viewModel.importState.collectAsStateWithLifecycle()
    val decks by viewModel.decks.collectAsStateWithLifecycle()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.loadImportFile(uri)
    }
    val count = state.preview.pairs.size

    Column(Modifier.fillMaxSize()) {
        TopBackBar(title = "Import words", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Source
            VGCard {
                Text("Your list", style = MaterialTheme.typography.titleMedium, color = VGColors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "One pair per line. Separate the word and its translation with a dash, tab, semicolon, " +
                        "comma or equals sign. Lines starting with # are ignored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VGColors.TextSecondary,
                )
                Spacer(Modifier.height(14.dp))
                when {
                    state.isLoadingFile -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = VGColors.Primary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Reading file…", color = VGColors.TextSecondary)
                    }
                    state.fileName != null -> FileChip(
                        name = state.fileName.orEmpty(),
                        lines = state.fileLineCount,
                        onClear = viewModel::clearImportFile,
                    )
                    else -> {
                        VGTextField(
                            value = state.text,
                            onValueChange = viewModel::onImportTextChange,
                            label = "Paste or type",
                            placeholder = "der Hund - the dog\ndie Katze - the cat",
                            singleLine = false,
                            minLines = 5,
                            maxLines = 12,
                            maxLength = 200_000,
                        )
                        Spacer(Modifier.height(12.dp))
                        SecondaryButton(
                            text = "Choose a file",
                            onClick = { filePicker.launch(IMPORT_MIME_TYPES) },
                            icon = Icons.Rounded.FileOpen,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "TXT, CSV or TSV up to 1 MB, including Anki and Quizlet exports.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VGColors.TextSecondary,
                        )
                    }
                }
            }

            // 2. Options
            VGCard {
                SettingRow(title = "Swap columns", subtitle = "Use this when the translation comes first.") {
                    VGSwitch(checked = state.swapColumns, onCheckedChange = viewModel::onSwapColumnsChange)
                }
                Spacer(Modifier.height(14.dp))
                SettingRow(title = "Skip the first line", subtitle = "For files that start with a header row.") {
                    VGSwitch(checked = state.skipFirstLine, onCheckedChange = viewModel::onSkipFirstLineChange)
                }
            }

            // 3. Target deck
            VGCard {
                Text("Add to", style = MaterialTheme.typography.titleMedium, color = VGColors.TextPrimary)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "new") {
                        TargetChip(
                            label = "New deck",
                            selected = state.targetDeckId == null,
                            onClick = { viewModel.onImportTargetChange(null) },
                        )
                    }
                    items(decks, key = { it.deck.id }) { item ->
                        TargetChip(
                            label = item.deck.name,
                            selected = state.targetDeckId == item.deck.id,
                            onClick = { viewModel.onImportTargetChange(item.deck.id) },
                        )
                    }
                }
                AnimatedVisibility(visible = state.targetDeckId == null) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        VGTextField(
                            value = state.newDeckName,
                            onValueChange = viewModel::onNewDeckNameChange,
                            label = "Deck name",
                            placeholder = "Spanish verbs",
                            maxLength = 40,
                        )
                    }
                }
            }

            // 4. Preview
            PreviewCard(state.preview, hasInput = state.text.isNotBlank())

            state.error?.let {
                Text(it, color = VGColors.Error, style = MaterialTheme.typography.bodyMedium)
            }
        }

        PrimaryButton(
            text = when {
                state.isImporting -> "Importing…"
                count == 0 -> "Import words"
                count == 1 -> "Import 1 word"
                else -> "Import $count words"
            },
            onClick = viewModel::confirmImport,
            enabled = count > 0 && !state.isImporting,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun FileChip(name: String, lines: Int, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VGColors.SurfaceHigh)
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Description, contentDescription = null, tint = VGColors.Primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = VGColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$lines lines", style = MaterialTheme.typography.bodySmall, color = VGColors.TextSecondary)
        }
        IconButton(onClick = onClear) {
            Icon(Icons.Rounded.Close, contentDescription = "Remove file", tint = VGColors.TextSecondary)
        }
    }
}

@Composable
private fun TargetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = VGColors.SurfaceHigh,
            labelColor = VGColors.TextSecondary,
            selectedContainerColor = VGColors.Primary.copy(alpha = 0.16f),
            selectedLabelColor = VGColors.Primary,
        ),
    )
}

@Composable
private fun PreviewCard(preview: ImportPreview, hasInput: Boolean) {
    VGCard {
        Text("Preview", style = MaterialTheme.typography.titleMedium, color = VGColors.TextPrimary)
        Spacer(Modifier.height(6.dp))
        if (!hasInput) {
            Text("Your words show up here as you type.", style = MaterialTheme.typography.bodySmall, color = VGColors.TextSecondary)
        } else {
            PreviewDetails(preview)
        }
    }
}

@Composable
private fun PreviewDetails(preview: ImportPreview) {
    Column {
        val summary = buildString {
            append(
                when (preview.pairs.size) {
                    0 -> "No pairs found yet."
                    1 -> "Found 1 pair"
                    else -> "Found ${preview.pairs.size} pairs"
                },
            )
            if (preview.pairs.isNotEmpty() && preview.separatorName != null) append(", split by ${preview.separatorName}.")
            else if (preview.pairs.isNotEmpty()) append(".")
        }
        Text(summary, style = MaterialTheme.typography.bodySmall, color = VGColors.TextSecondary)

        if (preview.pairs.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            preview.pairs.take(PREVIEW_ROWS).forEach { pair ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text(pair.foreign, style = MaterialTheme.typography.bodyMedium, color = VGColors.TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(12.dp))
                    Text(pair.translation, style = MaterialTheme.typography.bodyMedium, color = VGColors.TextSecondary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val more = preview.pairs.size - PREVIEW_ROWS
            if (more > 0) {
                Text("and $more more", style = MaterialTheme.typography.bodySmall, color = VGColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
            }
        }

        val notes = buildList {
            if (preview.skippedLineNumbers.isNotEmpty()) {
                val shown = preview.skippedLineNumbers.take(8).joinToString(", ")
                val extra = if (preview.skippedLineNumbers.size > 8) " and more" else ""
                add("Skipped lines $shown$extra: no separator, an empty side, or over 60 characters.")
            }
            if (preview.duplicateCount > 0) add("${preview.duplicateCount} repeated words in the list were merged.")
            if (preview.truncated) add("Only the first 5,000 lines are read.")
        }
        notes.forEach { note ->
            Spacer(Modifier.height(8.dp))
            Text(note, style = MaterialTheme.typography.bodySmall, color = VGColors.Warning)
        }
    }
}
