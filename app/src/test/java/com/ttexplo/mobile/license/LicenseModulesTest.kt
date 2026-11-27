package com.ttexplo.mobile.license

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

class LicenseModulesTest {

    private val tiers = listOf("DEMO", "TEMPORAL", "ANUAL", "PERPETUAL")
    private val basico = listOf(LicenseModules.ENTRADAS, LicenseModules.SALIDAS)
    private val completo = LicenseModules.ALL

    private fun checkWith(status: LicenseStatus, tier: String, modules: List<String>) = LicenseCheckResult(
        status = status,
        info = LicenseInfo(
            id = "LCN-TEST",
            client = "Cliente",
            mine = "Mina",
            supervisor = "Supervisor",
            deviceId = "*",
            tier = tier,
            issuedDate = "2026-11-26",
            expiresDate = "PERPETUAL",
            modules = modules,
            rawToken = "TTEX-PERP-ALL-00000000"
        )
    )

    private fun tokenWith(mods: List<String>, mv: Int?): String {
        val payload = JSONObject().apply {
            put("v", 1)
            put("id", "LCN-MODS-TEST")
            put("client", "Prueba")
            put("mine", "Polvorín")
            put("supervisor", "Supervisor")
            put("dev", "*")
            put("tier", "ANUAL")
            if (mv != null) put("mv", mv)
            put("issued", Instant.now().toString())
            put("expires", Instant.now().plus(365, ChronoUnit.DAYS).toString())
            put("mods", JSONArray(mods))
        }.toString()
        val b64 = Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8))
        return "TTEX:$b64.${LicenseManager.computeHmacSha256(payload, LicenseManager.MASTER_SECRET).take(32)}"
    }

    @Test
    fun configuracion_siempre_permitida_sin_importar_estado() {
        LicenseStatus.values().forEach { status ->
            assertWithMessage(status.name)
                .that(LicenseModules.isAllowed(LicenseCheckResult(status = status), LicenseModules.CONFIGURACION))
                .isTrue()
        }
    }

    @Test
    fun combinaciones_de_tier_y_modulos_respetan_la_lista_autorizada() {
        for (tier in tiers) {
            for ((perfil, mods) in listOf("BASICO" to basico, "COMPLETO" to completo)) {
                for (modulo in LicenseModules.ALL) {
                    val state = checkWith(LicenseStatus.VALID, tier, mods)
                    assertWithMessage("$tier/$perfil/$modulo")
                        .that(LicenseModules.isAllowed(state, modulo))
                        .isEqualTo(modulo in mods)
                }
            }
        }
    }

    @Test
    fun estados_no_activos_bloquean_todos_los_modulos() {
        val bloqueados = listOf(
            LicenseStatus.EXPIRED,
            LicenseStatus.DEVICE_MISMATCH,
            LicenseStatus.UNLICENSED,
            LicenseStatus.CORRUPTED
        )
        for (status in bloqueados) {
            for (modulo in LicenseModules.ALL) {
                assertWithMessage("$status/$modulo")
                    .that(LicenseModules.isAllowed(checkWith(status, "ANUAL", completo), modulo))
                    .isFalse()
            }
        }
    }

    @Test
    fun proxima_a_vencer_mantiene_los_modulos_autorizados() {
        val state = checkWith(LicenseStatus.EXPIRING_SOON, "ANUAL", basico)
        assertThat(LicenseModules.isAllowed(state, LicenseModules.ENTRADAS)).isTrue()
        assertThat(LicenseModules.isAllowed(state, LicenseModules.HISTORIAL)).isFalse()
    }

    @Test
    fun token_con_mv2_respeta_los_modulos_del_perfil() {
        val result = LicenseManager.verifyTokenForDevice(tokenWith(basico, mv = 2), "CUALQUIER")
        assertThat(result.getOrThrow().modules).containsExactlyElementsIn(basico)
    }

    @Test
    fun token_sin_mv_conserva_acceso_total() {
        val result = LicenseManager.verifyTokenForDevice(tokenWith(basico, mv = null), "CUALQUIER")
        assertThat(result.getOrThrow().modules).containsExactlyElementsIn(LicenseModules.ALL)
    }

    @Test
    fun token_v2_sin_modulos_solo_deja_configuracion() {
        val info = LicenseManager.verifyTokenForDevice(tokenWith(emptyList(), mv = 2), "CUALQUIER").getOrThrow()
        val state = checkWith(LicenseStatus.VALID, "ANUAL", info.modules)
        assertThat(LicenseModules.isAllowed(state, LicenseModules.ENTRADAS)).isFalse()
        assertThat(LicenseModules.isAllowed(state, LicenseModules.CONFIGURACION)).isTrue()
    }

    @Test
    fun firma_cubre_el_campo_mv() {
        val token = tokenWith(basico, mv = 2)
        val payload = String(Base64.getDecoder().decode(token.removePrefix("TTEX:").substringBefore(".")), Charsets.UTF_8)
        val forged = payload.replace("\"mv\":2", "\"mv\":1")
        assertThat(forged).contains("\"mv\":1")
        val forgedToken = "TTEX:${Base64.getEncoder().encodeToString(forged.toByteArray(Charsets.UTF_8))}.${token.substringAfterLast(".")}"
        val result = LicenseManager.verifyTokenForDevice(forgedToken, "CUALQUIER")
        assertThat(result.exceptionOrNull()).isInstanceOf(SecurityException::class.java)
    }

    @Test
    fun from_token_respeta_versiones_de_mv() {
        assertThat(LicenseModules.fromToken(basico, modsVersion = 1)).containsExactlyElementsIn(LicenseModules.ALL)
        assertThat(LicenseModules.fromToken(basico, modsVersion = 2)).containsExactlyElementsIn(basico)
        assertThat(LicenseModules.fromToken(basico, modsVersion = 3)).containsExactlyElementsIn(basico)
    }

    @Test
    fun module_for_vale_asigna_el_modulo_de_cada_tipo() {
        assertThat(LicenseModules.moduleForVale("DESPACHO")).isEqualTo(LicenseModules.SALIDAS)
        assertThat(LicenseModules.moduleForVale("ENTRADA")).isEqualTo(LicenseModules.ENTRADAS)
        assertThat(LicenseModules.moduleForVale("ACCESORIOS")).isEqualTo(LicenseModules.ACCESORIOS)
        assertThat(LicenseModules.moduleForVale("DEV. ACCESORIOS")).isEqualTo(LicenseModules.DEVOLUCIONES_ACCESORIOS)
        assertThat(LicenseModules.moduleForVale("DEVOLUCION")).isEqualTo(LicenseModules.DEVOLUCIONES)
        assertThat(LicenseModules.moduleForVale("CONTEO")).isEqualTo(LicenseModules.CONTEO_EN_CAJA)
    }

    @Test
    fun clave_corta_otorga_todos_los_modulos() {
        val key = LicenseManager.generateShortKey("ANUAL", "2099-01-01", "*")
        val result = LicenseManager.verifyTokenForDevice(key, "CUALQUIER")
        assertThat(result.getOrThrow().modules).containsExactlyElementsIn(LicenseModules.ALL)
    }
}
