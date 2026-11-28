package com.ttexplo.mobile.domain.download

import com.ttexplo.mobile.domain.scanner.DateTimeUtils
import org.json.JSONObject

/** Línea de código corregida en la sesión web. */
data class OverlayCode(val codigo: String, val hora: String, val cantidad: Int = 1)

/** Correcciones de un vale que viven solo en la sesión; nunca se guardan en Room. */
data class ValeEdit(
    val datos: Map<String, String> = emptyMap(),
    val codigos: List<OverlayCode>? = null
)

fun parseEdits(json: String): Map<String, ValeEdit> {
    val root = JSONObject(json)
    val out = mutableMapOf<String, ValeEdit>()
    val keys = root.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val obj = root.optJSONObject(key) ?: continue
        val datosObj = obj.optJSONObject("datos")
        val datos = mutableMapOf<String, String>()
        if (datosObj != null) {
            val dk = datosObj.keys()
            while (dk.hasNext()) {
                val k = dk.next()
                datos[k] = datosObj.optString(k, "")
            }
        }
        val arr = obj.optJSONArray("codigos")
        val codes = if (arr == null) null else (0 until arr.length()).mapNotNull { i ->
            val c = arr.optJSONObject(i) ?: return@mapNotNull null
            OverlayCode(
                codigo = c.optString("codigo", "").trim(),
                hora = c.optString("hora", "").trim().ifBlank { DateTimeUtils.currentTimestamp() },
                cantidad = c.optInt("cantidad", 1).coerceAtLeast(1)
            )
        }.filter { it.codigo.isNotEmpty() }
        out[key] = ValeEdit(datos, codes)
    }
    return out
}
