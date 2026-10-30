package com.vocabguard.main

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vocabguard.VocabGuardApp
import com.vocabguard.data.local.DeckEntity
import com.vocabguard.data.local.DeckWithCount
import com.vocabguard.data.local.WordEntity
import com.vocabguard.data.repository.DeckResult
import com.vocabguard.data.repository.ImportResult
import com.vocabguard.data.repository.ImportTarget
import com.vocabguard.data.repository.WordRepository
import com.vocabguard.data.repository.WordResult
import com.vocabguard.domain.ImportPreview
import com.vocabguard.domain.VocabImportParser
import com.vocabguard.sound.SoundPlayer
import com.vocabguard.util.FileTooLargeException
import com.vocabguard.util.NamedText
import com.vocabguard.util.TextFileReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DeckDetailUi {
    data object Loading : DeckDetailUi
    data object Missing : DeckDetailUi
    data class Ready(val deck: DeckEntity, val words: List<WordEntity>) : DeckDetailUi
}

data class ImportUiState(
    val text: String = "",
    val fileName: String? = null,
    val fileLineCount: Int = 0,
    val swapColumns: Boolean = false,
    val skipFirstLine: Boolean = false,
    /** null = create a new deck named [newDeckName]. */
    val targetDeckId: Long? = null,
    val newDeckName: String = "",
    val preview: ImportPreview = ImportPreview(),
    val isLoadingFile: Boolean = false,
    val isImporting: Boolean = false,
    val error: String? = null,
)

/** Decks, the words inside them, and the import flow. */
@OptIn(ExperimentalCoroutinesApi::class)
class WordsViewModel(
    private val repository: WordRepository,
    private val readFile: suspend (Uri) -> NamedText,
) : ViewModel() {

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    val decks: StateFlow<List<DeckWithCount>> = repository.observeDecks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val openDeckId = MutableStateFlow<Long?>(null)

    val deckDetail: StateFlow<DeckDetailUi> = openDeckId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(DeckDetailUi.Loading)
            } else {
                combine(repository.observeDeck(id), repository.observeWords(id)) { deck, words ->
                    if (deck == null) DeckDetailUi.Missing else DeckDetailUi.Ready(deck, words)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeckDetailUi.Loading)

    /** Bumped after a successful add so the form clears itself. */
    private val _addFormVersion = MutableStateFlow(0)
    val addFormVersion: StateFlow<Int> = _addFormVersion.asStateFlow()

    private val _importState = MutableStateFlow(ImportUiState())
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()
    private var importSessionKey: Long? = null
    private var importActive = false
    private var parseJob: Job? = null

    // ---- Decks -------------------------------------------------------------------------------

    fun openDeck(deckId: Long) {
        openDeckId.value = deckId
    }

    fun createDeck(name: String) = launchSafely("Couldn’t create the deck.") {
        when (val result = repository.createDeck(name)) {
            is DeckResult.Success -> _events.send(UiEvent.Navigate(Route.Deck(result.deckId)))
            DeckResult.Duplicate -> _events.send(UiEvent.Message("A deck called “${name.trim()}” already exists."))
            is DeckResult.Invalid -> _events.send(UiEvent.Message(result.reason))
        }
    }

    fun renameDeck(deckId: Long, name: String) = launchSafely("Couldn’t rename the deck.") {
        when (val result = repository.renameDeck(deckId, name)) {
            is DeckResult.Success -> Unit
            DeckResult.Duplicate -> _events.send(UiEvent.Message("A deck called “${name.trim()}” already exists."))
            is DeckResult.Invalid -> _events.send(UiEvent.Message(result.reason))
        }
    }

    fun setDeckActive(deckId: Long, active: Boolean) = launchSafely("Couldn’t update the deck.") {
        repository.setDeckActive(deckId, active)
    }

    fun deleteDeck(deck: DeckEntity) = launchSafely("Couldn’t delete the deck.") {
        repository.deleteDeck(deck.id)
        _events.send(UiEvent.Navigate(Route.Home, DashboardTab.Words))
        _events.send(UiEvent.Message("Deleted “${deck.name}”"))
    }

    // ---- Words -------------------------------------------------------------------------------

    fun addWord(deckId: Long, foreign: String, translation: String) = launchSafely("Couldn’t save the word.") {
        when (val result = repository.addWord(deckId, foreign, translation)) {
            WordResult.Success -> {
                _addFormVersion.update { it + 1 }
                _events.send(UiEvent.PlaySound(SoundPlayer.Sound.Tap))
            }
            WordResult.Duplicate -> _events.send(UiEvent.Message("“${foreign.trim()}” is already in this deck."))
            is WordResult.Invalid -> _events.send(UiEvent.Message(result.reason))
        }
    }

    fun updateWord(word: WordEntity, foreign: String, translation: String) = launchSafely("Couldn’t save the word.") {
        when (val result = repository.updateWord(word, foreign, translation)) {
            WordResult.Success -> Unit
            WordResult.Duplicate -> _events.send(UiEvent.Message("“${foreign.trim()}” is already in this deck."))
            is WordResult.Invalid -> _events.send(UiEvent.Message(result.reason))
        }
    }

    fun deleteWord(word: WordEntity) = launchSafely("Couldn’t delete the word.") {
        repository.deleteWord(word)
        _events.send(
            UiEvent.Undoable("Deleted “${word.foreignWord}”") {
                launchSafely("Couldn’t restore the word.") {
                    if (!repository.restoreWord(word)) {
                        _events.send(UiEvent.Message("“${word.foreignWord}” was added again in the meantime."))
                    }
                }
            },
        )
    }

    // ---- Import ------------------------------------------------------------------------------

    /** Starts an import session; survives rotation because it's a no-op for the same target. */
    fun startImport(deckId: Long?) {
        if (importActive && importSessionKey == deckId) return
        importActive = true
        importSessionKey = deckId
        // From a deck: import into it. From the Words tab: default to a new deck.
        _importState.value = ImportUiState(targetDeckId = deckId)
    }

    fun closeImport() {
        importActive = false
        importSessionKey = null
        parseJob?.cancel()
        _importState.value = ImportUiState()
    }

    fun onImportTextChange(text: String) {
        _importState.update { it.copy(text = text, error = null) }
        reparse(debounceMs = 150)
    }

    fun onSwapColumnsChange(swap: Boolean) {
        _importState.update { it.copy(swapColumns = swap) }
        reparse()
    }

    fun onSkipFirstLineChange(skip: Boolean) {
        _importState.update { it.copy(skipFirstLine = skip) }
        reparse()
    }

    fun onImportTargetChange(deckId: Long?) {
        _importState.update { it.copy(targetDeckId = deckId, error = null) }
    }

    fun onNewDeckNameChange(name: String) {
        _importState.update { it.copy(newDeckName = name, error = null) }
    }

    fun loadImportFile(uri: Uri) {
        _importState.update { it.copy(isLoadingFile = true, error = null) }
        viewModelScope.launch {
            try {
                val file = readFile(uri)
                val baseName = file.displayName?.substringBeforeLast('.')?.take(WordRepository.MAX_DECK_NAME).orEmpty()
                _importState.update {
                    it.copy(
                        text = file.text,
                        fileName = file.displayName ?: "Selected file",
                        fileLineCount = file.text.lines().count { line -> line.isNotBlank() },
                        newDeckName = it.newDeckName.ifBlank { baseName },
                        isLoadingFile = false,
                    )
                }
                reparse()
            } catch (e: CancellationException) {
                throw e
            } catch (e: FileTooLargeException) {
                _importState.update { it.copy(isLoadingFile = false, error = "That file is over 1 MB. Split it into smaller files.") }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't read import file", e)
                _importState.update { it.copy(isLoadingFile = false, error = "Couldn’t read that file. Choose a TXT or CSV file.") }
            }
        }
    }

    fun clearImportFile() {
        _importState.update { it.copy(text = "", fileName = null, fileLineCount = 0, preview = ImportPreview()) }
    }

    fun confirmImport() {
        val state = _importState.value
        if (state.isImporting) return
        if (state.preview.pairs.isEmpty()) {
            _importState.update { it.copy(error = "Add at least one line like “der Hund - the dog”.") }
            return
        }
        val target = state.targetDeckId?.let { ImportTarget.Existing(it) }
            ?: if (state.newDeckName.isBlank()) {
                _importState.update { it.copy(error = "Name the new deck.") }
                return
            } else {
                ImportTarget.New(state.newDeckName)
            }

        _importState.update { it.copy(isImporting = true, error = null) }
        viewModelScope.launch {
            try {
                when (val result = repository.importPairs(target, state.preview.pairs)) {
                    is ImportResult.Success -> {
                        val s = result.summary
                        val skippedNote = if (s.alreadyThere > 0) " ${s.alreadyThere} were already there." else ""
                        closeImport()
                        _events.send(UiEvent.Navigate(Route.Deck(s.deckId)))
                        _events.send(UiEvent.PlaySound(SoundPlayer.Sound.Correct))
                        _events.send(UiEvent.Message("Added ${s.added} words to “${s.deckName}”.$skippedNote"))
                    }
                    is ImportResult.Invalid -> _importState.update { it.copy(isImporting = false, error = result.reason) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                _importState.update { it.copy(isImporting = false, error = "Import failed. Nothing was saved, try again.") }
            }
        }
    }

    private fun reparse(debounceMs: Long = 0) {
        parseJob?.cancel()
        parseJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            val s = _importState.value
            val preview = withContext(Dispatchers.Default) {
                VocabImportParser.parse(s.text, swapColumns = s.swapColumns, skipFirstLine = s.skipFirstLine)
            }
            _importState.update { it.copy(preview = preview) }
        }
    }

    private fun launchSafely(errorMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, errorMessage, e)
                _events.send(UiEvent.Message(errorMessage))
            }
        }
    }

    companion object {
        private const val TAG = "WordsViewModel"

        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as VocabGuardApp
                WordsViewModel(
                    repository = app.container.wordRepository,
                    readFile = { uri -> TextFileReader.read(app, uri) },
                )
            }
        }
    }
}
