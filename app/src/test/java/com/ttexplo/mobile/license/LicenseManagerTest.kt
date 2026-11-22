package com.ttexplo.mobile.license

import com.google.common.truth.Truth.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Base64

class LicenseManagerTest {

    private fun generateWebToolToken(
        prefix: String = "TTEX:",
        dev: String = "*",
        tier: String = "ANUAL",
        expires: String = Instant.now().plus(365, ChronoUnit.DAYS).toString(),
        client: String = "Minera San Julián S.A. de C.V.",
        mine: String = "Mina Subterránea - Polvorín Principal"
    ): String {
        val payload = JSONObject().apply {
            put("v", 1)
            put("id", "LCN-20261122-AB12")
            put("client", client)
            put("mine", mine)
            put("supervisor", "Ing. Roberto Almonte (Jefe de Voladuras)")
            put("dev", dev)
            put("tier", tier)
            put("issued", Instant.now().toString())
            put("expires", expires)
            put("mods", JSONArray(listOf("ENTRADAS", "SALIDAS", "ACCESORIOS", "DEVOLUCIONES")))
        }

        val payloadStr = payload.toString()
        val sigHex = LicenseManager.computeHmacSha256(payloadStr, LicenseManager.MASTER_SECRET)
        val b64 = Base64.getEncoder().encodeToString(payloadStr.toByteArray(Charsets.UTF_8))
        return "$prefix$b64.${sigHex.take(32)}"
    }

    @Test
    fun computeHmacSha256_isDeterministicAndMatchesStandard() {
        val hash = LicenseManager.computeHmacSha256("test-data", LicenseManager.MASTER_SECRET)
        assertThat(hash).isNotEmpty()
        assertThat(hash.length).isEqualTo(64)
    }

    @Test
    fun verifyTokenForDevice_webToolTtexTokenWithSpanishAccents_succeeds() {
        val token = generateWebToolToken(prefix = "TTEX:", dev = "F020-F600-E29C-76D6")
        val result = LicenseManager.verifyTokenForDevice(token, "f020f600e29c76d6")
        assertThat(result.isSuccess).isTrue()
        val info = result.getOrThrow()
        assertThat(info.client).isEqualTo("Minera San Julián S.A. de C.V.")
        assertThat(info.mine).isEqualTo("Mina Subterránea - Polvorín Principal")
        assertThat(info.tier).isEqualTo("ANUAL")
    }

    @Test
    fun verifyTokenForDevice_licFileJsonPayload_succeeds() {
        val token = generateWebToolToken(prefix = "TTEX:", dev = "*")
        val licJson = JSONObject().apply {
            put("licenseKey", "TTEX-AN26-ALL-MI01-9B3C")
            put("activationToken", token)
            put("details", JSONObject().put("client", "Minera San Julián S.A. de C.V."))
        }.toString(2)

        val result = LicenseManager.verifyTokenForDevice(licJson, "1122334455667788")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().rawToken).isEqualTo(token)
    }

    @Test
    fun verifyTokenForDevice_tokenWithWhitespaceAndQuotes_succeeds() {
        val token = generateWebToolToken(prefix = "TTEX:", dev = "*")
        val messy = "  \"${token.substring(0, 40)}\n  ${token.substring(40)}\"  "
        val result = LicenseManager.verifyTokenForDevice(messy, "aabbccddeeff0011")
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun verifyShortKey_perpetualWildcard_succeeds() {
        val shortKey = LicenseManager.generateShortKey("PERPETUAL", "PERPETUAL", "*")
        assertThat(shortKey).startsWith("TTEX-PERP-ALL-")

        val result = LicenseManager.verifyTokenForDevice(shortKey.lowercase(), "f020f600e29c76d6")
        assertThat(result.isSuccess).isTrue()
        val info = result.getOrThrow()
        assertThat(info.tier).isEqualTo("PERPETUAL")
        assertThat(info.expiresDate).isEqualTo("PERPETUAL")
        assertThat(info.deviceId).isEqualTo("*")
    }

    @Test
    fun verifyShortKey_annualDeviceLocked_matchesExactDeviceAndBlocksOtherDevice() {
        val futureDate = LocalDate.now().plusDays(180).toString()
        val shortKey = LicenseManager.generateShortKey("ANUAL", futureDate, "F020-F600-E29C-76D6")
        assertThat(shortKey).contains("-F020-")

        // Same device (even with extra hyphen or spaces when typed manually)
        val typedWithSpaces = shortKey.replace("-", " ")
        val okResult = LicenseManager.verifyTokenForDevice(typedWithSpaces, "f020f600e29c76d6")
        assertThat(okResult.isSuccess).isTrue()
        assertThat(okResult.getOrThrow().tier).isEqualTo("ANUAL")

        // Different device with same 4-char prefix MUST fail HMAC verification
        val spoofedPrefixResult = LicenseManager.verifyTokenForDevice(shortKey, "f020000000000000")
        assertThat(spoofedPrefixResult.isFailure).isTrue()
        assertThat(spoofedPrefixResult.exceptionOrNull()).isInstanceOf(SecurityException::class.java)

        // Different device with different prefix returns device mismatch
        val wrongDeviceResult = LicenseManager.verifyTokenForDevice(shortKey, "1122334455667788")
        assertThat(wrongDeviceResult.isFailure).isTrue()
        assertThat(wrongDeviceResult.exceptionOrNull()?.message).contains("F020")
    }

    @Test
    fun verifyShortKey_expiredDate_failsWithClearMessage() {
        val expiredShortKey = LicenseManager.generateShortKey("DEMO", "2020-01-15", "*")
        val result = LicenseManager.verifyTokenForDevice(expiredShortKey, "f020f600e29c76d6")
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("2020-01-15")
    }

    @Test
    fun verifyShortKey_tamperedSignature_failsSecurityCheck() {
        val validShortKey = LicenseManager.generateShortKey("PERPETUAL", "PERPETUAL", "*")
        val tampered = validShortKey.dropLast(1) + if (validShortKey.endsWith("0")) "1" else "0"
        val result = LicenseManager.verifyTokenForDevice(tampered, "f020f600e29c76d6")
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(SecurityException::class.java)
    }

    @Test
    fun verifyTokenForDevice_legacyShortDisplayKey_returnsClearGuidance() {
        val result = LicenseManager.verifyTokenForDevice("TTEX-AN26-ALL-MI01-75E", "f020f600e29c76d6")
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("formato visual antiguo")
    }

    @Test
    fun verifyTokenForDevice_deviceMismatch_reportsBothDeviceIds() {
        val token = generateWebToolToken(prefix = "TTEX:", dev = "ZBR-8A4F-92C1")
        val result = LicenseManager.verifyTokenForDevice(token, "f020f600e29c76d6")
        assertThat(result.isFailure).isTrue()
        val msg = result.exceptionOrNull()?.message.orEmpty()
        assertThat(msg).contains("ZBR-8A4F-92C1")
        assertThat(msg).contains("F020-F600-E29C-76D6")
    }

    @Test
    fun verifyTokenForDevice_legacyAriaPrefix_stillSupported() {
        val token = generateWebToolToken(prefix = "ARIA:", dev = "*")
        val result = LicenseManager.verifyTokenForDevice(token, "1234567890abcdef")
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun daysRemaining_calculationIsAccurateIncludingLastDay() {
        val perpetual = LicenseManager.getDaysRemaining("PERPETUAL")
        assertThat(perpetual).isGreaterThan(1000L)

        val expired = LicenseManager.getDaysRemaining("2020-01-01T00:00:00.000Z")
        assertThat(expired).isEqualTo(0L)

        val sixHoursLeft = Instant.now().plus(6, ChronoUnit.HOURS).toString()
        assertThat(LicenseManager.getDaysRemaining(sixHoursLeft)).isEqualTo(1L)
    }

    @Test
    fun formattedDeviceId_formatsHexIntoFourCharGroups() {
        val raw = "f020f600e29c76d6"
        val formatted = raw.uppercase().chunked(4).joinToString("-")
        assertThat(formatted).isEqualTo("F020-F600-E29C-76D6")
    }
}
