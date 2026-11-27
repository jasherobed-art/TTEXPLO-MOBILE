package com.ttexplo.mobile.ui.navigation

import com.google.common.truth.Truth.assertThat
import com.ttexplo.mobile.license.LicenseModules
import org.junit.Test

class AppNavigationLicenseTest {

    @Test
    fun rutas_de_modulo_requieren_su_modulo() {
        assertThat(requiredLicenseModule(Screen.EntradasHeader.route)).isEqualTo(LicenseModules.ENTRADAS)
        assertThat(requiredLicenseModule(Screen.EntradasScan.route)).isEqualTo(LicenseModules.ENTRADAS)
        assertThat(requiredLicenseModule(Screen.DespachoScan.route)).isEqualTo(LicenseModules.SALIDAS)
        assertThat(requiredLicenseModule(Screen.AccesoriosScan.route)).isEqualTo(LicenseModules.ACCESORIOS)
        assertThat(requiredLicenseModule(Screen.Devoluciones.route)).isEqualTo(LicenseModules.DEVOLUCIONES)
        assertThat(requiredLicenseModule(Screen.DevolucionAccesoriosScan.route)).isEqualTo(LicenseModules.DEVOLUCIONES_ACCESORIOS)
        assertThat(requiredLicenseModule(Screen.BoxCount.route)).isEqualTo(LicenseModules.CONTEO_EN_CAJA)
        assertThat(requiredLicenseModule(Screen.Inventory.route)).isEqualTo(LicenseModules.INVENTARIO_POLVORIN)
        assertThat(requiredLicenseModule(Screen.InventorySync.route)).isEqualTo(LicenseModules.INVENTARIO_POLVORIN)
        assertThat(requiredLicenseModule(Screen.ValesHistory.route)).isEqualTo(LicenseModules.HISTORIAL)
    }

    @Test
    fun rutas_siempre_abiertas_no_tienen_modulo() {
        assertThat(requiredLicenseModule(Screen.Home.route)).isNull()
        assertThat(requiredLicenseModule(Screen.Settings.route)).isNull()
        assertThat(requiredLicenseModule(Screen.CatalogManagement.route)).isNull()
        assertThat(requiredLicenseModule(Screen.License.route)).isNull()
        assertThat(requiredLicenseModule(Screen.LicenseVault.route)).isNull()
        assertThat(requiredLicenseModule(null)).isNull()
    }
}
