package com.ttexplo.mobile.ui.screens.accesorios

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ttexplo.mobile.TTExploApp
import com.ttexplo.mobile.data.local.entity.AccesorioItemEntity
import com.ttexplo.mobile.data.local.entity.AccesorioValeEntity
import com.ttexplo.mobile.data.repository.SettingsRepository
import com.ttexplo.mobile.domain.model.AccesoriosRecord
import com.ttexplo.mobile.domain.model.AccessoryItemRecord
import com.ttexplo.mobile.domain.scanner.DateTimeUtils
import com.ttexplo.mobile.printer.BluetoothPrinterManager
import com.ttexplo.mobile.printer.PrinterProfile
import com.ttexplo.mobile.printer.ValePrintHelper
import com.ttexplo.mobile.ui.common.ScanFeedbackType
import com.ttexplo.mobile.ui.common.ScanFlashEvent
import com.ttexplo.mobile.ui.common.SoundFeedbackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AccessorySummary(
    val code: String,
    val totalQuantity: Int,
    val countEntries: Int
)

data class AccesoriosUiState(
    val draftId: Long? = null,
    val isDevolucion: Boolean = false,
    val vale: String = "",
    val empresa: String = "",
    val lugar: String = "",
    val transportista: String = "",
    val turno: String = "",
    val recibio: String = "",
    val entrego: String = "",
    val voladura: String = "",
    val items: List<AccessoryItemRecord> = emptyList(),
    val summaries: List<AccessorySummary> = emptyList(),
    val totalPieces: Int = 0,
    val wasRestoredFromDraft: Boolean = false,
    val isPrinting: Boolean = false,
    val printStatusMessage: String? = null,
    val lastScanMessage: String? = null,
    val latestFlashEvent: ScanFlashEvent? = null
)

class AccesoriosViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TTExploApp
    private val db = app.database
    private val inventoryRepository = app.inventoryRepository
    private val soundFeedback = SoundFeedbackManager(application)
    private val scanMutex = Mutex()
    val printerManager = BluetoothPrinterManager(application)
    val settingsRepository = SettingsRepository(application)

    fun isVisualFlashEnabled(): Boolean = soundFeedback.isVisualFlashEnabled()

    private val _uiState = MutableStateFlow(AccesoriosUiState())
    val uiState: StateFlow<AccesoriosUiState> = _uiState.asStateFlow()

    init {
        checkForDraft()
    }

    fun loadExistingVale(id: Long) {
        if (id <= 0) return
        viewModelScope.launch {
            val vale = db.accesorioDao().getValeById(id)
            if (vale != null) {
                val dbItems = db.accesorioDao().getItemsForVale(vale.id)
                val domainItems = dbItems.map {
                    AccessoryItemRecord(
                        code = it.codigoAccesorio,
                        quantity = it.cantidad,
                        timestamp = it.fechaEscaneo
                    )
                }
                val total = domainItems.sumOf { it.quantity }
                val isDev = domainItems.any { it.quantity < 0 }
                _uiState.value = _uiState.value.copy(
                    draftId = vale.id,
                    isDevolucion = isDev,
                    vale = vale.vale,
                    empresa = vale.empresa,
                    lugar = vale.lugar,
                    transportista = vale.transportista,
                    turno = vale.turno,
                    recibio = vale.recibio,
                    entrego = vale.entrego,
                    voladura = vale.voladura,
                    items = domainItems,
                    summaries = calculateSummaries(domainItems),
                    totalPieces = total,
                    wasRestoredFromDraft = true
                )
            }
        }
    }

    fun setDevolucionMode(isDevolucion: Boolean) {
        _uiState.value = _uiState.value.copy(isDevolucion = isDevolucion)
    }

    private fun checkForDraft() {
        viewModelScope.launch {
            val draft = db.accesorioDao().getActiveDraft()
            if (draft != null) {
                val dbItems = db.accesorioDao().getItemsForVale(draft.id)
                val domainItems = dbItems.map {
                    AccessoryItemRecord(
                        code = it.codigoAccesorio,
                        quantity = it.cantidad,
                        timestamp = it.fechaEscaneo
                    )
                }
                val total = domainItems.sumOf { it.quantity }
                val isDev = domainItems.any { it.quantity < 0 }
                _uiState.value = _uiState.value.copy(
                    draftId = draft.id,
                    isDevolucion = isDev,
                    vale = draft.vale,
                    empresa = draft.empresa,
                    lugar = draft.lugar,
                    transportista = draft.transportista,
                    turno = draft.turno,
                    recibio = draft.recibio,
                    entrego = draft.entrego,
                    voladura = draft.voladura,
                    items = domainItems,
                    summaries = calculateSummaries(domainItems),
                    totalPieces = total,
                    wasRestoredFromDraft = true
                )
            }
        }
    }

    fun updateHeader(
        vale: String, empresa: String, lugar: String, transportista: String,
        turno: String, recibio: String, entrego: String, voladura: String
    ) {
        val current = _uiState.value
        _uiState.value = current.copy(
            vale = if (vale.isNotBlank()) vale else current.vale,
            empresa = if (empresa.isNotBlank()) empresa else current.empresa,
            lugar = if (lugar.isNotBlank()) lugar else current.lugar,
            transportista = if (transportista.isNotBlank()) transportista else current.transportista,
            turno = if (turno.isNotBlank()) turno else current.turno,
            recibio = if (recibio.isNotBlank()) recibio else current.recibio,
            entrego = if (entrego.isNotBlank()) entrego else current.entrego,
            voladura = if (voladura.isNotBlank()) voladura else current.voladura
        )
    }

    private suspend fun ensureDraftCreated(): Long {
        val current = _uiState.value
        if (current.draftId != null) return current.draftId

        val newId = db.accesorioDao().insertVale(
            AccesorioValeEntity(
                vale = current.vale,
                empresa = current.empresa,
                lugar = current.lugar,
                transportista = current.transportista,
                turno = current.turno,
                recibio = current.recibio,
                entrego = current.entrego,
                voladura = current.voladura,
                fechaCreacion = DateTimeUtils.currentTimestamp(),
                estado = "BORRADOR"
            )
        )
        _uiState.value = current.copy(draftId = newId)
        return newId
    }

    fun onAccessoryScanned(code: String, quantity: Int) {
        val trimmed = code.trim()
        if (trimmed.isEmpty() || quantity == 0) return

        if (!trimmed.startsWith("X", ignoreCase = true)) {
            soundFeedback.playError()
            _uiState.value = _uiState.value.copy(
                lastScanMessage = "Error: El código de accesorio debe iniciar con 'X'",
                latestFlashEvent = ScanFlashEvent(type = ScanFeedbackType.ERROR)
            )
            return
        }

        val finalQty = if (_uiState.value.isDevolucion) -Math.abs(quantity) else Math.abs(quantity)

        viewModelScope.launch {
            scanMutex.withLock {
                val draftId = ensureDraftCreated()
                val timestamp = DateTimeUtils.currentTimestamp()

                db.accesorioDao().insertSingleItem(
                    AccesorioItemEntity(
                        valeId = draftId,
                        codigoAccesorio = trimmed,
                        cantidad = finalQty,
                        fechaEscaneo = timestamp
                    )
                )

                val newItem = AccessoryItemRecord(
                    code = trimmed,
                    quantity = finalQty,
                    timestamp = timestamp
                )
                val updatedItems = listOf(newItem) + _uiState.value.items
                val total = updatedItems.sumOf { it.quantity }

                soundFeedback.playSuccess()

                _uiState.value = _uiState.value.copy(
                    items = updatedItems,
                    summaries = calculateSummaries(updatedItems),
                    totalPieces = total,
                    lastScanMessage = if (_uiState.value.isDevolucion) "Devuelto: $trimmed (${finalQty} pzas)" else "Agregado: $trimmed x $finalQty pzas",
                    latestFlashEvent = ScanFlashEvent(type = ScanFeedbackType.SUCCESS)
                )
            }
        }
    }

    fun deleteItem(item: AccessoryItemRecord) {
        viewModelScope.launch {
            scanMutex.withLock {
                val draftId = _uiState.value.draftId
                if (draftId != null) {
                    db.accesorioDao().deleteItem(draftId, item.code)
                }
                val updated = _uiState.value.items.filterNot { it.code == item.code && it.timestamp == item.timestamp }
                val total = updated.sumOf { it.quantity }
                _uiState.value = _uiState.value.copy(
                    items = updated,
                    summaries = calculateSummaries(updated),
                    totalPieces = total,
                    lastScanMessage = "Accesorio ${item.code} eliminado"
                )
            }
        }
    }

    private fun calculateSummaries(items: List<AccessoryItemRecord>): List<AccessorySummary> {
        return items
            .groupBy { it.code }
            .map { (code, group) ->
                AccessorySummary(
                    code = code,
                    totalQuantity = group.sumOf { it.quantity },
                    countEntries = group.size
                )
            }
            .sortedByDescending { it.totalQuantity }
    }

    fun finishAndSave(onSuccess: () -> Unit) {
        viewModelScope.launch {
            scanMutex.withLock {
                val state = _uiState.value
                val draftId = state.draftId
                val savedValeId: Long? = if (draftId != null) {
                    db.accesorioDao().updateEstado(draftId, "FINALIZADO")
                    draftId
                } else if (state.items.isNotEmpty()) {
                    val newId = db.accesorioDao().insertVale(
                        AccesorioValeEntity(
                            vale = state.vale,
                            empresa = state.empresa,
                            lugar = state.lugar,
                            transportista = state.transportista,
                            turno = state.turno,
                            recibio = state.recibio,
                            entrego = state.entrego,
                            voladura = state.voladura,
                            fechaCreacion = DateTimeUtils.currentTimestamp(),
                            estado = "FINALIZADO"
                        )
                    )
                    val items = state.items.map {
                        AccesorioItemEntity(
                            valeId = newId,
                            codigoAccesorio = it.code,
                            cantidad = it.quantity,
                            fechaEscaneo = it.timestamp
                        )
                    }
                    db.accesorioDao().insertItems(items)
                    newId
                } else {
                    null
                }

                if (savedValeId != null && state.items.isNotEmpty()) {
                    inventoryRepository.recordAccesoriosVale(
                        valeId = savedValeId,
                        vale = state.vale,
                        empresa = state.empresa,
                        lugar = state.lugar,
                        isDevolucion = state.isDevolucion,
                        items = state.items
                    )
                }

                _uiState.value = _uiState.value.copy(draftId = null)
            }
            onSuccess()
        }
    }

    fun printTicket(
        profile: PrinterProfile? = null,
        onComplete: (Boolean, String) -> Unit
    ) {
        val state = _uiState.value
        val record = AccesoriosRecord(
            vale = state.vale,
            empresa = state.empresa,
            lugar = state.lugar,
            transportista = state.transportista,
            turno = state.turno,
            recibio = state.recibio,
            entrego = state.entrego,
            voladura = state.voladura,
            items = state.items
        )

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPrinting = true, printStatusMessage = null)
            val outcome = ValePrintHelper.printAccesorios(app, record, profile)
            _uiState.value = _uiState.value.copy(
                isPrinting = false,
                printStatusMessage = outcome.message
            )
            onComplete(outcome.success, outcome.message)
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundFeedback.release()
    }
}
