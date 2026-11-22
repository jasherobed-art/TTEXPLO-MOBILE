package com.ttexplo.mobile.license

enum class LicenseStatus {
    VALID,           // Activa y con vigencia mayor a 30 días
    EXPIRING_SOON,   // Activa pero faltan <= 30 días para vencer
    EXPIRED,         // Fecha de vigencia superada
    DEVICE_MISMATCH, // La licencia no corresponde al Hardware ID de este teléfono
    UNLICENSED,      // No hay licencia instalada
    CORRUPTED        // Firma criptográfica inválida o alterada
}

data class LicenseInfo(
    val id: String,
    val client: String,
    val mine: String,
    val supervisor: String,
    val deviceId: String,
    val tier: String,
    val issuedDate: String,
    val expiresDate: String,
    val modules: List<String>,
    val rawToken: String
)

data class LicenseCheckResult(
    val status: LicenseStatus,
    val info: LicenseInfo? = null,
    val daysRemaining: Long = 0L,
    val message: String = ""
)
