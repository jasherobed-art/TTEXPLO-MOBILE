package com.ttexplo.mobile.ui.screens.inventory

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ttexplo.mobile.TTExploApp
import com.ttexplo.mobile.data.local.entity.ProductoEntity
import com.ttexplo.mobile.data.repository.CatalogRepositoryImpl
import com.ttexplo.mobile.data.repository.SettingsRepository
import com.ttexplo.mobile.domain.inventory.BoxUnitWeightCalculator
import com.ttexplo.mobile.domain.inventory.InventoryCodeItem
import com.ttexplo.mobile.domain.inventory.InventoryDashboardTotals
import com.ttexplo.mobile.domain.inventory.InventoryStockItem
import com.ttexplo.mobile.domain.inventory.KardexEntry
import com.ttexplo.mobile.domain.inventory.QuickStockMode
import com.ttexplo.mobile.domain.model.InventoryCategory
import com.ttexplo.mobile.domain.model.ProductInfo
import com.ttexplo.mobile.domain.model.ProductType
import com.ttexplo.mobile.domain.scanner.ScanValidator
import com.ttexplo.mobile.domain.scanner.TipoEmpaque
import com.ttexplo.mobile.license.LicenseManager
import com.ttexplo.mobile.ui.common.SoundFeedbackManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class InventoryFilter(val label: String) {
    ALL("Todas"),
    ALTO_EXPLOSIVO("Alto Exp."),
    AGENTE_EXPLOSIVO("Agente"),
    CORDON_DETONANTE("Cordón"),
    CANUELAS("Cañuelas"),
    DETONADORES("Detonadores"),
    IN_STOCK("Con Stock")
}

data class SampleScanFeedback(
    val isSuccess: Boolean,
    val message: String,
    val resolvedProduct: ProductInfo? = null,
    val detectedEmpaque: TipoEmpaque = TipoEmpaque.DESCONOCIDO,
    val detectedCartridges: Int? = null,
    val calculatedUnitWeightKg: Double? = null
)

data class InventoryUiState(
    val polvorinName: String = "POLVORÍN PRINCIPAL",
    val deviceId: String = "",
    val totals: InventoryDashboardTotals = InventoryDashboardTotals(),
    val allItems: List<InventoryStockItem> = emptyList(),
    val filteredItems: List<InventoryStockItem> = emptyList(),
    val catalogProducts: List<ProductoEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: InventoryFilter = InventoryFilter.ALL,
    val statusBanner: String? = null,
    val activeConflictCount: Int = 0,
    val lastSyncTimestampIso: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TTExploApp
    private val db = app.database
    private val inventoryRepository = app.inventoryRepository
    private val catalogRepository = CatalogRepositoryImpl(db.productoDao())
    private val scanValidator = ScanValidator(catalogRepository)
    private val settingsRepository = SettingsRepository(application)
    private val soundFeedback = SoundFeedbackManager(application)

    private val _searchQuery = MutableStateFlow("")
    private val _selectedFilter = MutableStateFlow(InventoryFilter.ALL)
    private val _statusBanner = MutableStateFlow<String?>(null)

    private val _selectedKardexProductId = MutableStateFlow<String?>(null)
    val selectedKardexProductId: StateFlow<String?> = _selectedKardexProductId.asStateFlow()

    private val _selectedKardexInitialTab = MutableStateFlow(0)
    val selectedKardexInitialTab: StateFlow<Int> = _selectedKardexInitialTab.asStateFlow()

    val selectedKardexEntries: StateFlow<List<KardexEntry>> = _selectedKardexProductId
        .flatMapLatest { prodId ->
            if (prodId.isNullOrBlank()) flowOf(emptyList())
            else inventoryRepository.observeProductKardex(prodId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedProductCodes: StateFlow<List<InventoryCodeItem>> = _selectedKardexProductId
        .flatMapLatest { prodId ->
            if (prodId.isNullOrBlank()) flowOf(emptyList())
            else inventoryRepository.observeProductCodes(prodId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sampleScanFeedback = MutableStateFlow<SampleScanFeedback?>(null)
    val sampleScanFeedback: StateFlow<SampleScanFeedback?> = _sampleScanFeedback.asStateFlow()

    private val syncPrefs = application.getSharedPreferences("ttexplo_sync_prefs", Context.MODE_PRIVATE)

    private val syncSummaryFlow = combine(
        inventoryRepository.observeActiveConflicts(),
        inventoryRepository.observeSyncSessions()
    ) { conflicts, sessions ->
        val reviewed = syncPrefs.getStringSet("reviewed_conflict_ids", emptySet()) ?: emptySet()
        val unreviewedCount = conflicts.count { it.conflictId !in reviewed }
        unreviewedCount to sessions.firstOrNull()?.timestampIso
    }

    val uiState: StateFlow<InventoryUiState> = combine(
        inventoryRepository.observeInventoryOverview(),
        db.productoDao().getAllFlow(),
        _searchQuery,
        _selectedFilter,
        combine(_statusBanner, syncSummaryFlow) { b, s -> b to s }
    ) { (totals, items), catalog, query, filter, (banner, syncSummary) ->
        val cleanQuery = query.trim().lowercase()
        val filtered = items.filter { item ->
            val matchesFilter = when (filter) {
                InventoryFilter.ALL -> true
                InventoryFilter.ALTO_EXPLOSIVO -> item.category == InventoryCategory.ALTO_EXPLOSIVO
                InventoryFilter.AGENTE_EXPLOSIVO -> item.category == InventoryCategory.AGENTE_EXPLOSIVO
                InventoryFilter.CORDON_DETONANTE -> item.category == InventoryCategory.CORDON_DETONANTE
                InventoryFilter.CANUELAS -> item.category == InventoryCategory.CANUELAS
                InventoryFilter.DETONADORES -> item.category == InventoryCategory.DETONADORES
                InventoryFilter.IN_STOCK -> abs(item.stockQuantity) > 1e-6 || item.codesInPolvorinCount > 0
            }
            val matchesQuery = cleanQuery.isEmpty() ||
                item.productId.lowercase().contains(cleanQuery) ||
                item.productName.lowercase().contains(cleanQuery) ||
                item.presentation.lowercase().contains(cleanQuery) ||
                item.category.label.lowercase().contains(cleanQuery)
            matchesFilter && matchesQuery
        }

        InventoryUiState(
            polvorinName = settingsRepository.getTicketHeader().ifBlank { "POLVORÍN PRINCIPAL" },
            deviceId = LicenseManager.getFormattedDeviceId(app),
            totals = totals,
            allItems = items,
            filteredItems = filtered,
            catalogProducts = catalog,
            searchQuery = query,
            selectedFilter = filter,
            statusBanner = banner,
            activeConflictCount = syncSummary.first,
            lastSyncTimestampIso = syncSummary.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InventoryUiState(
            polvorinName = settingsRepository.getTicketHeader().ifBlank { "POLVORÍN PRINCIPAL" },
            deviceId = LicenseManager.getFormattedDeviceId(app)
        )
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectFilter(filter: InventoryFilter) {
        _selectedFilter.value = filter
    }

    fun toggleCategoryFilter(filter: InventoryFilter) {
        _selectedFilter.value = if (_selectedFilter.value == filter) InventoryFilter.ALL else filter
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun openKardex(productId: String, initialTab: Int = 0) {
        _selectedKardexInitialTab.value = initialTab
        _selectedKardexProductId.value = productId
    }

    fun closeKardex() {
        _selectedKardexProductId.value = null
        _selectedKardexInitialTab.value = 0
    }

    fun clearSampleScanFeedback() {
        _sampleScanFeedback.value = null
    }

    /**
     * Escanea 1 código de muestra (Caja 30d, Saco 18d, Cartucho 16d o Accesorio X...)
     * para pre-seleccionar el producto y sugerir su familia en Carga Rápida, autocalibrando
     * el peso unitario si es una Caja de 30 dígitos.
     */
    fun processSampleBarcode(rawCode: String, onProductResolved: (ProductInfo, TipoEmpaque) -> Unit) {
        val trimmed = rawCode.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            val result = scanValidator.processScan(trimmed, emptySet(), allowAccessories = true)
            if (!result.isValid) {
                soundFeedback.playError()
                _sampleScanFeedback.value = SampleScanFeedback(
                    isSuccess = false,
                    message = result.message
                )
                return@launch
            }

            soundFeedback.playSuccess()
            val catLabel = result.productInfo.inventoryCategory.label
            val detailMsg = if (result.detectedCartridgesPerBox != null && result.calculatedUnitWeightKg != null) {
                val uFormatted = BoxUnitWeightCalculator.formatUnitWeight3(result.calculatedUnitWeightKg)
                "Muestra reconocida ($catLabel): ${result.productInfo.name} · Auto-calibrado ${result.detectedCartridgesPerBox} cart./caja (${uFormatted} kg/cart.)"
            } else {
                "Muestra reconocida ($catLabel): ${result.productInfo.name} (${result.empaque.label})"
            }

            _sampleScanFeedback.value = SampleScanFeedback(
                isSuccess = true,
                message = detailMsg,
                resolvedProduct = result.productInfo,
                detectedEmpaque = result.empaque,
                detectedCartridges = result.detectedCartridgesPerBox,
                calculatedUnitWeightKg = result.calculatedUnitWeightKg
            )
            onProductResolved(result.productInfo, result.empaque)
        }
    }

    fun submitQuickPhysicalCount(
        productId: String,
        productName: String,
        presentation: String,
        productType: ProductType,
        inventoryCategory: InventoryCategory,
        unit: String,
        mode: QuickStockMode,
        targetQuantity: Double,
        cajasCount: Int,
        sacosCount: Int,
        cartuchosCount: Int,
        boxWeightKg: Double,
        unitWeightKg: Double,
        cartridgesPerBox: Int,
        notes: String,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val event = inventoryRepository.recordQuickPhysicalCount(
                productId = productId,
                productName = productName,
                presentation = presentation,
                productType = productType,
                unit = unit,
                mode = mode,
                targetQuantity = targetQuantity,
                inventoryCategory = inventoryCategory,
                cajasCount = cajasCount,
                sacosCount = sacosCount,
                cartuchosCount = cartuchosCount,
                boxWeightKg = boxWeightKg,
                unitWeightKg = unitWeightKg,
                cartridgesPerBox = cartridgesPerBox,
                notes = notes
            )
            if (event != null) {
                soundFeedback.playSuccess()
                val signStr = if (event.deltaQuantity >= 0) "+" else ""
                val qtyFormatted = if (event.unit == "PZA") {
                    "${BoxUnitWeightCalculator.formatPieces(event.deltaQuantity)} PZA"
                } else {
                    "${BoxUnitWeightCalculator.formatDecimal2(event.deltaQuantity)} ${event.unit}"
                }
                _statusBanner.value = "${inventoryCategory.shortLabel} · UUID ${event.eventUuid.take(8)}: ${event.productName} ($signStr$qtyFormatted)"
            } else {
                _statusBanner.value = "Sin diferencia física que registrar para $productName."
            }
            onDone()
        }
    }

    fun buildSyncJsonBundle(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val json = inventoryRepository.exportEventsAsSyncJson()
            onReady(json)
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundFeedback.release()
    }
}
