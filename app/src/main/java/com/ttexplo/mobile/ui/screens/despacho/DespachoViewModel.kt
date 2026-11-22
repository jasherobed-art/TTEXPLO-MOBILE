package com.ttexplo.mobile.ui.screens.despacho

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ttexplo.mobile.TTExploApp
import com.ttexplo.mobile.data.local.entity.DespachoEntity
import com.ttexplo.mobile.data.local.entity.DespachoItemEntity
import com.ttexplo.mobile.data.local.entity.ProductoEntity
import com.ttexplo.mobile.data.repository.CatalogRepositoryImpl
import com.ttexplo.mobile.data.repository.SettingsRepository
import com.ttexplo.mobile.domain.model.DespachoRecord
import com.ttexplo.mobile.domain.model.ProductInfo
import com.ttexplo.mobile.domain.model.ProductType
import com.ttexplo.mobile.domain.model.ScannedCodeRecord
import com.ttexplo.mobile.domain.scanner.BarcodeItem
import com.ttexplo.mobile.domain.scanner.DateTimeUtils
import com.ttexplo.mobile.domain.scanner.ScanResult
import com.ttexplo.mobile.domain.scanner.ScanValidator
import com.ttexplo.mobile.domain.scanner.TipoEmpaque
import com.ttexplo.mobile.printer.BluetoothPrinterManager
import com.ttexplo.mobile.printer.TicketGenerator
import com.ttexplo.mobile.ui.common.ScanFeedbackType
import com.ttexplo.mobile.ui.common.ScanFlashEvent
import com.ttexplo.mobile.ui.common.SoundFeedbackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ProductMarker(
    val productName: String,
    val presentation: String,
    val count: Int,
    val type: ProductType = ProductType.ALTO_EXPLOSIVO,
    val empaque: TipoEmpaque = TipoEmpaque.CAJA
)

data class DespachoUiState(
    val draftId: Long? = null,
    val vale: String = "",
    val empresa: String = "",
    val lugar: String = "",
    val transportista: String = "",
    val turno: String = "",
    val voladura: String = "",
    val scannedItems: List<BarcodeItem> = emptyList(),
    val latestScanResult: ScanResult? = null,
    val latestFlashEvent: ScanFlashEvent? = null,
    val markers: List<ProductMarker> = emptyList(),
    val wasRestoredFromDraft: Boolean = false,
    val isPrinting: Boolean = false,
    val printStatusMessage: String? = null
)

class DespachoViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TTExploApp
    private val db = app.database
    private val inventoryRepository = app.inventoryRepository
    private val catalogRepository = CatalogRepositoryImpl(db.productoDao())
    val settingsRepository = SettingsRepository(application)
    private val scanValidator = ScanValidator(catalogRepository)
    private val soundFeedback = SoundFeedbackManager(application)
    private val scanMutex = Mutex()
    val printerManager = BluetoothPrinterManager(application)

    fun isVisualFlashEnabled(): Boolean = soundFeedback.isVisualFlashEnabled()

    fun savePreferredPrinter(mac: String, name: String) {
        settingsRepository.setPreferredPrinterMac(mac)
        settingsRepository.setPreferredPrinterName(name)
    }

    private val _uiState = MutableStateFlow(DespachoUiState())
    val uiState: StateFlow<DespachoUiState> = _uiState.asStateFlow()

    init {
        checkForDraft()
    }

    fun loadExistingDespacho(id: Long) {
        if (id <= 0) return
        viewModelScope.launch {
            val despacho = db.despachoDao().getDespachoById(id)
            if (despacho != null) {
                val dbItems = db.despachoDao().getItemsForDespacho(despacho.id)
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
                val markers = calculateMarkers(domainItems)
                _uiState.value = _uiState.value.copy(
                    draftId = despacho.id,
                    vale = despacho.vale,
                    empresa = despacho.empresa,
                    lugar = despacho.lugar,
                    transportista = despacho.transportista,
                    turno = despacho.turno,
                    voladura = despacho.voladura,
                    scannedItems = domainItems,
                    markers = markers,
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
            val draft = db.despachoDao().getActiveDraft()
            if (draft != null) {
                val dbItems = db.despachoDao().getItemsForDespacho(draft.id)
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
                val markers = calculateMarkers(domainItems)
                _uiState.value = _uiState.value.copy(
                    draftId = draft.id,
                    vale = draft.vale,
                    empresa = draft.empresa,
                    lugar = draft.lugar,
                    transportista = draft.transportista,
                    turno = draft.turno,
                    voladura = draft.voladura,
                    scannedItems = domainItems,
                    markers = markers,
                    wasRestoredFromDraft = true
                )
            }
        }
    }

    fun updateHeader(
        vale: String, empresa: String, lugar: String,
        transportista: String, turno: String, voladura: String
    ) {
        val current = _uiState.value
        _uiState.value = current.copy(
            vale = if (vale.isNotBlank()) vale else current.vale,
            empresa = if (empresa.isNotBlank()) empresa else current.empresa,
            lugar = if (lugar.isNotBlank()) lugar else current.lugar,
            transportista = if (transportista.isNotBlank()) transportista else current.transportista,
            turno = if (turno.isNotBlank()) turno else current.turno,
            voladura = if (voladura.isNotBlank()) voladura else current.voladura
        )
    }

    private suspend fun ensureDraftCreated(): Long {
        val current = _uiState.value
        if (current.draftId != null) return current.draftId

        val newId = db.despachoDao().insertDespacho(
            DespachoEntity(
                vale = current.vale,
                empresa = current.empresa,
                lugar = current.lugar,
                transportista = current.transportista,
                turno = current.turno,
                voladura = current.voladura,
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
                    db.despachoDao().insertSingleItem(
                        DespachoItemEntity(
                            despachoId = draftId,
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
                    val updatedMarkers = calculateMarkers(updatedList)

                    _uiState.value = _uiState.value.copy(
                        scannedItems = updatedList,
                        markers = updatedMarkers,
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
                    db.despachoDao().deleteItem(draftId, item.code)
                }
                val updated = _uiState.value.scannedItems.filterNot { it.code == item.code }
                val markers = calculateMarkers(updated)
                _uiState.value = _uiState.value.copy(
                    scannedItems = updated,
                    markers = markers,
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

    private fun calculateMarkers(items: List<BarcodeItem>): List<ProductMarker> {
        return items
            .groupBy { Triple(it.productInfo.name, it.productInfo.presentation, it.empaque) }
            .map { (key, group) ->
                ProductMarker(
                    productName = key.first,
                    presentation = key.second,
                    count = group.size,
                    type = group.firstOrNull()?.productInfo?.type ?: ProductType.DESCONOCIDO,
                    empaque = key.third
                )
            }
            .sortedWith(compareBy<ProductMarker> { it.empaque.ordinal }.thenByDescending { it.count })
    }

    fun finishAndSave(onSuccess: () -> Unit) {
        viewModelScope.launch {
            scanMutex.withLock {
                val state = _uiState.value
                val draftId = state.draftId
                val savedDespachoId: Long? = if (draftId != null) {
                    db.despachoDao().updateEstado(draftId, "FINALIZADO")
                    draftId
                } else if (state.scannedItems.isNotEmpty()) {
                    val newId = db.despachoDao().insertDespacho(
                        DespachoEntity(
                            vale = state.vale,
                            empresa = state.empresa,
                            lugar = state.lugar,
                            transportista = state.transportista,
                            turno = state.turno,
                            voladura = state.voladura,
                            fechaCreacion = DateTimeUtils.currentTimestamp(),
                            estado = "FINALIZADO"
                        )
                    )
                    val items = state.scannedItems.map {
                        DespachoItemEntity(
                            despachoId = newId,
                            codigoBarras = it.code,
                            fechaEscaneo = it.timestamp,
                            productoId = it.productInfo.id,
                            productoNombre = it.productInfo.name,
                            presentacion = it.productInfo.presentation,
                            tipo = it.productInfo.type.name
                        )
                    }
                    db.despachoDao().insertItems(items)
                    newId
                } else {
                    null
                }

                if (savedDespachoId != null && state.scannedItems.isNotEmpty()) {
                    inventoryRepository.recordDespacho(
                        despachoId = savedDespachoId,
                        vale = state.vale,
                        empresa = state.empresa,
                        lugar = state.lugar,
                        items = state.scannedItems
                    )
                }

                _uiState.value = _uiState.value.copy(draftId = null)
            }
            onSuccess()
        }
    }

    fun printTicket(onComplete: (String) -> Unit) {
        val state = _uiState.value
        val pairedPrinters = printerManager.getPairedPrinters()
        if (pairedPrinters.isEmpty()) {
            onComplete("No hay dispositivos Bluetooth vinculados en Android.")
            return
        }

        val targetDevice = printerManager.findPreferredPrinter() ?: pairedPrinters.first()
        val targetName = try { targetDevice.name ?: targetDevice.address } catch (_: Exception) { targetDevice.address }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPrinting = true)

            val record = DespachoRecord(
                vale = state.vale,
                empresa = state.empresa,
                lugar = state.lugar,
                transportista = state.transportista,
                turno = state.turno,
                voladura = state.voladura,
                items = state.scannedItems.map {
                    ScannedCodeRecord(
                        code = it.code,
                        timestamp = it.timestamp,
                        productName = it.productInfo.name,
                        presentation = it.productInfo.presentation
                    )
                }
            )

            val mineName = settingsRepository.getMineName()
            val permitNumber = settingsRepository.getPermitNumber()
            val ticketHeader = settingsRepository.getTicketHeader()
            val paperWidth = settingsRepository.getPaperWidth()
            val protocol = settingsRepository.getPrinterProtocol()

            // Check if target is Zebra (CPCL)
            val isZebra = protocol == "CPCL" || (protocol == "AUTO" && targetName.lowercase().let {
                it.contains("zebra") || it.contains("zq") || it.contains("rw") || it.contains("qln") || it.contains("zd") || it.contains("imz")
            })

            val bytes = when (protocol) {
                "CPCL" -> TicketGenerator.generateDespachoCpcl(
                    despacho = record,
                    paperWidth = paperWidth,
                    mineName = mineName,
                    permitNumber = permitNumber,
                    headerNote = ticketHeader,
                    includeSignatures = settingsRepository.getPrintSignatures(),
                    includeBreakdown = settingsRepository.getPrintBreakdown(),
                    includeCodeDetails = settingsRepository.getPrintCodeDetails()
                )
                "PLAIN" -> TicketGenerator.generateDespachoPlain(
                    record = record,
                    paperWidth = paperWidth,
                    mineName = mineName,
                    permitNumber = permitNumber,
                    headerNote = ticketHeader,
                    includeSignatures = settingsRepository.getPrintSignatures(),
                    includeBreakdown = settingsRepository.getPrintBreakdown(),
                    includeCodeDetails = settingsRepository.getPrintCodeDetails()
                )
                else -> {
                    if (isZebra && protocol != "ESCPOS") {
                        TicketGenerator.generateDespachoCpcl(
                            despacho = record,
                            paperWidth = paperWidth,
                            mineName = mineName,
                            permitNumber = permitNumber,
                            headerNote = ticketHeader,
                            includeSignatures = settingsRepository.getPrintSignatures(),
                            includeBreakdown = settingsRepository.getPrintBreakdown(),
                            includeCodeDetails = settingsRepository.getPrintCodeDetails()
                        )
                    } else {
                        TicketGenerator.generateDespachoEscPos(
                            record = record,
                            paperWidth = paperWidth,
                            mineName = mineName,
                            permitNumber = permitNumber,
                            headerNote = ticketHeader,
                            includeSignatures = settingsRepository.getPrintSignatures(),
                            includeBreakdown = settingsRepository.getPrintBreakdown(),
                            includeCodeDetails = settingsRepository.getPrintCodeDetails()
                        )
                    }
                }
            }

            val result = printerManager.printBytes(targetDevice.address, bytes)

            _uiState.value = _uiState.value.copy(
                isPrinting = false,
                printStatusMessage = if (result.isSuccess) "Ticket impreso en $targetName" else "Error en $targetName: ${result.exceptionOrNull()?.localizedMessage}"
            )

            if (result.isSuccess) {
                onComplete("Impresión exitosa en $targetName")
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Fallo al conectar"
                onComplete("Error en $targetName: $err")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundFeedback.release()
    }
}
