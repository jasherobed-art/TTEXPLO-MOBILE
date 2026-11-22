package com.ttexplo.mobile.license

import android.content.Context
import android.provider.Settings
import android.util.Base64
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object LicenseManager {
    const val MASTER_SECRET = "TTEXPLO_2026_MASTER_SECRET_KEY_MINING_AUTHORIZATION_98124"
    private const val PREFS_NAME = "ttexplo_license_prefs"
    private const val KEY_TOKEN = "active_license_token"
    private const val KEY_TEMP_1DAY_PROVISIONED = "temp_1day_trial_provisioned"

    private val SHORT_KEY_REGEX = Regex(
        "^TTEX-(PERP|[DAT]\\d{6})-([A-Z0-9]{3,4})-([0-9A-F]{8})$"
    )

    /**
     * Obtiene el identificador único del hardware Android (64-bit hex).
     */
    fun getRawDeviceId(context: Context): String {
        return try {
            val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (id.isNullOrBlank()) "UNKNOWN" else id.trim()
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }

    /**
     * Devuelve el ID de hardware formateado en bloques legibles de 4 caracteres (ej: F020-F600-E29C-76D6).
     */
    fun getFormattedDeviceId(context: Context): String {
        val raw = getRawDeviceId(context).uppercase()
        if (raw == "UNKNOWN" || raw.length < 4) return raw
        return raw.chunked(4).joinToString("-")
    }

    /**
     * Calcula la firma HMAC-SHA256 y retorna el hash en formato hexadecimal en minúsculas.
     */
    fun computeHmacSha256(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)
        val hashBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(hashBytes.size * 2)
        for (b in hashBytes) {
            sb.append(String.format("%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    /**
     * Genera una clave corta escribible a mano firmada con HMAC-SHA256 (compatible 100% con License Master Pro).
     *
     * Formato:
     * - Vitalicia: TTEX-PERP-<DEVTAG>-<HMAC8>   (ej: TTEX-PERP-ALL-8A3F1C92)
     * - Con fecha: TTEX-<T><YYMMDD>-<DEVTAG>-<HMAC8> (ej: TTEX-A271122-F020-7C14A8E0)
     *   donde <T> es D (DEMO), A (ANUAL) o T (TEMPORAL), y <DEVTAG> es ALL o los primeros 4 chars del Device ID.
     *   La firma HMAC-SHA256 se calcula sobre el Device ID completo (o '*') para impedir falsificaciones.
     */
    fun generateShortKey(
        tier: String,
        expiresIsoOrPerpetual: String,
        deviceId: String = "*"
    ): String {
        val normalizedTier = tier.trim().uppercase()
        val tierExpCode = if (normalizedTier == "PERPETUAL" || expiresIsoOrPerpetual.equals("PERPETUAL", ignoreCase = true)) {
            "PERP"
        } else {
            val tierChar = when (normalizedTier) {
                "DEMO" -> 'D'
                "TEMPORAL" -> 'T'
                else -> 'A'
            }
            val dateOnly = if (expiresIsoOrPerpetual.length >= 10) {
                expiresIsoOrPerpetual.substring(0, 10)
            } else {
                LocalDate.now().plusYears(1).toString()
            }
            val localDate = LocalDate.parse(dateOnly)
            val yymmdd = localDate.format(DateTimeFormatter.ofPattern("yyMMdd"))
            "$tierChar$yymmdd"
        }

        val cleanDev = deviceId.replace(Regex("[^A-Za-z0-9*]"), "").uppercase()
        val isWildcard = cleanDev.isBlank() || cleanDev == "*" || cleanDev == "ALL" || cleanDev == "TODOS"
        val canonicalDev = if (isWildcard) "*" else cleanDev
        val devTag = if (isWildcard) "ALL" else cleanDev.take(4)

        val payloadToSign = "SHORT|V1|$tierExpCode|$canonicalDev"
        val sig8 = computeHmacSha256(payloadToSign, MASTER_SECRET).take(8).uppercase()
        return "TTEX-$tierExpCode-$devTag-$sig8"
    }

    /**
     * Interpreta fechas en formato ISO-8601 o estándar de fecha.
     */
    fun parseIsoDate(dateStr: String): Instant? {
        if (dateStr.isBlank() || dateStr.equals("PERPETUAL", ignoreCase = true)) return null
        return try {
            Instant.parse(dateStr)
        } catch (e: Exception) {
            try {
                OffsetDateTime.parse(dateStr).toInstant()
            } catch (e2: Exception) {
                try {
                    val dateOnly = if (dateStr.length >= 10) dateStr.substring(0, 10) else dateStr
                    LocalDate.parse(dateOnly).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant()
                } catch (e3: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Calcula los días restantes de vigencia a partir de una fecha de caducidad.
     * Usa redondeo hacia arriba cuando aún quedan horas de vigencia (> 0s) para no
     * marcar como vencida una licencia en su último día.
     */
    fun getDaysRemaining(expiresStr: String): Long {
        if (expiresStr.equals("PERPETUAL", ignoreCase = true)) return 9999L
        val expiryInstant = parseIsoDate(expiresStr) ?: return 0L
        val now = Instant.now()
        val diffSeconds = expiryInstant.epochSecond - now.epochSecond
        if (diffSeconds <= 0L) return 0L
        return (diffSeconds + 86399L) / 86400L
    }

    /**
     * Intenta normalizar una clave corta ingresada manualmente (acepta minúsculas, espacios o guion extra en la firma).
     */
    private fun tryNormalizeShortKey(rawText: String): String? {
        val compact = rawText
            .uppercase()
            .replace(Regex("[\\s_]+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')

        // Caso exacto: TTEX-PERP-ALL-8A3F1C92 o TTEX-A271122-F020-7C14A8E0
        if (SHORT_KEY_REGEX.matches(compact)) return compact

        // Caso con guion extra en la firma de 8 chars: TTEX-A271122-ALL-9F3C-8A12
        val parts = compact.split("-")
        if (parts.size == 5 && parts[0] == "TTEX" && parts[3].length == 4 && parts[4].length == 4) {
            val merged = "${parts[0]}-${parts[1]}-${parts[2]}-${parts[3]}${parts[4]}"
            if (SHORT_KEY_REGEX.matches(merged)) return merged
        }
        return null
    }

    /**
     * Normaliza y extrae el token oficial TTEX:..., ARIA:... o clave corta firmada TTEX-... a partir de:
     * - Clave corta escribible a mano (TTEX-PERP-ALL-XXXXXXXX o TTEX-A271122-F020-XXXXXXXX).
     * - Token largo copiado directamente (con o sin saltos de línea, espacios o comillas).
     * - Contenido JSON de un archivo .lic exportado por el Generador Web.
     * - Texto con prefijo adicional (ej. mensajes copiados de WhatsApp/correo).
     */
    fun extractNormalizedToken(rawInput: String): Result<String> {
        var text = rawInput
            .replace("\uFEFF", "")
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .trim()
            .trim('"', '\'')
            .trim()

        if (text.isBlank()) {
            return Result.failure(
                IllegalArgumentException("El campo de licencia está vacío. Ingrese una clave corta 'TTEX-...', pegue un token 'TTEX:...', importe un archivo .lic o escanee el código QR.")
            )
        }

        // 1. Si es contenido JSON de un archivo .lic generado por la herramienta web
        if (text.startsWith("{")) {
            try {
                val licJson = JSONObject(text)
                val extracted = licJson.optString("activationToken", "")
                    .ifBlank { licJson.optString("shortKey", "") }
                    .ifBlank { licJson.optString("token", "") }
                    .trim()
                if (extracted.isNotBlank()) {
                    text = extracted
                } else {
                    return Result.failure(
                        IllegalArgumentException("El archivo .lic no contiene el campo 'activationToken' ni 'shortKey'.")
                    )
                }
            } catch (e: Exception) {
                return Result.failure(
                    IllegalArgumentException("Archivo .lic con formato JSON inválido: ${e.message}")
                )
            }
        }

        // 2. Verificar si es una clave corta firmada oficial (TTEX-PERP-ALL-XXXXXXXX o TTEX-A271122-F020-XXXXXXXX)
        val normalizedShort = tryNormalizeShortKey(text)
        if (normalizedShort != null) {
            return Result.success(normalizedShort)
        }

        // 3. Localizar prefijo TTEX: o ARIA: (insensible a mayúsculas, incluso dentro de texto adicional)
        val prefixMatch = Regex("(?i)(TTEX|ARIA):").find(text)
        if (prefixMatch != null) {
            val canonicalPrefix = prefixMatch.groupValues[1].uppercase() + ":"
            val afterPrefix = text.substring(prefixMatch.range.last + 1)
            val compact = afterPrefix.replace(Regex("\\s+"), "").trim('"', '\'')
            return Result.success(canonicalPrefix + compact)
        }

        // 4. Detectar clave corta decorativa antigua sin firma completa (ej: TTEX-AN26-ALL-MI01-75E)
        val upperPreview = text.uppercase()
        if (upperPreview.startsWith("TTEX-") || upperPreview.startsWith("ARIA-")) {
            return Result.failure(
                IllegalArgumentException(
                    "La clave '$text' corresponde al formato visual antiguo sin firma completa. Usa el Generador actualizado para obtener una clave corta firmada (ej. TTEX-A271122-ALL-XXXXXXXX) o haz clic en 'COPIAR' para pegar el token completo 'TTEX:...'."
                )
            )
        }

        return Result.failure(
            IllegalArgumentException(
                "Formato de licencia inválido: ingrese una clave corta firmada ('TTEX-A271122-...'), un token ('TTEX:...'), o importe un archivo .lic oficial."
            )
        )
    }

    /**
     * Decodifica Base64 tolerando formato estándar, URL-safe y padding faltante.
     */
    private fun decodeBase64Flexible(b64Input: String): ByteArray {
        val normalized = b64Input
            .replace(Regex("\\s+"), "")
            .replace('-', '+')
            .replace('_', '/')
        val padLen = (4 - (normalized.length % 4)) % 4
        val padded = normalized + "=".repeat(padLen)
        return try {
            Base64.decode(padded, Base64.DEFAULT)
        } catch (_: Throwable) {
            java.util.Base64.getDecoder().decode(padded)
        }
    }

    /**
     * Valida una clave corta firmada (TTEX-PERP-ALL-XXXXXXXX o TTEX-A271122-F020-XXXXXXXX).
     */
    private fun verifyShortKeyForDevice(
        normalizedShortKey: String,
        currentRawId: String,
        formattedCurrentId: String
    ): Result<LicenseInfo> {
        val match = SHORT_KEY_REGEX.matchEntire(normalizedShortKey)
            ?: return Result.failure(
                IllegalArgumentException("Formato de clave corta inválido. Ejemplo válido: TTEX-A271122-ALL-9F3C8A12.")
            )

        val tierExpCode = match.groupValues[1]
        val devTag = match.groupValues[2]
        val providedSig = match.groupValues[3]

        val isWildcard = devTag == "ALL"
        val cleanCurrentUpper = currentRawId.replace(Regex("[^A-Za-z0-9]"), "").uppercase()

        if (!isWildcard && !cleanCurrentUpper.startsWith(devTag)) {
            return Result.failure(
                IllegalStateException(
                    "Clave corta restringida al dispositivo que inicia con '$devTag', pero esta terminal tiene el ID '$formattedCurrentId'. Copie este ID en el generador o seleccione 'Cualquier Terminal (*)'."
                )
            )
        }

        val canonicalDev = if (isWildcard) "*" else cleanCurrentUpper
        val payloadToSign = "SHORT|V1|$tierExpCode|$canonicalDev"
        val expectedSig = computeHmacSha256(payloadToSign, MASTER_SECRET).take(8).uppercase()

        if (!expectedSig.equals(providedSig, ignoreCase = true)) {
            val detail = if (!isWildcard) {
                "Firma criptográfica inválida para esta terminal ($formattedCurrentId). Verifique que la clave fue generada exactamente para este Device ID y que no tiene errores de captura."
            } else {
                "Firma criptográfica inválida en la clave corta. Verifique cada carácter o genérela nuevamente en License Master Pro."
            }
            return Result.failure(SecurityException(detail))
        }

        val tier: String
        val expiresStr: String
        if (tierExpCode == "PERP") {
            tier = "PERPETUAL"
            expiresStr = "PERPETUAL"
        } else {
            tier = when (tierExpCode[0]) {
                'D' -> "DEMO"
                'T' -> "TEMPORAL"
                else -> "ANUAL"
            }
            val yy = tierExpCode.substring(1, 3).toIntOrNull()
            val mm = tierExpCode.substring(3, 5).toIntOrNull()
            val dd = tierExpCode.substring(5, 7).toIntOrNull()
            val expiryDate = try {
                if (yy == null || mm == null || dd == null) {
                    throw IllegalArgumentException("Fecha inválida")
                }
                LocalDate.of(2000 + yy, mm, dd)
            } catch (e: Exception) {
                return Result.failure(IllegalArgumentException("Fecha de vigencia inválida dentro de la clave corta."))
            }

            val expiryInstant = expiryDate.atTime(23, 59, 59).toInstant(ZoneOffset.UTC)
            if (expiryInstant.isBefore(Instant.now())) {
                val todayLocal = LocalDate.now().toString()
                return Result.failure(
                    IllegalStateException(
                        "La licencia venció el $expiryDate (fecha actual del dispositivo: $todayLocal). Verifique la fecha del equipo o emita una clave con mayor vigencia."
                    )
                )
            }
            expiresStr = expiryInstant.toString()
        }

        val info = LicenseInfo(
            id = "SHORT-${providedSig.take(6)}",
            client = "Licencia Activada por Clave Corta",
            mine = if (isWildcard) "Terminal Autorizada (Multiterminal)" else "Terminal $formattedCurrentId",
            supervisor = "Activación Manual Offline",
            deviceId = if (isWildcard) "*" else formattedCurrentId,
            tier = tier,
            issuedDate = LocalDate.now().toString(),
            expiresDate = expiresStr,
            modules = listOf("ENTRADAS", "SALIDAS", "ACCESORIOS", "DEVOLUCIONES"),
            rawToken = normalizedShortKey
        )
        return Result.success(info)
    }

    /**
     * Valida criptográficamente el token largo, clave corta o archivo .lic sin guardarlo aún.
     */
    fun verifyToken(context: Context, tokenString: String): Result<LicenseInfo> {
        val currentRawId = getRawDeviceId(context)
        val formattedCurrent = getFormattedDeviceId(context)
        return verifyTokenForDevice(tokenString, currentRawId, formattedCurrent)
    }

    /**
     * Núcleo puro de verificación criptográfica (también facilita pruebas unitarias sin Context).
     */
    internal fun verifyTokenForDevice(
        tokenString: String,
        currentRawId: String,
        formattedCurrentId: String = currentRawId.uppercase().chunked(4).joinToString("-")
    ): Result<LicenseInfo> {
        val normalizedResult = extractNormalizedToken(tokenString)
        if (normalizedResult.isFailure) {
            return Result.failure(normalizedResult.exceptionOrNull()!!)
        }
        val cleanToken = normalizedResult.getOrThrow()

        // Ruta 1: Clave corta escribible a mano firmada con HMAC-SHA256
        if (SHORT_KEY_REGEX.matches(cleanToken)) {
            return verifyShortKeyForDevice(cleanToken, currentRawId, formattedCurrentId)
        }

        // Ruta 2: Token largo TTEX:<base64>.<hmac32> o ARIA:<base64>.<hmac32>
        val prefix = when {
            cleanToken.startsWith("TTEX:") -> "TTEX:"
            cleanToken.startsWith("ARIA:") -> "ARIA:"
            else -> return Result.failure(
                IllegalArgumentException("Formato de licencia inválido: debe iniciar con 'TTEX:' o ser una clave corta firmada 'TTEX-...'.")
            )
        }

        val payloadAndSig = cleanToken.removePrefix(prefix)
        val dotIndex = payloadAndSig.lastIndexOf('.')
        if (dotIndex <= 0 || dotIndex >= payloadAndSig.length - 1) {
            return Result.failure(
                IllegalArgumentException("Estructura de licencia incompleta (falta delimitador '.' y firma digital). Verifique que el código se haya copiado completo.")
            )
        }

        val b64Payload = payloadAndSig.substring(0, dotIndex).trim()
        val signatureHex = payloadAndSig.substring(dotIndex + 1).trim()

        val jsonString = try {
            val decodedBytes = decodeBase64Flexible(b64Payload)
            String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            return Result.failure(
                IllegalArgumentException("Contenido de licencia ilegible o incompleto: ${e.message}")
            )
        }

        // 1. Verificación de firma HMAC-SHA256 (acepta 32 hex truncados del generador o 64 hex completos)
        val computedFullSig = computeHmacSha256(jsonString, MASTER_SECRET)
        val computedSig32 = computedFullSig.take(32)

        val sigMatches = computedSig32.equals(signatureHex, ignoreCase = true) ||
            computedFullSig.equals(signatureHex, ignoreCase = true)
        if (!sigMatches) {
            return Result.failure(
                SecurityException("Firma criptográfica inválida. Verifique que copió el token completo sin recortes y que fue generado por T&T Explo License Master.")
            )
        }

        // 2. Parseo de datos JSON
        val json = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Estructura de datos inválida: ${e.message}"))
        }

        val id = json.optString("id", "LCN-SIN-ID")
        val client = json.optString("client", "Cliente No Especificado")
        val mine = json.optString("mine", "Faena Minera")
        val supervisor = json.optString("supervisor", "Supervisor General")
        val targetDev = json.optString("dev", "*").trim()
        val tier = json.optString("tier", "ANUAL")
        val issued = json.optString("issued", "")
        val expires = json.optString("expires", "PERPETUAL")

        val modsList = mutableListOf<String>()
        val modsArr = json.optJSONArray("mods")
        if (modsArr != null) {
            for (i in 0 until modsArr.length()) {
                modsList.add(modsArr.optString(i))
            }
        }

        // 3. Verificación de candado de hardware (Device ID)
        val cleanCurrent = currentRawId.replace(Regex("[-\\s:_]"), "").trim()
        val cleanTarget = targetDev.replace(Regex("[-\\s:_]"), "").trim()

        val isWildcard = targetDev == "*" ||
            cleanTarget == "*" ||
            cleanTarget.equals("ALL", ignoreCase = true) ||
            cleanTarget.equals("TODOS", ignoreCase = true) ||
            cleanTarget.equals("CUALQUIERA", ignoreCase = true)
        val isMatch = isWildcard || (cleanTarget.isNotEmpty() && cleanTarget.equals(cleanCurrent, ignoreCase = true))

        if (!isMatch) {
            return Result.failure(
                IllegalStateException(
                    "Licencia restringida al dispositivo '$targetDev', pero esta terminal tiene el ID '$formattedCurrentId'. Copie este ID en el generador o seleccione 'Cualquier Terminal (*)'."
                )
            )
        }

        // 4. Verificación de caducidad
        if (!expires.equals("PERPETUAL", ignoreCase = true)) {
            val expiryInstant = parseIsoDate(expires)
            if (expiryInstant != null && expiryInstant.isBefore(Instant.now())) {
                val dateOnly = if (expires.length >= 10) expires.substring(0, 10) else expires
                val todayLocal = LocalDate.now().toString()
                return Result.failure(
                    IllegalStateException(
                        "La licencia venció el $dateOnly (fecha actual del dispositivo: $todayLocal). Verifique la fecha del equipo o emita una licencia con mayor vigencia."
                    )
                )
            }
        }

        val info = LicenseInfo(
            id = id,
            client = client,
            mine = mine,
            supervisor = supervisor,
            deviceId = targetDev,
            tier = tier,
            issuedDate = issued,
            expiresDate = expires,
            modules = modsList,
            rawToken = cleanToken
        )
        return Result.success(info)
    }

    /**
     * Valida y guarda permanentemente el token o clave corta normalizada en SharedPreferences.
     */
    fun saveLicense(context: Context, tokenString: String): Result<LicenseInfo> {
        val verification = verifyToken(context, tokenString)
        if (verification.isSuccess) {
            val cleanNormalizedToken = verification.getOrThrow().rawToken
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_TOKEN, cleanNormalizedToken).apply()
        }
        return verification
    }

    /**
     * Elimina la licencia activa instalada.
     */
    fun clearLicense(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    /**
     * Aprovisiona por única vez en instalación limpia una licencia temporal oficial de 1 día
     * firmada criptográficamente con HMAC-SHA256 (`TTEX-T<YYMMDD>-ALL-<HMAC8>`).
     *
     * No sobreescribe licencias existentes ni se auto-renueva si vence o se borra manualmente.
     */
    fun ensureOneDayTemporaryLicenseIfNeeded(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingToken = prefs.getString(KEY_TOKEN, null)
        if (!existingToken.isNullOrBlank()) return null
        if (prefs.getBoolean(KEY_TEMP_1DAY_PROVISIONED, false)) return null

        val expiresTomorrow = LocalDate.now().plusDays(1).toString()
        val tempKey = generateShortKey(
            tier = "TEMPORAL",
            expiresIsoOrPerpetual = expiresTomorrow,
            deviceId = "*"
        )
        prefs.edit()
            .putString(KEY_TOKEN, tempKey)
            .putBoolean(KEY_TEMP_1DAY_PROVISIONED, true)
            .apply()
        return tempKey
    }

    /**
     * Comprueba el estado actual de la licencia instalada en la aplicación.
     */
    fun checkLicense(context: Context): LicenseCheckResult {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null)

        if (token.isNullOrBlank()) {
            return LicenseCheckResult(
                status = LicenseStatus.UNLICENSED,
                message = "La aplicación no cuenta con una licencia instalada. Ingrese una clave corta 'TTEX-...', pegue el token 'TTEX:...', importe su archivo .lic o escanee el código QR."
            )
        }

        val verifyResult = verifyToken(context, token)
        if (verifyResult.isFailure) {
            val err = verifyResult.exceptionOrNull()?.message ?: "Licencia no válida"
            val status = when {
                err.contains("dispositivo", ignoreCase = true) || err.contains("terminal", ignoreCase = true) ->
                    LicenseStatus.DEVICE_MISMATCH
                err.contains("venció", ignoreCase = true) || err.contains("expiró", ignoreCase = true) ->
                    LicenseStatus.EXPIRED
                err.contains("Firma", ignoreCase = true) ->
                    LicenseStatus.CORRUPTED
                else ->
                    LicenseStatus.UNLICENSED
            }
            return LicenseCheckResult(
                status = status,
                message = err
            )
        }

        val info = verifyResult.getOrThrow()
        val days = getDaysRemaining(info.expiresDate)

        return when {
            !info.expiresDate.equals("PERPETUAL", ignoreCase = true) && days <= 0L -> {
                val expDateOnly = if (info.expiresDate.length >= 10) info.expiresDate.substring(0, 10) else info.expiresDate
                LicenseCheckResult(
                    status = LicenseStatus.EXPIRED,
                    info = info,
                    daysRemaining = 0L,
                    message = "La licencia de ${info.client} venció el $expDateOnly."
                )
            }
            !info.expiresDate.equals("PERPETUAL", ignoreCase = true) && days <= 30L -> {
                val expDateOnly = if (info.expiresDate.length >= 10) info.expiresDate.substring(0, 10) else info.expiresDate
                LicenseCheckResult(
                    status = LicenseStatus.EXPIRING_SOON,
                    info = info,
                    daysRemaining = days,
                    message = "AVISO DE VIGENCIA: La licencia vencerá en $days días ($expDateOnly). Contacte a T&T Explo para renovación."
                )
            }
            else -> {
                LicenseCheckResult(
                    status = LicenseStatus.VALID,
                    info = info,
                    daysRemaining = days,
                    message = "Licencia autorizada para ${info.client} (${info.mine})."
                )
            }
        }
    }
}
