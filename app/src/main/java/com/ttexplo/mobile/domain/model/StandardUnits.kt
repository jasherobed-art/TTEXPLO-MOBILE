package com.ttexplo.mobile.domain.model

/** Unidades estándar del catálogo: kilos, metros y piezas. */
object StandardUnits {
    const val KG = "KG"
    const val MTS = "MTS"
    const val PZA = "PZA"

    private val METERS = setOf("MTS", "MT", "M", "METRO", "METROS", "ML")
    private val KILOS = setOf("KG", "KGS", "KILO", "KILOS", "KILOGRAMO", "KILOGRAMOS")
    private val PIECES = setOf("PZA", "PZAS", "PIEZA", "PIEZAS", "UNIDAD", "UNIDADES", "UN", "U")
    private val BY_METER = listOf("CORDON", "PRIMACORD", "DETACORD", "MECHA", "GUIA", "CABLE")

    /** Lleva cualquier variante ("KGS", "METRO", "PIEZA"…) a KG, MTS o PZA; si no la reconoce, la infiere. */
    fun normalize(raw: String?, tipo: String, id: String, producto: String): String {
        // Alto explosivo y agente siempre se manejan en kilos.
        if (tipo == "ALTO_EXPLOSIVO" || tipo == "AGENTE") return KG
        val unit = raw?.trim()?.uppercase()?.removeSuffix(".").orEmpty()
        when {
            unit in KILOS -> return KG
            unit in METERS -> return MTS
            unit in PIECES -> return PZA
        }
        val isAccessory = tipo == "ACCESORIO" ||
            (tipo != "ALTO_EXPLOSIVO" && tipo != "AGENTE" && id.startsWith("X", ignoreCase = true))
        if (!isAccessory) return KG
        val name = producto.uppercase()
        return if (BY_METER.any { name.contains(it) }) MTS else PZA
    }
}
