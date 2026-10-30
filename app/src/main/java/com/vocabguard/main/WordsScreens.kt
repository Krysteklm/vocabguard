package com.vocabguard.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocabguard.data.local.DeckWithCount
import com.vocabguard.data.local.WordEntity
import com.vocabguard.data.repository.WordRepository
import com.vocabguard.ui.components.ConfirmDialog
import com.vocabguard.ui.components.NameDialog
import com.vocabguard.ui.components.NoticeCard
import com.vocabguard.ui.components.PrimaryButton
import com.vocabguard.ui.components.ProgressTrack
import com.vocabguard.ui.components.ScreenHeader
import com.vocabguard.ui.components.SecondaryButton
import com.vocabguard.ui.components.TopBackBar
import com.vocabguard.ui.components.VGCard
import com.vocabguard.ui.components.VGSwitch
import com.vocabguard.ui.components.VGTextField
import com.vocabguard.ui.components.appearOnce
import com.vocabguard.ui.theme.VGColors

private const val MASTERY_LEVELS = WordRepository.LEARNED_THRESHOLD

// ---------------------------------------------------------------------------------------------
// Deck list
// ---------------------------------------------------------------------------------------------

@Composable
fun WordsHomeScreen(
    viewModel: WordsViewModel,
    activeWordCount: Int,
    onOpenDeck: (Long) -> Unit,
    onImport: () -> Unit,
) {
    val decks by viewModel.decks.collectAsStateWithLifecycle()
    var showNewDeck by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            ScreenHeader(
                title = "Words",
                subtitle = "Your decks. Only active decks show up in quizzes.",
                modifier = Modifier.appearOnce(0),
            )
        }
        item(key = "actions") {
            Row(Modifier.appearOnce(1), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(
                    text = "Import words",
                    onClick = onImport,
                    icon = Icons.Rounded.FileUpload,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = "New deck",
                    onClick = { showNewDeck = true },
                    icon = Icons.Rounded.Add,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (activeWordCount == 0 && decks.isNotEmpty()) {
            item(key = "noActive") {
                NoticeCard(
                    icon = Icons.Rounded.Info,
                    tint = VGColors.Primary,
                    title = "No active words",
                    body = "Turn on a deck with words in it. Until then, quizzes use the built-in German pack.",
                )
            }
        }
        if (decks.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Create a deck or import a list to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VGColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                )
            }
        }
        items(decks, key = { it.deck.id }) { item ->
            DeckCard(
                item = item,
                onClick = { onOpenDeck(item.deck.id) },
                onActiveChange = { viewModel.setDeckActive(item.deck.id, it) },
                modifier = Modifier.animateItem(),
            )
        }
    }

    if (showNewDeck) {
        NameDialog(
            title = "New deck",
            confirmLabel = "Create deck",
            initialValue = "",
            onConfirm = {
                showNewDeck = false
                viewModel.createDeck(it)
            },
            onDismiss = { showNewDeck = false },
        )
    }
}

@Composable
private fun DeckCard(item: DeckWithCount, onClick: () -> Unit, onActiveChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val progress = if (item.wordCount == 0) 0f else item.learnedCount.toFloat() / item.wordCount
    VGCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.deck.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (item.deck.isActive) VGColors.TextPrimary else VGColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${item.wordCount} words, ${item.learnedCount} learned",
                    style = MaterialTheme.typography.bodySmall,
                    color = VGColors.TextSecondary,
                )
            }
            VGSwitch(checked = item.deck.isActive, onCheckedChange = onActiveChange)
        }
        Spacer(Modifier.height(12.dp))
        ProgressTrack(fraction = progress, color = if (item.deck.isActive) VGColors.Success else VGColors.TextSecondary)
    }
}

// ---------------------------------------------------------------------------------------------
// Deck detail
// ---------------------------------------------------------------------------------------------

@Composable
fun DeckScreen(
    viewModel: WordsViewModel,
    deckId: Long,
    onBack: () -> Unit,
    onImportIntoDeck: () -> Unit,
) {
    val detail by viewModel.deckDetail.collectAsStateWithLifecycle()
    val formVersion by viewModel.addFormVersion.collectAsStateWithLifecycle()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var editingWordId by rememberSaveable { mutableStateOf<Long?>(null) }

    val ready = (detail as? DeckDetailUi.Ready)?.takeIf { it.deck.id == deckId }

    Column(Modifier.fillMaxSize()) {
        TopBackBar(
            title = ready?.deck?.name ?: "Deck",
            onBack = onBack,
            actions = {
                if (ready != null) {
                    IconButton(onClick = onImportIntoDeck) {
                        Icon(Icons.Rounded.FileUpload, contentDescription = "Import into this deck", tint = VGColors.TextPrimary)
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Deck options", tint = VGColors.TextPrimary)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename deck") },
                                onClick = {
                                    menuOpen = false
                                    renaming = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete deck", color = VGColors.Error) },
                                onClick = {
                                    menuOpen = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                }
            },
        )

        when {
            detail is DeckDetailUi.Missing -> Text(
                "This deck no longer exists.",
                color = VGColors.TextSecondary,
                modifier = Modifier.padding(20.dp),
            )
            ready == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VGColors.Primary)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "active") {
                    VGCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Use in quizzes", style = MaterialTheme.typography.titleSmall, color = VGColors.TextPrimary)
                                Text(
                                    "${ready.words.size} words in this deck",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VGColors.TextSecondary,
                                )
                            }
                            VGSwitch(
                                checked = ready.deck.isActive,
                                onCheckedChange = { viewModel.setDeckActive(ready.deck.id, it) },
                            )
                        }
                    }
                }
                item(key = "add") {
                    AddWordCard(
                        formVersion = formVersion,
                        onAdd = { foreign, translation -> viewModel.addWord(ready.deck.id, foreign, translation) },
                    )
                }
                if (ready.words.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "Add words above, or import a whole list with the upload button.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VGColors.TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                        )
                    }
                }
                items(ready.words, key = { it.id }) { word ->
                    WordRow(
                        word = word,
                        onClick = { editingWordId = word.id },
                        onDelete = { viewModel.deleteWord(word) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (ready != null) {
        if (renaming) {
            NameDialog(
                title = "Rename deck",
                confirmLabel = "Rename",
                initialValue = ready.deck.name,
                onConfirm = {
                    renaming = false
                    viewModel.renameDeck(ready.deck.id, it)
                },
                onDismiss = { renaming = false },
            )
        }
        if (confirmDelete) {
            ConfirmDialog(
                title = "Delete “${ready.deck.name}”?",
                body = "This removes the deck and its ${ready.words.size} words. It can’t be undone.",
                confirmLabel = "Delete deck",
                destructive = true,
                onConfirm = {
                    confirmDelete = false
                    viewModel.deleteDeck(ready.deck)
                },
                onDismiss = { confirmDelete = false },
            )
        }
        ready.words.firstOrNull { it.id == editingWordId }?.let { word ->
            EditWordDialog(
                word = word,
                onSave = { foreign, translation ->
                    editingWordId = null
                    viewModel.updateWord(word, foreign, translation)
                },
                onDismiss = { editingWordId = null },
            )
        }
    }
}

@Composable
private fun AddWordCard(formVersion: Int, onAdd: (String, String) -> Unit) {
    // Keyed on formVersion: a successful add bumps it and clears both fields.
    var foreign by rememberSaveable(formVersion) { mutableStateOf("") }
    var translation by rememberSaveable(formVersion) { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val canSubmit = foreign.isNotBlank() && translation.isNotBlank()

    VGCard {
        Text("Add a word", style = MaterialTheme.typography.titleMedium, color = VGColors.TextPrimary)
        Spacer(Modifier.height(14.dp))
        VGTextField(
            value = foreign,
            onValueChange = { foreign = it },
            label = "Word",
            placeholder = "der Schlüssel",
            maxLength = WordRepository.MAX_LENGTH,
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
        )
        Spacer(Modifier.height(10.dp))
        VGTextField(
            value = translation,
            onValueChange = { translation = it },
            label = "Translation",
            placeholder = "the key",
            maxLength = WordRepository.MAX_LENGTH,
            onImeAction = {
                if (canSubmit) {
                    onAdd(foreign, translation)
                    focusManager.moveFocus(FocusDirection.Up)
                }
            },
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "Add word",
            onClick = { onAdd(foreign, translation) },
            enabled = canSubmit,
            icon = Icons.Rounded.Add,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EditWordDialog(word: WordEntity, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var foreign by rememberSaveable(word.id) { mutableStateOf(word.foreignWord) }
    var translation by rememberSaveable(word.id) { mutableStateOf(word.nativeTranslation) }
    val canSave = foreign.isNotBlank() && translation.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VGColors.Surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Edit word", color = VGColors.TextPrimary) },
        text = {
            Column {
                VGTextField(value = foreign, onValueChange = { foreign = it }, label = "Word", imeAction = ImeAction.Next)
                Spacer(Modifier.height(10.dp))
                VGTextField(
                    value = translation,
                    onValueChange = { translation = it },
                    label = "Translation",
                    onImeAction = { if (canSave) onSave(foreign, translation) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(foreign, translation) }, enabled = canSave) {
                Text("Save", color = if (canSave) VGColors.Primary else VGColors.TextSecondary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = VGColors.TextSecondary) }
        },
    )
}

@Composable
private fun WordRow(word: WordEntity, onClick: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = VGColors.Surface,
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(word.foreignWord, style = MaterialTheme.typography.titleMedium, color = VGColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(word.nativeTranslation, style = MaterialTheme.typography.bodyMedium, color = VGColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MasteryDots(correctCount = word.correctCount)
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete ${word.foreignWord}", tint = VGColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun MasteryDots(correctCount: Int) {
    val filled = correctCount.coerceIn(0, MASTERY_LEVELS)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .semantics { contentDescription = "Learned $filled of $MASTERY_LEVELS" },
    ) {
        repeat(MASTERY_LEVELS) { index ->
            val color by animateColorAsState(if (index < filled) VGColors.Primary else VGColors.Track, label = "masteryDot")
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

