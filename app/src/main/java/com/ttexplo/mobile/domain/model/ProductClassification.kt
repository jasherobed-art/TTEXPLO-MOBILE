package com.ttexplo.mobile.domain.model

enum class ProductType(val label: String) {
    ALTO_EXPLOSIVO("Alto Explosivo"),
    AGENTE("Agente de Voladura"),
    ACCESORIO("Accesorio"),
    DESCONOCIDO("Sin catalogar")
}

/**
 * Las 5 familias regulatorias SEDENA que se controlan de forma estrictamente separada
 * en el inventario de polvorín.
 */
enum class InventoryCategory(
    val label: String,
    val shortLabel: String,
    val defaultUnit: String,
    val supportsBarcodeLedger: Boolean
) {
    ALTO_EXPLOSIVO("Alto Explosivo", "Alto Exp.", "KG", true),
    AGENTE_EXPLOSIVO("Agente Explosivo", "Agente", "KG", true),
    CORDON_DETONANTE("Cordón Detonante", "Cordón", "MTS", false),
    CANUELAS("Cañuelas", "Cañuelas", "PZA", false),
    DETONADORES("Detonadores", "Detonadores", "PZA", false);

    fun toProductType(): ProductType = when (this) {
        ALTO_EXPLOSIVO -> ProductType.ALTO_EXPLOSIVO
        AGENTE_EXPLOSIVO -> ProductType.AGENTE
        CORDON_DETONANTE, CANUELAS, DETONADORES -> ProductType.ACCESORIO
    }

    companion object {
        val accessoryCategories: List<InventoryCategory> = listOf(
            CORDON_DETONANTE,
            CANUELAS,
            DETONADORES
        )

        fun fromStringOrNull(raw: String?): InventoryCategory? {
            if (raw.isNullOrBlank()) return null
            val normalized = raw.trim().uppercase()
            return entries.find { it.name == normalized }
        }

        /**
         * Clasifica de forma determinista un producto dentro de una de las 5 familias SEDENA.
         * Para códigos de accesorio `X...` nuevos, sugiere la familia y unidad por defecto
         * a partir del prefijo/nombre/unidad, permitiendo al operador cambiarla libremente.
         */
        fun inferCategory(
            productType: ProductType,
            codeOrId: String = "",
            productName: String = "",
            unit: String = ""
        ): InventoryCategory {
            val cleanCode = codeOrId.trim().uppercase()
            val cleanUnit = unit.trim().uppercase()

            if (productType == ProductType.AGENTE) return AGENTE_EXPLOSIVO
            if (productType == ProductType.ALTO_EXPLOSIVO) return ALTO_EXPLOSIVO

            val isAccessory = productType == ProductType.ACCESORIO || cleanCode.startsWith("X")
            if (isAccessory) {
                val normalized = "$cleanCode ${productName.trim().uppercase()}"
                    .replace('Á', 'A')
                    .replace('É', 'E')
                    .replace('Í', 'I')
                    .replace('Ó', 'O')
                    .replace('Ú', 'U')
                    .replace('Ñ', 'N')

                if (
                    normalized.contains("XCOR") ||
                    normalized.contains("CORDON") ||
                    normalized.contains("PENT") ||
                    normalized.contains("PRIMACORD") ||
                    normalized.contains("DETACORD")
                ) {
                    return CORDON_DETONANTE
                }

                if (
                    normalized.contains("XCAN") ||
                    normalized.contains("CANUELA") ||
                    normalized.contains("XMECH") ||
                    normalized.contains("MECHA") ||
                    normalized.contains("GUIA") ||
                    normalized.contains("CARMEX") ||
                    normalized.contains("XIGN") ||
                    normalized.contains("IGNIT")
                ) {
                    return CANUELAS
                }

                if (
                    normalized.contains("XDET") ||
                    normalized.contains("DETON") ||
                    normalized.contains("NONEL") ||
                    normalized.contains("NOEL") ||
                    normalized.contains("XFUL") ||
                    normalized.contains("FULM") ||
                    normalized.contains("INIC") ||
                    normalized.contains("ELEC") ||
                    normalized.contains("XBOO") ||
                    normalized.contains("BOOSTER")
                ) {
                    return DETONADORES
                }

                if (cleanUnit == "MTS") {
                    return CORDON_DETONANTE
                }

                return DETONADORES
            }

            return if (ProductClassifier.inferType(productName) == ProductType.AGENTE) {
                AGENTE_EXPLOSIVO
            } else {
                ALTO_EXPLOSIVO
            }
        }
    }
}

data class ProductInfo(
    val id: String,
    val name: String,
    val presentation: String,
    val noSap: String,
    val unit: String,
    val weightKg: Double,
    val type: ProductType,
    val pg: String,
    val unitWeightKg: Double = 0.0,
    val cartridgesPerBox: Int = 0,
    val inventoryCategory: InventoryCategory = InventoryCategory.inferCategory(type, id, name, unit)
)

object ProductClassifier {

    private val AGENT_KEYWORDS = listOf(
        "ANFO", "AMEX", "AUSTINITE", "MEXAMON", "NITRATO", "NITRO", "ANFOMIN", "ANFOOVIEDO", "ANFOREY"
    )

    fun inferType(productName: String): ProductType {
        val upper = productName.uppercase()
        for (kw in AGENT_KEYWORDS) {
            if (upper.contains(kw)) {
                return ProductType.AGENTE
            }
        }
        return ProductType.ALTO_EXPLOSIVO
    }

    fun extractPrefix(barcode: String): String {
        val trimmed = barcode.trim()
        return when {
            trimmed.startsWith("X", ignoreCase = true) -> "X"
            trimmed.length >= 3 -> trimmed.substring(0, 3)
            else -> trimmed
        }
    }
}
