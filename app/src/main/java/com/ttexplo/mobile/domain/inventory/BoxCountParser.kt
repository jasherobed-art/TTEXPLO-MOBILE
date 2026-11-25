package com.ttexplo.mobile.domain.inventory

/** Parsea `CAJ[30 dígitos],KP,piezas,fechaHora` y calcula el rango de referencia. */
object BoxCountParser {
    fun parseCodeOnly(raw: String, boxWeightKg: Double = 25.0): BoxCountRecord? {
        val code = raw.trim().removePrefix("CAJ")
        if (code.length != 30 || !code.all { it.isDigit() }) return null
        val initial = code.substring(9, 15).toLongOrNull() ?: return null
        val finalCode = code.substring(15, 21).toLongOrNull() ?: return null
        val range = (finalCode - initial).toInt()
        if (range <= 0 || range > BoxUnitWeightCalculator.MAX_CARTRIDGES_PER_BOX) return null
        return BoxCountRecord(
            code = code,
            kp = "",
            pieces = range,
            timestamp = "",
            rangeCount = range,
            unitWeightKg = BoxUnitWeightCalculator.calculateUnitWeightKg(boxWeightKg, range) ?: return null
        )
    }

    fun parse(raw: String, boxWeightKg: Double = 25.0): BoxCountRecord? {
        val parts = raw.trim().removePrefix("CAJ").split(",")
        if (parts.size != 4) return null

        val code = parts[0].trim()
        val kp = parts[1].trim()
        val pieces = parts[2].trim().toIntOrNull() ?: return null
        val timestamp = parts[3].trim()
        if (code.length != 30 || !code.all { it.isDigit() }) return null
        if (kp.isEmpty() || pieces !in 1..BoxUnitWeightCalculator.MAX_CARTRIDGES_PER_BOX) return null
        if (timestamp.isEmpty()) return null

        val initial = code.substring(9, 15).toLongOrNull() ?: return null
        val finalCode = code.substring(15, 21).toLongOrNull() ?: return null
        val range = (finalCode - initial).toInt()
        if (range <= 0 || range > BoxUnitWeightCalculator.MAX_CARTRIDGES_PER_BOX) return null

        return BoxCountRecord(
            code = code,
            kp = kp,
            pieces = pieces,
            timestamp = timestamp,
            rangeCount = range,
            unitWeightKg = BoxUnitWeightCalculator.calculateUnitWeightKg(boxWeightKg, pieces) ?: return null
        )
    }
}
