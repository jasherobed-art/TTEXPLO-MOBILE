package com.ttexplo.mobile.license

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LicenseVaultTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("ttexplo_license_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        java.io.File(context.filesDir, "license_vault.json").delete()
    }

    private fun validToken(): String {
        val payload = JSONObject().apply {
            put("v", 1)
            put("id", "LCN-TEST-VAULT-01")
            put("client", "Prueba")
            put("mine", "Polvorín")
            put("supervisor", "Supervisor")
            put("dev", "*")
            put("tier", "ANUAL")
            put("issued", Instant.now().toString())
            put("expires", Instant.now().plus(365, ChronoUnit.DAYS).toString())
            put("mods", JSONArray(listOf("ENTRADAS", "SALIDAS", "ACCESORIOS", "DEVOLUCIONES")))
        }.toString()
        val b64 = Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8))
        return "TTEX:$b64.${LicenseManager.computeHmacSha256(payload, LicenseManager.MASTER_SECRET).take(32)}"
    }

    @Test
    fun roundTripJsonKeepsEntries() {
        val entry = LicenseVault.VaultEntry("id-1", "Polvorín Norte", "TTEX:abc", "2026-11-23T00:00:00Z")
        val json = LicenseVault.toJson(listOf(entry))
        assertThat(LicenseVault.fromJson(json)).containsExactly(entry)
    }

    @Test
    fun replaceAllRoundTrips() {
        val entries = listOf(
            LicenseVault.VaultEntry("a", "A", "TTEX:a", "t1"),
            LicenseVault.VaultEntry("b", "B", "TTEX:b", "t2")
        )
        LicenseVault.replaceAll(context, entries)
        assertThat(LicenseVault.load(context)).containsExactlyElementsIn(entries)
    }

    @Test
    fun addRejectsInvalidToken() {
        val result = LicenseVault.add(context, "Mala", "basura")
        assertThat(result.isFailure).isTrue()
        assertThat(LicenseVault.load(context)).isEmpty()
    }

    @Test
    fun saveLicenseMarksRealLicenseAndHidesDemo() {
        assertThat(LicenseManager.isDemoModeAllowed(context)).isTrue()

        val result = LicenseManager.saveLicense(context, validToken())
        assertThat(result.isSuccess).isTrue()
        assertThat(LicenseManager.isDemoModeAllowed(context)).isFalse()
    }

    @Test
    fun factoryTemporaryLicenseKeepsDemoVisible() {
        val temp = LicenseManager.ensureOneDayTemporaryLicenseIfNeeded(context)
        assertThat(temp).isNotNull()
        assertThat(LicenseManager.checkLicense(context).status)
            .isIn(listOf(LicenseStatus.VALID, LicenseStatus.EXPIRING_SOON))
        assertThat(LicenseManager.isDemoModeAllowed(context)).isTrue()
    }
}
