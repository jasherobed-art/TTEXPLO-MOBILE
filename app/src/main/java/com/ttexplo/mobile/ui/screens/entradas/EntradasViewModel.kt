package com.ttexplo.mobile.ui.screens.entradas

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ttexplo.mobile.TTExploApp
import com.ttexplo.mobile.data.local.entity.EntradaEntity
import com.ttexplo.mobile.data.local.entity.EntradaItemEntity
import com.ttexplo.mobile.data.local.entity.ProductoEntity
import com.ttexplo.mobile.data.repository.CatalogRepositoryImpl
import com.ttexplo.mobile.domain.model.ProductInfo
import com.ttexplo.mobile.domain.model.ProductType
import com.ttexplo.mobile.domain.scanner.BarcodeItem
import com.ttexplo.mobile.domain.scanner.DateTimeUtils
import com.ttexplo.mobile.domain.scanner.ScanResult
import com.ttexplo.mobile.domain.scanner.ScanValidator
import com.ttexplo.mobile.domain.scanner.TipoEmpaque
import com.ttexplo.mobile.ui.common.ScanFeedbackType
import com.ttexplo.mobile.ui.common.ScanFlashEvent
import com.ttexplo.mobile.ui.common.SoundFeedbackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ProductSummary(
    val id: String,
    val name: String,
    val presentation: String,
    val type: ProductType,
    val count: Int,
    val empaque: TipoEmpaque
)

data class EntradasUiState(
    val draftId: Long? = null,
    val proveedor: String = "",
    val polvorin: String = "",
    val factura: String = "",
    val scannedItems: List<BarcodeItem> = emptyList(),
    val latestScanResult: ScanResult? = null,
    val latestFlashEvent: ScanFlashEvent? = null,
    val wasRestoredFromDraft: Boolean = false,
    val isFinished: Boolean = false
)

class EntradasViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TTExploApp
    private val db = app.database
    private val inventoryRepository = app.inventoryRepository
    private val catalogRepository = CatalogRepositoryImpl(db.productoDao())
    private val scanValidator = ScanValidator(catalogRepository)
    private val soundFeedback = SoundFeedbackManager(application)
    private val scanMutex = Mutex()

    fun isVisualFlashEnabled(): Boolean = soundFeedback.isVisualFlashEnabled()

    private val _uiState = MutableStateFlow(EntradasUiState())
    val uiState: StateFlow<EntradasUiState> = _uiState.asStateFlow()

    val itemsByProduct: StateFlow<Map<String, Int>> = _uiState.map { state ->
        state.scannedItems
            .groupBy { "${it.productInfo.name} (${it.productInfo.presentation})" }
            .mapValues { it.value.size }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val productSummaries: StateFlow<List<ProductSummary>> = _uiState.map { state ->
        state.scannedItems
            .groupBy { Triple(it.productInfo.id, it.productInfo.presentation, it.empaque) }
            .map { (key, items) ->
                val first = items.first()
                ProductSummary(
                    id = key.first,
                    name = first.productInfo.name,
                    presentation = key.second,
                    type = first.productInfo.type,
                    count = items.size,
                    empaque = key.third
                )
            }
            .sortedWith(compareBy<ProductSummary> { it.empaque.ordinal }.thenByDescending { it.count })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        checkForDraft()
    }

    fun loadExistingEntrada(id: Long) {
        if (id <= 0) return
        viewModelScope.launch {
            val entrada = db.entradaDao().getEntradaById(id)
            if (entrada != null) {
                val dbItems = db.entradaDao().getItemsForEntrada(entrada.id)
                val domainItems = dbItems.map { entity ->
                    val product = catalogRepository.lookupBarcode(entity.codigoBarras)
                    BarcodeItem(
                        code = entity.codigoBarras,
                        timestamp = entity.fechaEscaneo,
                        productInfo = product,
                        empaque = when (entity.codigoBarras.length) {
                            30 -> TipoEmpaque.CAJA
                            18 -> TipoEmpaque.SACO
                            16, 17 -> TipoEmpaque.PIEZA
                            else -> TipoEmpaque.DESCONOCIDO
                        }
                    )
                }
                _uiState.value = _uiState.value.copy(
                    draftId = entrada.id,
                    proveedor = entrada.proveedor,
                    polvorin = entrada.polvorin,
                    factura = entrada.factura,
                    scannedItems = domainItems,
                    wasRestoredFromDraft = true
                )
            }
        }
    }

    fun registerProductInCatalog(entity: ProductoEntity) {
        viewModelScope.launch {
            db.productoDao().insert(entity)
            val pendingCode = _uiState.value.latestScanResult?.rawBarcode
            if (!pendingCode.isNullOrBlank() && _uiState.value.latestScanResult?.unknownId == entity.idProducto) {
                onBarcodeScanned(pendingCode)
            }
        }
    }

    private fun checkForDraft() {
        viewModelScope.launch {
            val draft = db.entradaDao().getActiveDraft()
            if (draft != null) {
                val dbItems = db.entradaDao().getItemsForEntrada(draft.id)
                val domainItems = dbItems.map { entity ->
                    val product = catalogRepository.lookupBarcode(entity.codigoBarras)
                    BarcodeItem(
                        code = entity.codigoBarras,
                        timestamp = entity.fechaEscaneo,
                        productInfo = product,
                        empaque = when (entity.codigoBarras.length) {
                            30 -> TipoEmpaque.CAJA
                            18 -> TipoEmpaque.SACO
                            16, 17 -> TipoEmpaque.PIEZA
                            else -> TipoEmpaque.DESCONOCIDO
                        }
                    )
                }
                _uiState.value = _uiState.value.copy(
                    draftId = draft.id,
                    proveedor = draft.proveedor,
                    polvorin = draft.polvorin,
                    factura = draft.factura,
                    scannedItems = domainItems,
                    wasRestoredFromDraft = true
                )
            }
        }
    }

    fun updateHeader(prov: String, polv: String, fact: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            proveedor = if (prov.isNotBlank()) prov else current.proveedor,
            polvorin = if (polv.isNotBlank()) polv else current.polvorin,
            factura = if (fact.isNotBlank()) fact else current.factura
        )
    }

    private suspend fun ensureDraftCreated(): Long {
        val current = _uiState.value
        if (current.draftId != null) return current.draftId

        val newId = db.entradaDao().insertEntrada(
            EntradaEntity(
                proveedor = current.proveedor,
                polvorin = current.polvorin,
                factura = current.factura,
                fechaCreacion = DateTimeUtils.currentTimestamp(),
                estado = "BORRADOR"
            )
        )
        _uiState.value = current.copy(draftId = newId)
        return newId
    }

    fun onBarcodeScanned(barcode: String) {
        val trimmed = barcode.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            scanMutex.withLock {
                val sessionCodes = _uiState.value.scannedItems.map { it.code }.toSet()
                val result = scanValidator.processScan(trimmed, sessionCodes, allowAccessories = false)

                if (!result.isValid) {
                    soundFeedback.playError()
                    _uiState.value = _uiState.value.copy(
                        latestScanResult = result,
                        latestFlashEvent = ScanFlashEvent(type = ScanFeedbackType.ERROR)
                    )
                    return@withLock
                }

                if (result.isDuplicate) {
                    soundFeedback.playWarning()
                    _uiState.value = _uiState.value.copy(
                        latestScanResult = result,
                        latestFlashEvent = ScanFlashEvent(type = ScanFeedbackType.WARNING)
                    )
                } else {
                    soundFeedback.playSuccess()
                    val draftId = ensureDraftCreated()

                    // Persist immediately in SQLite draft
                    db.entradaDao().insertSingleItem(
                        EntradaItemEntity(
                            entradaId = draftId,
                            codigoBarras = result.rawBarcode,
                            fechaEscaneo = result.timestamp,
                            productoId = result.productInfo.id,
                            productoNombre = result.productInfo.name,
                            presentacion = result.productInfo.presentation,
                            tipo = result.productInfo.type.name
                        )
                    )

                    val newItem = BarcodeItem(
                        code = result.rawBarcode,
                        timestamp = result.timestamp,
                        productInfo = result.productInfo,
                        empaque = result.empaque
                    )
                    val updatedList = listOf(newItem) + _uiState.value.scannedItems
                    _uiState.value = _uiState.value.copy(
                        scannedItems = updatedList,
                        latestScanResult = result,
                        latestFlashEvent = ScanFlashEvent(type = ScanFeedbackType.SUCCESS)
                    )
                }
            }
        }
    }

    fun deleteItem(item: BarcodeItem) {
        viewModelScope.launch {
            scanMutex.withLock {
                val draftId = _uiState.value.draftId
                if (draftId != null) {
                    db.entradaDao().deleteItem(draftId, item.code)
                }
                val updated = _uiState.value.scannedItems.filterNot { it.code == item.code }
                _uiState.value = _uiState.value.copy(
                    scannedItems = updated,
                    latestScanResult = ScanResult(
                        rawBarcode = item.code,
                        timestamp = DateTimeUtils.currentTimestamp(),
                        productInfo = item.productInfo,
                        isDuplicate = false,
                        isValid = true,
                        message = "Código ${item.code} eliminado"
                    )
                )
            }
        }
    }

    fun finishAndSave(onSuccess: () -> Unit) {
        viewModelScope.launch {
            scanMutex.withLock {
                val state = _uiState.value
                val draftId = state.draftId
                val savedEntradaId: Long? = if (draftId != null) {
                    db.entradaDao().updateEstado(draftId, "FINALIZADO")
                    draftId
                } else if (state.scannedItems.isNotEmpty()) {
                    val newId = db.entradaDao().insertEntrada(
                        EntradaEntity(
                            proveedor = state.proveedor,
                            polvorin = state.polvorin,
                            factura = state.factura,
                            fechaCreacion = DateTimeUtils.currentTimestamp(),
                            estado = "FINALIZADO"
                        )
                    )
                    val items = state.scannedItems.map {
                        EntradaItemEntity(
                            entradaId = newId,
                            codigoBarras = it.code,
                            fechaEscaneo = it.timestamp,
                            productoId = it.productInfo.id,
                            productoNombre = it.productInfo.name,
                            presentacion = it.productInfo.presentation,
                            tipo = it.productInfo.type.name
                        )
                    }
                    db.entradaDao().insertItems(items)
                    newId
                } else {
                    null
                }

                if (savedEntradaId != null && state.scannedItems.isNotEmpty()) {
                    inventoryRepository.recordEntrada(
                        entradaId = savedEntradaId,
                        proveedor = state.proveedor,
                        polvorin = state.polvorin,
                        factura = state.factura,
                        items = state.scannedItems
                    )
                }

                _uiState.value = _uiState.value.copy(isFinished = true, draftId = null)
            }
            onSuccess()
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundFeedback.release()
    }
}
