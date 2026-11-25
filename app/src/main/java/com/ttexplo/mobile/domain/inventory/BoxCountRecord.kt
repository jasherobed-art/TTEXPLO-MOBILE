package com.ttexplo.mobile.domain.inventory

/** Conteo físico de una caja: el operador teclea KP y piezas reales. */
data class BoxCountRecord(
    val code: String,
    val kp: String,
    val pieces: Int,
    val timestamp: String,
    val rangeCount: Int,
    val unitWeightKg: Double
)
