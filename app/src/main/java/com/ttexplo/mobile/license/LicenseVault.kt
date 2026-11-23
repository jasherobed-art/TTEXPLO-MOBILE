package com.ttexplo.mobile.license

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Maletín local de licencias emitidas fuera de la app (License Master Pro).
 * Cada entrada se valida con LicenseManager antes de guardarse.
 */
object LicenseVault {

    data class VaultEntry(
        val id: String,
        val alias: String,
        val token: String,
        val createdAt: String
    )

    private const val FILE_NAME = "license_vault.json"

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun load(context: Context): List<VaultEntry> = fromJson(file(context).readTextSafe())

    fun add(context: Context, alias: String, token: String): Result<Unit> {
        val verified = LicenseManager.verifyToken(context, token)
        if (verified.isFailure) return Result.failure(verified.exceptionOrNull()!!)
        val cleanToken = verified.getOrThrow().rawToken
        val entry = VaultEntry(
            id = UUID.randomUUID().toString(),
            alias = alias.trim().ifBlank { "Licencia ${verified.getOrThrow().tier}" },
            token = cleanToken,
            createdAt = Instant.now().toString()
        )
        val updated = load(context) + entry
        file(context).writeText(toJson(updated))
        return Result.success(Unit)
    }

    fun remove(context: Context, id: String) {
        file(context).writeText(toJson(load(context).filterNot { it.id == id }))
    }

    fun toJson(entries: List<VaultEntry>): String {
        val array = JSONArray()
        entries.forEach { e ->
            array.put(JSONObject().apply {
                put("id", e.id)
                put("alias", e.alias)
                put("token", e.token)
                put("createdAt", e.createdAt)
            })
        }
        return JSONObject().put("entries", array).toString()
    }

    fun fromJson(json: String): List<VaultEntry> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONObject(json).optJSONArray("entries") ?: return emptyList()
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id", "")
                val token = o.optString("token", "")
                if (id.isBlank() || token.isBlank()) return@mapNotNull null
                VaultEntry(
                    id = id,
                    alias = o.optString("alias", "Licencia"),
                    token = token,
                    createdAt = o.optString("createdAt", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun File.readTextSafe(): String = try {
        if (exists()) readText() else ""
    } catch (e: Exception) {
        ""
    }
}
