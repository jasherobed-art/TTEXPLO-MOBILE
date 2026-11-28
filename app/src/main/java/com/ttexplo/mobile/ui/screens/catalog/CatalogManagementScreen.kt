package com.ttexplo.mobile.ui.screens.catalog

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttexplo.mobile.TTExploApp
import com.ttexplo.mobile.data.local.entity.ProductoEntity
import com.ttexplo.mobile.domain.model.ProductClassifier
import com.ttexplo.mobile.domain.model.ProductType
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.common.ProductEditDialog
import com.ttexplo.mobile.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogManagementScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = (context.applicationContext as TTExploApp).database.productoDao()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var productList by remember { mutableStateOf<List<ProductoEntity>>(emptyList()) }
    var showDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductoEntity?>(null) }

    fun refresh() {
        scope.launch {
            val base = if (searchQuery.isBlank()) dao.getAll() else dao.search(searchQuery.trim())
            productList = base.filter { p ->
                val isAccesorio = p.tipo == ProductType.ACCESORIO.name
                if (selectedTab == 1) isAccesorio else !isAccesorio
            }
        }
    }

    LaunchedEffect(searchQuery, selectedTab) {
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AutoShrinkText(
                        text = "Catálogo",
                        color = MineTextPrimary,
                        fontWeight = FontWeight.Bold,
                        targetTextSize = 18.sp,
                        minTextSize = 14.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = MineTextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editingProduct = null
                        showDialog = true
                    }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Agregar Producto", tint = MineSafetyYellow)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MineDarkBackground)
            )
        },
        containerColor = MineDarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Dos catálogos: alto/agente y accesorios/artificios
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MineDarkSurface,
                contentColor = MineSafetyYellow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Alto y Agente", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Accesorios y Artificios", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Buscar por ID o Nombre...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MineSafetyYellow) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MineSafetyYellow,
                    unfocusedBorderColor = MineBorderGray,
                    focusedTextColor = MineTextPrimary,
                    unfocusedTextColor = MineTextPrimary,
                    focusedLabelColor = MineSafetyYellow
                )
            )

            Text(
                text = if (selectedTab == 0) {
                    "ALTO EXPLOSIVO Y AGENTE: ${productList.size}"
                } else {
                    "ACCESORIOS Y ARTIFICIOS: ${productList.size}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MineTextMuted,
                fontWeight = FontWeight.Bold
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(productList, key = { it.idProducto }) { prod ->
                    ProductCardItem(
                        product = prod,
                        onEdit = {
                            editingProduct = prod
                            showDialog = true
                        },
                        onDelete = {
                            scope.launch {
                                dao.delete(prod.idProducto)
                                Toast.makeText(context, "Producto ${prod.idProducto} eliminado", Toast.LENGTH_SHORT).show()
                                refresh()
                            }
                        }
                    )
                }
            }
        }
    }

    if (showDialog) {
        ProductEditDialog(
            initial = editingProduct,
            onDismiss = { showDialog = false },
            onSave = { entity ->
                scope.launch {
                    dao.insert(entity)
                    Toast.makeText(context, "Producto ${entity.idProducto} guardado", Toast.LENGTH_SHORT).show()
                    showDialog = false
                    val target = if (entity.tipo == ProductType.ACCESORIO.name) 1 else 0
                    if (selectedTab != target) selectedTab = target else refresh()
                }
            }
        )
    }
}

@Composable
private fun ProductCardItem(
    product: ProductoEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val category = product.resolvedCategory()
    val isAccesorio = product.tipo == ProductType.ACCESORIO.name
    val badgeColor = when (category) {
        com.ttexplo.mobile.domain.model.InventoryCategory.ALTO_EXPLOSIVO -> MineHighExplosiveOrange
        com.ttexplo.mobile.domain.model.InventoryCategory.AGENTE_EXPLOSIVO -> EmpaqueSacoColor
        com.ttexplo.mobile.domain.model.InventoryCategory.CORDON_DETONANTE -> MineAgentCyan
        com.ttexplo.mobile.domain.model.InventoryCategory.CANUELAS -> MineSafetyYellow
        com.ttexplo.mobile.domain.model.InventoryCategory.DETONADORES -> EmpaqueAccesorioColor
    }
    val typeLabel = category.label

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ID: ${product.idProducto}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MineDarkBackground
                        )
                    }
                    AutoShrinkText(
                        text = product.producto,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MineTextPrimary,
                        targetTextSize = 14.sp,
                        minTextSize = 10.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                AutoShrinkText(
                    text = "Medida: ${product.presentacion.ifBlank { "-" }} • SAP: ${product.noSap.ifBlank { "-" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MineTextSecondary,
                    targetTextSize = 12.sp,
                    minTextSize = 9.sp
                )
                val weightLine = if (isAccesorio) {
                    "Tipo: $typeLabel • Unidad: ${product.unidad}"
                } else {
                    val unitDetail = if (product.pesoUnitarioKg > 0.0) {
                        val uStr = com.ttexplo.mobile.domain.inventory.BoxUnitWeightCalculator.formatUnitWeight3(product.pesoUnitarioKg)
                        val cStr = if (product.cartuchosPorCaja > 0) "${product.cartuchosPorCaja} cart. · " else ""
                        " • ${cStr}${uStr} kg/cart."
                    } else {
                        ""
                    }
                    "Tipo: $typeLabel • ${product.peso} ${product.unidad}$unitDetail"
                }
                AutoShrinkText(
                    text = weightLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MineSafetyYellow,
                    targetTextSize = 12.sp,
                    minTextSize = 9.sp
                )
            }

            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MineSafetyYellow)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MineErrorRed)
                }
            }
        }
    }
}
