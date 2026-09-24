package id.local.shopeeextractor.session

import id.local.shopeeextractor.data.CaptureRepository
import id.local.shopeeextractor.dedupe.DeduplicationEngine
import id.local.shopeeextractor.parser.ParsedTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CaptureUiState(
    val state: CaptureState = CaptureState.IDLE,
    val sessionId: Long? = null,
    val uniqueCount: Int = 0,
    val duplicateSkippedCount: Int = 0,
)

class CaptureCoordinator(private val repository: CaptureRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val dedupe = DeduplicationEngine()
    private var sequenceIndex = 0

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state

    fun ready() {
        if (_state.value.state == CaptureState.IDLE) {
            _state.update { it.copy(state = CaptureState.READY) }
        }
    }

    fun start() {
        if (_state.value.state == CaptureState.RECORDING) return
        scope.launch {
            mutex.withLock {
                dedupe.reset()
                sequenceIndex = 0
                val sessionId = repository.createSession()
                _state.value = CaptureUiState(state = CaptureState.RECORDING, sessionId = sessionId)
            }
        }
    }

    fun stop() {
        val snapshot = _state.value
        if (snapshot.state != CaptureState.RECORDING || snapshot.sessionId == null) return
        scope.launch {
            mutex.withLock {
                repository.stopSession(snapshot.sessionId, dedupe.duplicateSkippedCount)
                _state.value = snapshot.copy(
                    state = CaptureState.STOPPED,
                    duplicateSkippedCount = dedupe.duplicateSkippedCount,
                )
            }
        }
    }

    fun onVisibleTransactions(transactions: List<ParsedTransaction>) {
        val snapshot = _state.value
        if (snapshot.state != CaptureState.RECORDING || snapshot.sessionId == null) return
        scope.launch {
            mutex.withLock {
                val accepted = dedupe.acceptVisibleWindow(transactions)
                repository.addTransactions(snapshot.sessionId, sequenceIndex, accepted)
                sequenceIndex += accepted.size
                _state.value = snapshot.copy(
                    uniqueCount = sequenceIndex,
                    duplicateSkippedCount = dedupe.duplicateSkippedCount,
                )
            }
        }
    }
}
