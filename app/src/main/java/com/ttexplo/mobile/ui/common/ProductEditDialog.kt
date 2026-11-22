package com.ttexplo.mobile.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttexplo.mobile.data.local.entity.ProductoEntity
import com.ttexplo.mobile.domain.inventory.BoxUnitWeightCalculator
import com.ttexplo.mobile.domain.model.InventoryCategory
import com.ttexplo.mobile.domain.model.ProductClassifier
import com.ttexplo.mobile.domain.model.ProductType
import com.ttexplo.mobile.ui.theme.*

@Composable
fun ProductEditDialog(
    initialId: String? = null,
    initial: ProductoEntity? = null,
    onDismiss: () -> Unit,
    onSave: (ProductoEntity) -> Unit
) {
    var id by remember { mutableStateOf(initial?.idProducto ?: initialId ?: "") }
    var nombre by remember { mutableStateOf(initial?.producto ?: "") }
    var presentacion by remember { mutableStateOf(initial?.presentacion ?: "") }
    var noSap by remember { mutableStateOf(initial?.noSap ?: "") }
    var selectedType by remember {
        mutableStateOf(
            when (initial?.tipo) {
                ProductType.AGENTE.name -> ProductType.AGENTE
                ProductType.ACCESORIO.name -> ProductType.ACCESORIO
                else -> if ((initial?.idProducto ?: initialId ?: "").startsWith("X", ignoreCase = true)) {
                    ProductType.ACCESORIO
                } else {
                    ProductType.ALTO_EXPLOSIVO
                }
            }
        )
    }
    var selectedCategory by remember {
        mutableStateOf(
            initial?.resolvedCategory() ?: InventoryCategory.inferCategory(
                productType = selectedType,
                codeOrId = initialId.orEmpty(),
                productName = "",
                unit = ""
            )
        )
    }
    var categoryManuallyChanged by remember { mutableStateOf(initial != null) }

    var unidad by remember {
        mutableStateOf(
            initial?.unidad?.ifBlank { selectedCategory.defaultUnit }
                ?: selectedCategory.defaultUnit
        )
    }
    var pesoText by remember {
        mutableStateOf(
            initial?.peso?.takeIf { it > 0.0 }?.toString()
                ?: if (selectedType == ProductType.ACCESORIO) "0" else "25.0"
        )
    }
    var cartuchosText by remember {
        mutableStateOf(initial?.cartuchosPorCaja?.takeIf { it > 0 }?.toString() ?: "")
    }
    var pesoUnitarioText by remember {
        mutableStateOf(
            initial?.pesoUnitarioKg?.takeIf { it > 0.0 }?.let {
                BoxUnitWeightCalculator.formatUnitWeight3(it)
            } ?: ""
        )
    }

    val isAccessory = selectedType == ProductType.ACCESORIO
    val idValid = if (isAccessory) {
        id.trim().startsWith("X", ignoreCase = true) && id.trim().length >= 2
    } else {
        id.trim().length == 3
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial == null) "Agregar Producto a Catálogo" else "Editar Producto",
                color = MineTextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tipo de material
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedType == ProductType.ALTO_EXPLOSIVO,
                        onClick = {
                            selectedType = ProductType.ALTO_EXPLOSIVO
                            selectedCategory = InventoryCategory.ALTO_EXPLOSIVO
                            unidad = "KG"
                            if (pesoText == "0" || pesoText.isBlank()) pesoText = "25.0"
                        },
                        label = { Text("Alto Exp.", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedType == ProductType.AGENTE,
                        onClick = {
                            selectedType = ProductType.AGENTE
                            selectedCategory = InventoryCategory.AGENTE_EXPLOSIVO
                            unidad = "KG"
                            if (pesoText == "0" || pesoText.isBlank()) pesoText = "25.0"
                        },
                        label = { Text("Agente", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedType == ProductType.ACCESORIO,
                        onClick = {
                            selectedType = ProductType.ACCESORIO
                            val inferred = InventoryCategory.inferCategory(ProductType.ACCESORIO, id, nombre, "")
                            selectedCategory = inferred
                            unidad = inferred.defaultUnit
                            pesoText = "0"
                        },
                        label = { Text("Accesorio", fontSize = 11.sp) }
                    )
                }

                // Sub-selector de las 3 familias de Accesorios
                if (isAccessory) {
                    Text(
                        text = "Familia de Accesorio:",
                        color = MineTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        InventoryCategory.accessoryCategories.forEach { accCat ->
                            FilterChip(
                                selected = selectedCategory == accCat,
                                onClick = {
                                    selectedCategory = accCat
                                    categoryManuallyChanged = true
                                    unidad = accCat.defaultUnit
                                },
                                modifier = Modifier.weight(1f),
                                label = {
                                    AutoShrinkText(
                                        text = accCat.shortLabel,
                                        initialTextSize = 11.sp,
                                        minTextSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedCategory == accCat) MineDarkBackground else MineTextPrimary
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when (accCat) {
                                        InventoryCategory.CORDON_DETONANTE -> MineAgentCyan
                                        InventoryCategory.CANUELAS -> MineSafetyYellow
                                        else -> EmpaqueAccesorioColor
                                    },
                                    containerColor = MineDarkBackground
                                )
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = id,
                    onValueChange = { value ->
                        if (isAccessory) {
                            id = value.uppercase().take(24)
                            if (!categoryManuallyChanged) {
                                val inferred = InventoryCategory.inferCategory(ProductType.ACCESORIO, id, nombre, "")
                                selectedCategory = inferred
                                unidad = inferred.defaultUnit
                            }
                        } else if (value.length <= 3) {
                            id = value
                        }
                    },
                    label = { Text(if (isAccessory) "Código Accesorio (inicia con X)" else "ID Prefijo (3 dígitos)") },
                    enabled = initial == null && initialId == null,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MineSafetyYellow,
                        focusedTextColor = MineTextPrimary,
                        unfocusedTextColor = MineTextPrimary
                    )
                )

                OutlinedTextField(
                    value = nombre,
                    onValueChange = {
                        nombre = it
                        if (!isAccessory) {
                            selectedType = ProductClassifier.inferType(it)
                            selectedCategory = if (selectedType == ProductType.AGENTE) {
                                InventoryCategory.AGENTE_EXPLOSIVO
                            } else {
                                InventoryCategory.ALTO_EXPLOSIVO
                            }
                        } else if (!categoryManuallyChanged) {
                            val inferred = InventoryCategory.inferCategory(ProductType.ACCESORIO, id, it, "")
                            selectedCategory = inferred
                            unidad = inferred.defaultUnit
                        }
                    },
                    label = { Text(if (isAccessory) "Nombre (ej. Cordón 5g / Cañuela / Detonador)" else "Nombre del Producto (ej. EMULEX 1)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MineSafetyYellow,
                        focusedTextColor = MineTextPrimary,
                        unfocusedTextColor = MineTextPrimary
                    )
                )

                OutlinedTextField(
                    value = presentacion,
                    onValueChange = { presentacion = it },
                    label = { Text(if (isAccessory) "Presentación (ej. Pieza / Rollo 500m)" else "Medida / Presentación (ej. 1 1/4\" X 16\")") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MineSafetyYellow,
                        focusedTextColor = MineTextPrimary,
                        unfocusedTextColor = MineTextPrimary
                    )
                )

                if (isAccessory) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Unidad:", color = MineTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        FilterChip(
                            selected = unidad == "PZA",
                            onClick = { unidad = "PZA" },
                            label = { Text("PZA (Piezas)", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = unidad == "MTS",
                            onClick = { unidad = "MTS" },
                            label = { Text("MTS (Metros)", fontSize = 11.sp) }
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = pesoText,
                            onValueChange = { input ->
                                pesoText = input
                                val p = input.toDoubleOrNull() ?: 0.0
                                val c = cartuchosText.toIntOrNull() ?: 0
                                if (p > 0.0 && c > 0) {
                                    BoxUnitWeightCalculator.calculateUnitWeightKg(p, c)?.let { u ->
                                        pesoUnitarioText = BoxUnitWeightCalculator.formatUnitWeight3(u)
                                    }
                                }
                            },
                            label = { Text(if (selectedType == ProductType.AGENTE) "Peso Saco (kg)" else "Peso Caja (kg)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MineSafetyYellow,
                                focusedTextColor = MineTextPrimary,
                                unfocusedTextColor = MineTextPrimary
                            )
                        )
                        if (selectedType == ProductType.ALTO_EXPLOSIVO) {
                            OutlinedTextField(
                                value = cartuchosText,
                                onValueChange = { input ->
                                    if (input.all { it.isDigit() } && input.length <= 3) {
                                        cartuchosText = input
                                        val p = pesoText.toDoubleOrNull() ?: 0.0
                                        val c = input.toIntOrNull() ?: 0
                                        if (p > 0.0 && c > 0) {
                                            BoxUnitWeightCalculator.calculateUnitWeightKg(p, c)?.let { u ->
                                                pesoUnitarioText = BoxUnitWeightCalculator.formatUnitWeight3(u)
                                            }
                                        }
                                    }
                                },
                                label = { Text("Cart./Caja") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MineSafetyYellow,
                                    focusedTextColor = MineTextPrimary,
                                    unfocusedTextColor = MineTextPrimary
                                )
                            )
                        }
                    }

                    if (selectedType == ProductType.ALTO_EXPLOSIVO) {
                        OutlinedTextField(
                            value = pesoUnitarioText,
                            onValueChange = { pesoUnitarioText = it },
                            label = { Text("Peso Unitario / Cartucho (kg) — auto al escanear caja") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MineSafetyYellow,
                                focusedTextColor = MineTextPrimary,
                                unfocusedTextColor = MineTextPrimary
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = noSap,
                    onValueChange = { noSap = it },
                    label = { Text("No. SAP (opcional)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MineSafetyYellow,
                        focusedTextColor = MineTextPrimary,
                        unfocusedTextColor = MineTextPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                enabled = idValid && nombre.isNotBlank(),
                onClick = {
                    val cleanId = id.trim().uppercase()
                    val parsedPeso = if (isAccessory) 0.0 else (pesoText.toDoubleOrNull() ?: 25.0)
                    val parsedCarts = if (selectedType == ProductType.ALTO_EXPLOSIVO) {
                        cartuchosText.toIntOrNull() ?: 0
                    } else {
                        0
                    }
                    val parsedUnitWeight = if (selectedType == ProductType.ALTO_EXPLOSIVO) {
                        pesoUnitarioText.toDoubleOrNull()
                            ?: if (parsedPeso > 0.0 && parsedCarts > 0) {
                                BoxUnitWeightCalculator.calculateUnitWeightKg(parsedPeso, parsedCarts) ?: 0.0
                            } else {
                                0.0
                            }
                    } else {
                        0.0
                    }
                    val finalCategory = when (selectedType) {
                        ProductType.ALTO_EXPLOSIVO -> InventoryCategory.ALTO_EXPLOSIVO
                        ProductType.AGENTE -> InventoryCategory.AGENTE_EXPLOSIVO
                        else -> if (selectedCategory in InventoryCategory.accessoryCategories) {
                            selectedCategory
                        } else {
                            InventoryCategory.inferCategory(ProductType.ACCESORIO, cleanId, nombre, unidad)
                        }
                    }

                    val entity = ProductoEntity(
                        idProducto = cleanId,
                        producto = nombre.trim(),
                        presentacion = presentacion.trim(),
                        noSap = noSap.trim(),
                        unidad = unidad.trim().uppercase(),
                        cantidad = initial?.cantidad ?: 0.0,
                        codBarras = cleanId,
                        peso = parsedPeso,
                        pg = initial?.pg ?: "",
                        pu = initial?.pu ?: 0.0,
                        tipo = selectedType.name,
                        pesoUnitarioKg = parsedUnitWeight,
                        cartuchosPorCaja = parsedCarts,
                        categoriaInventario = finalCategory.name
                    )
                    onSave(entity)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MineSafetyYellow, contentColor = MineDarkBackground)
            ) {
                Text("Guardar en Catálogo", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = MineTextMuted)
            }
        },
        containerColor = MineDarkSurface
    )
}
