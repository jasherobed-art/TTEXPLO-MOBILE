package com.ttexplo.mobile.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.ttexplo.mobile.printer.PaperWidth

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("ttexplo_prefs", Context.MODE_PRIVATE)

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
        val widthStr = prefs.getString("paper_width", "WIDTH_4_INCH") ?: "WIDTH_4_INCH"
        return if (widthStr == "WIDTH_3_INCH") PaperWidth.WIDTH_3_INCH else PaperWidth.WIDTH_4_INCH
    }
    fun setPaperWidth(width: PaperWidth) = prefs.edit().putString("paper_width", width.name).apply()

    fun getPrinterProtocol(): String = prefs.getString("printer_protocol", "CPCL") ?: "CPCL"
    fun setPrinterProtocol(protocol: String) = prefs.edit().putString("printer_protocol", protocol.trim()).apply()

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
    }
}
