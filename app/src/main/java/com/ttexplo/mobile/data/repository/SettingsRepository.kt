package com.ttexplo.mobile.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.ttexplo.mobile.printer.PaperWidth
import com.ttexplo.mobile.printer.PrinterProfile

class SettingsRepository internal constructor(
    private val prefs: SharedPreferences
) {

    constructor(context: Context) : this(
        context.getSharedPreferences("ttexplo_prefs", Context.MODE_PRIVATE)
    )

    fun getMineName(): String = prefs.getString("mine_name", "MINERA T&T S.A. DE C.V.") ?: "MINERA T&T S.A. DE C.V."
    fun setMineName(name: String) = prefs.edit().putString("mine_name", name.trim()).apply()

    fun getPermitNumber(): String = prefs.getString("permit_number", "PERMISO GENERAL SEDENA NO. 1234") ?: "PERMISO GENERAL SEDENA NO. 1234"
    fun setPermitNumber(permit: String) = prefs.edit().putString("permit_number", permit.trim()).apply()

    fun getTicketHeader(): String = prefs.getString("ticket_header", "POLVORÍN PRINCIPAL") ?: "POLVORÍN PRINCIPAL"
    fun setTicketHeader(header: String) = prefs.edit().putString("ticket_header", header.trim()).apply()

    fun getPreferredPrinterMac(): String = prefs.getString("preferred_printer_mac", "") ?: ""
    fun setPreferredPrinterMac(mac: String) = prefs.edit().putString("preferred_printer_mac", mac).apply()

    fun getPreferredPrinterName(): String = prefs.getString("preferred_printer_name", "") ?: ""
    fun setPreferredPrinterName(name: String) = prefs.edit().putString("preferred_printer_name", name).apply()

    fun getPaperWidth(): PaperWidth {
        val widthStr = prefs.getString("paper_width", PaperWidth.WIDTH_4_INCH.name)
        return PaperWidth.fromNameOrDefault(widthStr)
    }
    fun setPaperWidth(width: PaperWidth) {
        prefs.edit().putString("paper_width", width.name).apply()
        syncActiveProfileSettings(paperWidth = width)
    }

    fun getPrinterProtocol(): String = prefs.getString("printer_protocol", "CPCL") ?: "CPCL"
    fun setPrinterProtocol(protocol: String) {
        val normalized = protocol.trim().uppercase()
        prefs.edit().putString("printer_protocol", normalized).apply()
        syncActiveProfileSettings(protocol = normalized)
    }

    // =========================================================================
    // GESTIÓN DE MÚLTIPLES IMPRESORAS BLUETOOTH (v2.7.0)
    // =========================================================================

    fun getPrinterProfiles(): List<PrinterProfile> {
        val raw = prefs.getString(KEY_PRINTER_PROFILES, null)
        val parsed = PrinterProfile.listFromJsonString(raw)
        if (parsed.isNotEmpty()) {
            val activeId = prefs.getString(KEY_ACTIVE_PRINTER_ID, null)
            return if (activeId != null && parsed.any { it.id == activeId }) {
                parsed.map { it.copy(isDefault = it.id == activeId) }
            } else {
                val firstDefault = parsed.firstOrNull { it.isDefault } ?: parsed.first()
                parsed.map { it.copy(isDefault = it.id == firstDefault.id) }
            }
        }

        // Migración automática transparente desde v2.4.1 (impresora única legada)
        val legacyMac = getPreferredPrinterMac().trim()
        if (legacyMac.isNotBlank()) {
            val legacyName = getPreferredPrinterName().trim().ifBlank { legacyMac }
            val migrated = PrinterProfile(
                alias = legacyName,
                macAddress = legacyMac,
                deviceName = legacyName,
                protocol = getPrinterProtocol(),
                paperWidth = getPaperWidth(),
                isDefault = true
            )
            savePrinterProfiles(listOf(migrated))
            return listOf(migrated)
        }

        return emptyList()
    }

    fun savePrinterProfiles(profiles: List<PrinterProfile>) {
        val normalized = if (profiles.isEmpty()) {
            emptyList()
        } else {
            val defaultProfile = profiles.firstOrNull { it.isDefault } ?: profiles.first()
            profiles.map { it.copy(isDefault = it.id == defaultProfile.id) }
        }

        val active = normalized.firstOrNull { it.isDefault }
        val editor = prefs.edit()
            .putString(KEY_PRINTER_PROFILES, PrinterProfile.listToJsonString(normalized))
            .putString(KEY_ACTIVE_PRINTER_ID, active?.id ?: "")

        // Mantener sincronizadas las claves legadas para compatibilidad 100%
        if (active != null) {
            editor
                .putString("preferred_printer_mac", active.macAddress)
                .putString("preferred_printer_name", active.displayTitle())
                .putString("printer_protocol", active.protocol)
                .putString("paper_width", active.paperWidth.name)
        } else {
            editor
                .putString("preferred_printer_mac", "")
                .putString("preferred_printer_name", "")
        }
        editor.apply()
    }

    fun getActivePrinterProfile(): PrinterProfile? {
        val profiles = getPrinterProfiles()
        return profiles.firstOrNull { it.isDefault } ?: profiles.firstOrNull()
    }

    fun upsertPrinterProfile(profile: PrinterProfile, setAsActive: Boolean = false): List<PrinterProfile> {
        val current = getPrinterProfiles().toMutableList()
        val existingIdx = current.indexOfFirst {
            it.id == profile.id || it.macAddress.equals(profile.macAddress, ignoreCase = true)
        }
        val makeActive = setAsActive || profile.isDefault || current.isEmpty()
        val updatedProfile = profile.copy(isDefault = makeActive)

        if (existingIdx >= 0) {
            val keepId = current[existingIdx].id
            current[existingIdx] = updatedProfile.copy(id = keepId)
        } else {
            current.add(updatedProfile)
        }

        val finalList = if (makeActive) {
            val targetId = if (existingIdx >= 0) current[existingIdx].id else updatedProfile.id
            current.map { it.copy(isDefault = it.id == targetId) }
        } else {
            current
        }
        savePrinterProfiles(finalList)
        return getPrinterProfiles()
    }

    fun setActivePrinterProfile(profileId: String): List<PrinterProfile> {
        val current = getPrinterProfiles()
        if (current.none { it.id == profileId }) return current
        val updated = current.map { it.copy(isDefault = it.id == profileId) }
        savePrinterProfiles(updated)
        return getPrinterProfiles()
    }

    fun deletePrinterProfile(profileId: String): List<PrinterProfile> {
        val remaining = getPrinterProfiles().filterNot { it.id == profileId }
        savePrinterProfiles(remaining)
        return getPrinterProfiles()
    }

    private fun syncActiveProfileSettings(protocol: String? = null, paperWidth: PaperWidth? = null) {
        val raw = prefs.getString(KEY_PRINTER_PROFILES, null) ?: return
        val profiles = PrinterProfile.listFromJsonString(raw)
        if (profiles.isEmpty()) return
        val activeId = prefs.getString(KEY_ACTIVE_PRINTER_ID, null)
        val updated = profiles.map { p ->
            val isTarget = if (!activeId.isNullOrBlank()) p.id == activeId else p.isDefault
            if (isTarget) {
                p.copy(
                    protocol = protocol ?: p.protocol,
                    paperWidth = paperWidth ?: p.paperWidth
                )
            } else {
                p
            }
        }
        prefs.edit().putString(KEY_PRINTER_PROFILES, PrinterProfile.listToJsonString(updated)).apply()
    }

    // =========================================================================

    // Orientación de pantalla: true = vertical fijo, false = el usuario puede girar
    fun getLockPortrait(): Boolean = prefs.getBoolean("lock_portrait", true)
    fun setLockPortrait(enabled: Boolean) = prefs.edit().putBoolean("lock_portrait", enabled).apply()

    fun getPrintSignatures(): Boolean = prefs.getBoolean("print_signatures", true)
    fun setPrintSignatures(enabled: Boolean) = prefs.edit().putBoolean("print_signatures", enabled).apply()

    fun getPrintBreakdown(): Boolean = prefs.getBoolean("print_breakdown", true)
    fun setPrintBreakdown(enabled: Boolean) = prefs.edit().putBoolean("print_breakdown", enabled).apply()

    fun getPrintCodeDetails(): Boolean = prefs.getBoolean("print_code_details", true)
    fun setPrintCodeDetails(enabled: Boolean) = prefs.edit().putBoolean("print_code_details", enabled).apply()

    // Audio, Vibración y Destello Visual en escaneo
    fun getScanSoundEnabled(): Boolean = prefs.getBoolean("scan_sound_enabled", true)
    fun setScanSoundEnabled(enabled: Boolean) = prefs.edit().putBoolean("scan_sound_enabled", enabled).apply()

    fun getScanVibrationEnabled(): Boolean = prefs.getBoolean("scan_vibration_enabled", true)
    fun setScanVibrationEnabled(enabled: Boolean) = prefs.edit().putBoolean("scan_vibration_enabled", enabled).apply()

    fun getScanVisualFlashEnabled(): Boolean = prefs.getBoolean("scan_visual_flash_enabled", true)
    fun setScanVisualFlashEnabled(enabled: Boolean) = prefs.edit().putBoolean("scan_visual_flash_enabled", enabled).apply()

    fun getScanVolumePercent(): Int = prefs.getInt("scan_volume_percent", 100).coerceIn(10, 100)
    fun setScanVolumePercent(percent: Int) = prefs.edit().putInt("scan_volume_percent", percent.coerceIn(10, 100)).apply()

    /** "MEDIA" (Multimedia) o "ALARM" (Alarma / Alta Potencia para polvorín ruidoso). */
    fun getScanAudioStream(): String {
        val raw = prefs.getString("scan_audio_stream", AUDIO_STREAM_MEDIA) ?: AUDIO_STREAM_MEDIA
        return if (raw == AUDIO_STREAM_ALARM) AUDIO_STREAM_ALARM else AUDIO_STREAM_MEDIA
    }
    fun setScanAudioStream(stream: String) {
        val normalized = if (stream == AUDIO_STREAM_ALARM) AUDIO_STREAM_ALARM else AUDIO_STREAM_MEDIA
        prefs.edit().putString("scan_audio_stream", normalized).apply()
    }

    companion object {
        const val AUDIO_STREAM_MEDIA = "MEDIA"
        const val AUDIO_STREAM_ALARM = "ALARM"
        private const val KEY_PRINTER_PROFILES = "printer_profiles_json"
        private const val KEY_ACTIVE_PRINTER_ID = "active_printer_profile_id"
    }
}
