package com.ttexplo.mobile.domain.inventory

/** Vale de conteo de cartuchos: un renglón por caja contada, ya editado por el operador. */
data class BoxCountItem(
    val code: String,
    val descripcion: String,
    val kp: String,
    val piezas: Int,
    val fechaHora: String
)

data class BoxCountVale(
    val folio: String,
    val items: List<BoxCountItem>
) {
    val totalPiezas: Int get() = items.sumOf { it.piezas }
}

object BoxCountFolio {
    fun build(fechaCompacta: String, consecutivo: Int): String =
        "CC-$fechaCompacta-${consecutivo.toString().padStart(3, '0')}"
}
