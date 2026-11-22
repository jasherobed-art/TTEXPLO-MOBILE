package com.ttexplo.mobile.domain.model

data class EntradaRecord(
    val proveedor: String,
    val polvorin: String,
    val factura: String,
    val items: List<ScannedCodeRecord>
)

data class DespachoRecord(
    val vale: String,
    val empresa: String,
    val lugar: String,
    val transportista: String,
    val turno: String,
    val voladura: String,
    val items: List<ScannedCodeRecord>
)

data class DevolucionRecord(
    val empleadoEntrega: String,
    val empleadoRecibe: String,
    val items: List<ScannedCodeRecord>
)

data class AccesoriosRecord(
    val vale: String,
    val empresa: String,
    val lugar: String,
    val transportista: String,
    val turno: String,
    val recibio: String,
    val entrego: String,
    val voladura: String,
    val items: List<AccessoryItemRecord>
)

data class ScannedCodeRecord(
    val code: String,
    val timestamp: String,
    val productName: String = "",
    val presentation: String = ""
)

data class AccessoryItemRecord(
    val code: String,
    val quantity: Int,
    val timestamp: String
)
