package com.ttexplo.mobile.printer

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Perfil guardado de impresora térmica Bluetooth.
 * Cada impresora conserva su propio alias, dirección MAC, protocolo y ancho de papel.
 */
data class PrinterProfile(
    val id: String = UUID.randomUUID().toString(),
    val alias: String,
    val macAddress: String,
    val deviceName: String = "",
    val protocol: String = "CPCL", // "CPCL", "ESCPOS", "PLAIN"
    val paperWidth: PaperWidth = PaperWidth.WIDTH_4_INCH,
    val isDefault: Boolean = false
) {
    fun displayTitle(): String = alias.ifBlank { deviceName.ifBlank { macAddress } }

    fun protocolLabel(): String = when (protocol.uppercase()) {
        "CPCL" -> "CPCL"
        "ESCPOS" -> "ESC/POS"
        "PLAIN" -> "RAW"
        else -> protocol
    }

    fun shortSpecSummary(): String = "${paperWidth.combinedLabel} · ${protocolLabel()}"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("alias", alias)
        put("macAddress", macAddress)
        put("deviceName", deviceName)
        put("protocol", protocol)
        put("paperWidth", paperWidth.name)
        put("isDefault", isDefault)
    }

    companion object {
        fun fromJson(obj: JSONObject): PrinterProfile {
            val widthName = obj.optString("paperWidth", PaperWidth.WIDTH_4_INCH.name)
            val width = PaperWidth.fromNameOrDefault(widthName)
            val rawProto = obj.optString("protocol", "CPCL").uppercase()
            val proto = if (rawProto in listOf("CPCL", "ESCPOS", "PLAIN")) rawProto else "CPCL"
            return PrinterProfile(
                id = obj.optString("id", UUID.randomUUID().toString()).ifBlank { UUID.randomUUID().toString() },
                alias = obj.optString("alias", ""),
                macAddress = obj.optString("macAddress", ""),
                deviceName = obj.optString("deviceName", ""),
                protocol = proto,
                paperWidth = width,
                isDefault = obj.optBoolean("isDefault", false)
            )
        }

        fun listToJsonString(profiles: List<PrinterProfile>): String {
            val arr = JSONArray()
            profiles.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJsonString(json: String?): List<PrinterProfile> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                val out = mutableListOf<PrinterProfile>()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val parsed = fromJson(item)
                    if (parsed.macAddress.isNotBlank()) {
                        out.add(parsed)
                    }
                }
                out
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
