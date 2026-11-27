package com.ttexplo.mobile.license

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// Tokens emitidos por license_generator/index.html con cada perfil. Si cambia el generador, regenerarlos.
class LicenseGeneratorParityTest {

    private val basicoToken = GeneratorTokens.BASICO

    private val operativoToken = GeneratorTokens.OPERATIVO

    private val completoToken = GeneratorTokens.COMPLETO

    private val completoClaveCorta = GeneratorTokens.COMPLETO_CLAVE_CORTA

    @Test
    fun token_basico_del_generador_se_valida_con_sus_modulos() {
        val info = LicenseManager.verifyTokenForDevice(basicoToken, "CUALQUIER").getOrThrow()
        assertThat(info.client).isEqualTo("T&T Explo")
        assertThat(info.modules).containsExactly("ENTRADAS", "SALIDAS", "HISTORIAL")
    }

    @Test
    fun token_operativo_del_generador_se_valida_con_sus_modulos() {
        val info = LicenseManager.verifyTokenForDevice(operativoToken, "CUALQUIER").getOrThrow()
        assertThat(info.modules).containsExactly(
            "ENTRADAS", "SALIDAS", "ACCESORIOS", "DEVOLUCIONES", "DEVOLUCIONES_ACCESORIOS", "HISTORIAL"
        )
    }

    @Test
    fun token_completo_del_generador_se_valida_con_todos_los_modulos() {
        val info = LicenseManager.verifyTokenForDevice(completoToken, "CUALQUIER").getOrThrow()
        assertThat(info.modules).containsExactlyElementsIn(LicenseModules.ALL)
    }

    @Test
    fun clave_corta_completo_del_generador_se_valida_con_todos_los_modulos() {
        val info = LicenseManager.verifyTokenForDevice(completoClaveCorta, "CUALQUIER").getOrThrow()
        assertThat(info.modules).containsExactlyElementsIn(LicenseModules.ALL)
    }
}
