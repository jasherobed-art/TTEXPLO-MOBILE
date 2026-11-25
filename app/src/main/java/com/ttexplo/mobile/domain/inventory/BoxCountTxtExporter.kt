package com.ttexplo.mobile.domain.inventory

/** Exporta el conteo en el formato que usa el operador: CAJ, líneas y blanco final. */
object BoxCountTxtExporter {
    fun render(records: List<BoxCountRecord>): String = buildString {
        append("CAJ\n")
        records.forEach { record ->
            append(record.code).append(',')
                .append(record.kp).append(',')
                .append(record.pieces).append(',')
                .append(record.timestamp).append('\n')
        }
        append('\n')
    }

    fun fileName(date: String): String = "ttexplo$date.txt"
}
