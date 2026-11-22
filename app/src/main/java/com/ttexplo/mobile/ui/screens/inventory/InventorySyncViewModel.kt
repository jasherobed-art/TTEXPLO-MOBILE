package com.ttexplo.mobile.ui.screens.inventory

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ttexplo.mobile.TTExploApp
import com.ttexplo.mobile.data.local.entity.SyncSessionDirection
import com.ttexplo.mobile.data.local.entity.SyncSessionEntity
import com.ttexplo.mobile.data.repository.SettingsRepository
import com.ttexplo.mobile.domain.inventory.QrChunkIngestResult
import com.ttexplo.mobile.domain.inventory.QrDensityMode
import com.ttexplo.mobile.domain.inventory.QrSyncReassembler
import com.ttexplo.mobile.domain.inventory.SyncConflict
import com.ttexplo.mobile.domain.inventory.SyncExportPackage
import com.ttexplo.mobile.domain.inventory.SyncExportScope
import com.ttexplo.mobile.domain.inventory.SyncMergeReport
import com.ttexplo.mobile.license.LicenseManager
import com.ttexplo.mobile.ui.common.SoundFeedbackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class QrReceiverProgressState(
    val sessionId: String? = null,
    val totalParts: Int = 0,
    val receivedParts: Set<Int> = emptySet(),
    val statusMessage: String? = null,
    val isError: Boolean = false
)

data class InventorySyncUiState(
    val deviceId: String = "",
    val polvorinName: String = "POLVORÍN PRINCIPAL",
    val selectedScope: SyncExportScope = SyncExportScope.PENDING_ONLY,
    val densityMode: QrDensityMode = QrDensityMode.HIGH_CAPACITY,
    val exportPackage: SyncExportPackage? = null,
    val isBuildingExport: Boolean = false,
    val activeQrIndex: Int = 0,
    val isAutoPlay: Boolean = false,
    val autoPlayIntervalMs: Long = 2000L,
    val receiverState: QrReceiverProgressState = QrReceiverProgressState(),
    val lastMergeReport: SyncMergeReport? = null,
    val demoChunksRemaining: Int = 0,
    val demoChunksTotal: Int = 0,
    val activeConflicts: List<SyncConflict> = emptyList(),
    val reviewedConflictsCount: Int = 0,
    val syncSessions: List<SyncSessionEntity> = emptyList(),
    val bannerMessage: String? = null,
    val bannerIsError: Boolean = false
)

class InventorySyncViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TTExploApp
    private val inventoryRepository = app.inventoryRepository
    private val settingsRepository = SettingsRepository(application)
    private val soundFeedback = SoundFeedbackManager(application)
    private val prefs = application.getSharedPreferences("ttexplo_sync_prefs", Context.MODE_PRIVATE)

    private val qrReassembler = QrSyncReassembler()
    private var demoChunkQueue: List<String> = emptyList()
    private var demoChunkCursor: Int = 0

    private val _selectedScope = MutableStateFlow(SyncExportScope.PENDING_ONLY)
    private val _densityMode = MutableStateFlow(QrDensityMode.HIGH_CAPACITY)
    private val _exportPackage = MutableStateFlow<SyncExportPackage?>(null)
    private val _isBuildingExport = MutableStateFlow(false)
    private val _activeQrIndex = MutableStateFlow(0)
    private val _isAutoPlay = MutableStateFlow(false)
    private val _autoPlayIntervalMs = MutableStateFlow(2000L)
    private val _receiverState = MutableStateFlow(QrReceiverProgressState())
    private val _lastMergeReport = MutableStateFlow<SyncMergeReport?>(null)
    private val _demoProgress = MutableStateFlow(0 to 0) // remaining to total
    private val _reviewedConflictIds = MutableStateFlow(loadReviewedConflictIds())
    private val _banner = MutableStateFlow<Pair<String, Boolean>?>(null)

    private data class ExportControlState(
        val scope: SyncExportScope,
        val density: QrDensityMode,
        val pkg: SyncExportPackage?,
        val building: Boolean,
        val qrIndex: Int,
        val autoPlay: Boolean,
        val intervalMs: Long
    )

    private val exportFlow = combine(
        _selectedScope,
        _densityMode,
        _exportPackage,
        _isBuildingExport,
        _activeQrIndex,
        _isAutoPlay,
        _autoPlayIntervalMs
    ) { arr ->
        ExportControlState(
            scope = arr[0] as SyncExportScope,
            density = arr[1] as QrDensityMode,
            pkg = arr[2] as SyncExportPackage?,
            building = arr[3] as Boolean,
            qrIndex = arr[4] as Int,
            autoPlay = arr[5] as Boolean,
            intervalMs = arr[6] as Long
        )
    }

    private data class ReceiveControlState(
        val receiver: QrReceiverProgressState,
        val mergeReport: SyncMergeReport?,
        val demoProgress: Pair<Int, Int>,
        val banner: Pair<String, Boolean>?
    )

    private val receiveFlow = combine(
        _receiverState,
        _lastMergeReport,
        _demoProgress,
        _banner
    ) { receiver, report, demo, banner ->
        ReceiveControlState(receiver, report, demo, banner)
    }

    val uiState: StateFlow<InventorySyncUiState> = combine(
        exportFlow,
        receiveFlow,
        inventoryRepository.observeActiveConflicts(),
        _reviewedConflictIds,
        inventoryRepository.observeSyncSessions()
    ) { exp, rec, allConflicts, reviewedIds, sessions ->
        val unreviewed = allConflicts.filter { it.conflictId !in reviewedIds }
        val reviewedCount = allConflicts.size - unreviewed.size
        InventorySyncUiState(
            deviceId = LicenseManager.getFormattedDeviceId(app),
            polvorinName = settingsRepository.getTicketHeader().ifBlank { "POLVORÍN PRINCIPAL" },
            selectedScope = exp.scope,
            densityMode = exp.density,
            exportPackage = exp.pkg,
            isBuildingExport = exp.building,
            activeQrIndex = exp.qrIndex,
            isAutoPlay = exp.autoPlay,
            autoPlayIntervalMs = exp.intervalMs,
            receiverState = rec.receiver,
            lastMergeReport = rec.mergeReport,
            demoChunksRemaining = rec.demoProgress.first,
            demoChunksTotal = rec.demoProgress.second,
            activeConflicts = unreviewed,
            reviewedConflictsCount = reviewedCount,
            syncSessions = sessions,
            bannerMessage = rec.banner?.first,
            bannerIsError = rec.banner?.second ?: false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InventorySyncUiState(
            deviceId = LicenseManager.getFormattedDeviceId(app),
            polvorinName = settingsRepository.getTicketHeader().ifBlank { "POLVORÍN PRINCIPAL" }
        )
    )

    init {
        rebuildExportPackage(SyncExportScope.PENDING_ONLY, QrDensityMode.HIGH_CAPACITY)
    }

    fun clearBanner() {
        _banner.value = null
    }

    fun selectExportScope(scope: SyncExportScope) {
        _selectedScope.value = scope
        _isAutoPlay.value = false
        rebuildExportPackage(scope, _densityMode.value)
    }

    fun selectDensityMode(mode: QrDensityMode) {
        if (_densityMode.value == mode) return
        _densityMode.value = mode
        _isAutoPlay.value = false
        rebuildExportPackage(_selectedScope.value, mode)
    }

    fun refreshExportPackage() {
        rebuildExportPackage(_selectedScope.value, _densityMode.value)
    }

    private fun rebuildExportPackage(
        scope: SyncExportScope,
        density: QrDensityMode = _densityMode.value
    ) {
        viewModelScope.launch {
            _isBuildingExport.value = true
            try {
                val pkg = inventoryRepository.buildSyncExportPackage(scope, densityMode = density)
                _exportPackage.value = pkg
                _activeQrIndex.value = 0
                if (pkg.qrPackage.chunks.size <= 1) {
                    _isAutoPlay.value = false
                }
            } finally {
                _isBuildingExport.value = false
            }
        }
    }

    fun prevQr() {
        val total = _exportPackage.value?.qrPackage?.chunks?.size ?: 0
        if (total <= 1) return
        val cur = _activeQrIndex.value
        _activeQrIndex.value = if (cur <= 0) total - 1 else cur - 1
    }

    fun nextQr() {
        val total = _exportPackage.value?.qrPackage?.chunks?.size ?: 0
        if (total <= 1) return
        _activeQrIndex.value = (_activeQrIndex.value + 1) % total
    }

    fun toggleAutoPlay() {
        val total = _exportPackage.value?.qrPackage?.chunks?.size ?: 0
        if (total <= 1) {
            _isAutoPlay.value = false
            return
        }
        _isAutoPlay.value = !_isAutoPlay.value
    }

    fun stopAutoPlay() {
        _isAutoPlay.value = false
    }

    fun toggleAutoPlayInterval() {
        _autoPlayIntervalMs.value = if (_autoPlayIntervalMs.value <= 1500L) 2500L else 1500L
    }

    fun confirmQrShared(markPendingAsSynced: Boolean = true) {
        val pkg = _exportPackage.value ?: return
        if (pkg.eventUuids.isEmpty() && pkg.codeEventUuids.isEmpty()) return
        viewModelScope.launch {
            inventoryRepository.markExportSessionCompleted(
                pkg = pkg,
                direction = SyncSessionDirection.EXPORT_QR,
                markPendingAsSynced = markPendingAsSynced
            )
            soundFeedback.playSuccess()
            _isAutoPlay.value = false
            _banner.value = "Paquete QR (${pkg.eventUuids.size} mov., ${pkg.codeEventUuids.size} cód.) marcado como compartido." to false
            rebuildExportPackage(_selectedScope.value)
        }
    }

    fun recordJsonExported(pkg: SyncExportPackage, markPendingAsSynced: Boolean = true) {
        viewModelScope.launch {
            inventoryRepository.markExportSessionCompleted(
                pkg = pkg,
                direction = SyncSessionDirection.EXPORT_JSON,
                markPendingAsSynced = markPendingAsSynced
            )
            soundFeedback.playSuccess()
            _banner.value = "Archivo JSON exportado (${pkg.eventUuids.size} mov., ${pkg.codeEventUuids.size} cód.) y registrado en historial." to false
            rebuildExportPackage(_selectedScope.value)
        }
    }

    /**
     * Procesa cualquier lectura del lector óptico Zebra (o pegado manual) en la pestaña Recibir.
     * Soporta paquetes QR multi-parte en cualquier orden y también JSON crudo pegado directamente.
     */
    fun onScannerInputReceived(rawInput: String) {
        val clean = rawInput.trim()
        if (clean.isEmpty()) return

        // Si el usuario pegó JSON completo directamente
        if (clean.startsWith("{") && clean.endsWith("}")) {
            importJsonPayload(clean, SyncSessionDirection.IMPORT_JSON)
            return
        }

        when (val result = qrReassembler.acceptScan(clean)) {
            is QrChunkIngestResult.Progress -> {
                if (result.wasDuplicatePart) {
                    soundFeedback.playWarning()
                } else {
                    soundFeedback.playSuccess()
                }
                val dupSuffix = if (result.wasDuplicatePart) " (ya recibido)" else ""
                _receiverState.value = QrReceiverProgressState(
                    sessionId = result.sessionId,
                    totalParts = result.totalParts,
                    receivedParts = result.receivedParts,
                    statusMessage = "Bloque ${result.justAddedPart}/${result.totalParts} recibido$dupSuffix • Faltan ${result.totalParts - result.receivedParts.size}",
                    isError = false
                )
            }
            is QrChunkIngestResult.Complete -> {
                soundFeedback.playSuccess()
                _receiverState.value = QrReceiverProgressState(
                    sessionId = null,
                    totalParts = 0,
                    receivedParts = emptySet(),
                    statusMessage = "Paquete QR ${result.sessionId} (${result.totalParts}/${result.totalParts}) verificado con CRC32. Fusionando...",
                    isError = false
                )
                importJsonPayload(result.json, SyncSessionDirection.IMPORT_QR)
            }
            is QrChunkIngestResult.Error -> {
                soundFeedback.playError()
                _receiverState.value = _receiverState.value.copy(
                    sessionId = qrReassembler.activeSessionId,
                    totalParts = qrReassembler.totalParts,
                    receivedParts = qrReassembler.receivedPartIndices,
                    statusMessage = result.reason,
                    isError = true
                )
            }
        }
    }

    fun resetQrReceiver() {
        qrReassembler.reset()
        demoChunkQueue = emptyList()
        demoChunkCursor = 0
        _demoProgress.value = 0 to 0
        _receiverState.value = QrReceiverProgressState(
            statusMessage = "Receptor reiniciado. Apunte el lector Zebra al QR de otra terminal.",
            isError = false
        )
    }

    fun importJsonFileContent(jsonText: String) {
        importJsonPayload(jsonText, SyncSessionDirection.IMPORT_JSON)
    }

    private fun importJsonPayload(jsonText: String, sourceDirection: String) {
        viewModelScope.launch {
            val result = inventoryRepository.importSyncJson(jsonText, sourceDirection)
            result.fold(
                onSuccess = { report ->
                    _lastMergeReport.value = report
                    val totalNew = report.eventsMerged + report.codeEventsMerged
                    val totalDup = report.eventsSkippedDuplicate + report.codeEventsSkippedDuplicate
                    if (report.conflicts.isNotEmpty()) {
                        soundFeedback.playWarning()
                    } else {
                        soundFeedback.playSuccess()
                    }
                    _receiverState.value = QrReceiverProgressState(
                        statusMessage = "Fusión completada desde ${report.remoteDeviceId}: +$totalNew nuevos, $totalDup duplicados omitidos.",
                        isError = false
                    )
                    _banner.value = if (report.conflicts.isNotEmpty()) {
                        "Fusión completada (+$totalNew nuevos) con ${report.conflicts.size} conflicto(s) por revisar." to true
                    } else {
                        "Fusión idempotente exitosa desde ${report.remoteDeviceId}: +$totalNew nuevos ($totalDup dup. omitidos)." to false
                    }
                    rebuildExportPackage(_selectedScope.value)
                },
                onFailure = { err ->
                    soundFeedback.playError()
                    val msg = err.message ?: "No se pudo importar el paquete de sincronización."
                    _receiverState.value = _receiverState.value.copy(
                        statusMessage = msg,
                        isError = true
                    )
                    _banner.value = msg to true
                }
            )
        }
    }

    /**
     * Simulador de MODO PRUEBA (V3):
     * - `simulateSingleQrDemo`: recibe un turno completo de `ZBR-POLV-02` en **1 solo QR V3** (1 disparo).
     * - `simulateNextDemoQr`: fuerza fragmentación multi-parte en orden inverso para probar avance por bloques.
     */
    fun simulateSingleQrDemo(includeConflict: Boolean = true) {
        viewModelScope.launch {
            qrReassembler.reset()
            val chunks = inventoryRepository.generateDemoRemoteSyncQrChunks(
                includeConflictSample = includeConflict,
                forceMultiPart = false
            )
            demoChunkQueue = emptyList()
            demoChunkCursor = 0
            _demoProgress.value = 0 to chunks.size
            for (chunk in chunks) {
                onScannerInputReceived(chunk)
            }
        }
    }

    fun simulateNextDemoQr(includeConflict: Boolean = true) {
        viewModelScope.launch {
            if (demoChunkQueue.isEmpty() || demoChunkCursor >= demoChunkQueue.size) {
                qrReassembler.reset()
                demoChunkQueue = inventoryRepository.generateDemoRemoteSyncQrChunks(
                    includeConflictSample = includeConflict,
                    forceMultiPart = true
                ).reversed()
                demoChunkCursor = 0
            }
            val nextChunk = demoChunkQueue[demoChunkCursor++]
            val remaining = (demoChunkQueue.size - demoChunkCursor).coerceAtLeast(0)
            _demoProgress.value = remaining to demoChunkQueue.size
            onScannerInputReceived(nextChunk)
            if (remaining == 0) {
                demoChunkQueue = emptyList()
                demoChunkCursor = 0
            }
        }
    }

    fun markConflictReviewed(conflictId: String) {
        val updated = _reviewedConflictIds.value + conflictId
        _reviewedConflictIds.value = updated
        prefs.edit().putStringSet(KEY_REVIEWED_CONFLICTS, updated).apply()
        _banner.value = "Conflicto marcado como revisado." to false
    }

    fun restoreReviewedConflicts() {
        _reviewedConflictIds.value = emptySet()
        prefs.edit().remove(KEY_REVIEWED_CONFLICTS).apply()
        _banner.value = "Se restauraron todos los conflictos detectados en el libro." to false
    }

    private fun loadReviewedConflictIds(): Set<String> {
        return prefs.getStringSet(KEY_REVIEWED_CONFLICTS, emptySet())?.toSet() ?: emptySet()
    }

    override fun onCleared() {
        super.onCleared()
        soundFeedback.release()
    }

    companion object {
        private const val KEY_REVIEWED_CONFLICTS = "reviewed_conflict_ids"
    }
}
