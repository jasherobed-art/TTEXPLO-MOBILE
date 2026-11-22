package com.ttexplo.mobile.license

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LicenseProvisioningTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("ttexplo_license_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun ensureOneDayTemporaryLicense_provisionsOnceOnCleanInstall_andNeverAutoRenewsAfterClear() {
        // 1. Clean install: provisions a real HMAC-SHA256 signed 1-day TEMPORAL short key
        val provisionedKey = LicenseManager.ensureOneDayTemporaryLicenseIfNeeded(context)
        assertThat(provisionedKey).isNotNull()
        assertThat(provisionedKey).startsWith("TTEX-T")
        assertThat(provisionedKey).contains("-ALL-")

        val check = LicenseManager.checkLicense(context)
        assertThat(check.status).isAnyOf(LicenseStatus.VALID, LicenseStatus.EXPIRING_SOON)
        assertThat(check.info).isNotNull()
        assertThat(check.info!!.tier).isEqualTo("TEMPORAL")
        assertThat(check.daysRemaining).isAtLeast(1L)
        assertThat(check.daysRemaining).isAtMost(2L)

        // 2. Subsequent call is a no-op (does not replace active key)
        val secondCall = LicenseManager.ensureOneDayTemporaryLicenseIfNeeded(context)
        assertThat(secondCall).isNull()

        // 3. Clearing license must NOT re-grant another free 1-day trial
        LicenseManager.clearLicense(context)
        val afterClear = LicenseManager.ensureOneDayTemporaryLicenseIfNeeded(context)
        assertThat(afterClear).isNull()
        assertThat(LicenseManager.checkLicense(context).status).isEqualTo(LicenseStatus.UNLICENSED)
    }

    @Test
    fun ensureOneDayTemporaryLicense_neverOverwritesExistingValidLicense() {
        val perpetualKey = LicenseManager.generateShortKey("PERPETUAL", "PERPETUAL", "*")
        val saved = LicenseManager.saveLicense(context, perpetualKey)
        assertThat(saved.isSuccess).isTrue()

        val result = LicenseManager.ensureOneDayTemporaryLicenseIfNeeded(context)
        assertThat(result).isNull()

        val check = LicenseManager.checkLicense(context)
        assertThat(check.status).isEqualTo(LicenseStatus.VALID)
        assertThat(check.info!!.tier).isEqualTo("PERPETUAL")
    }
}
