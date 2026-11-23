package com.ttexplo.mobile.ui.navigation

import java.net.URLDecoder
import java.util.Base64

/**
 * Codificador URL-safe inmune a caracteres especiales (%, /, +, ?, #, espacios, acentos, comillas)
 * para pasar argumentos de encabezados a través de Navigation Compose sin riesgo de crash.
 */
object RouteParamCodec {
    private const val PREFIX = "b64~"

    fun encode(value: String, fallback: String = "-"): String {
        val clean = value.trim().ifBlank { fallback }
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(clean.toByteArray(Charsets.UTF_8))
        return "$PREFIX$encoded"
    }

    fun decode(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        if (raw.startsWith(PREFIX)) {
            return try {
                val payload = raw.removePrefix(PREFIX)
                String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
            } catch (_: Exception) {
                raw
            }
        }
        return try {
            URLDecoder.decode(raw, "UTF-8")
        } catch (_: Exception) {
            raw
        }
    }
}

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object EntradasHeader : Screen("entradas_header")
    object EntradasScan : Screen("entradas_scan/{prov}/{polv}/{fact}?id={id}") {
        fun createRoute(prov: String, polv: String, fact: String, id: Long = 0L): String {
            val encProv = RouteParamCodec.encode(prov)
            val encPolv = RouteParamCodec.encode(polv)
            val encFact = RouteParamCodec.encode(fact)
            return "entradas_scan/$encProv/$encPolv/$encFact?id=$id"
        }
    }
    object DespachoHeader : Screen("despacho_header")
    object DespachoScan : Screen("despacho_scan/{vale}/{empresa}/{lugar}/{transportista}/{turno}/{voladura}?id={id}") {
        fun createRoute(
            vale: String,
            empresa: String,
            lugar: String,
            transportista: String,
            turno: String,
            voladura: String,
            id: Long = 0L
        ): String {
            val encVale = RouteParamCodec.encode(vale)
            val encEmp = RouteParamCodec.encode(empresa)
            val encLugar = RouteParamCodec.encode(lugar)
            val encTrans = RouteParamCodec.encode(transportista)
            val encTurno = RouteParamCodec.encode(turno, fallback = "1")
            val encVol = RouteParamCodec.encode(voladura)
            return "despacho_scan/$encVale/$encEmp/$encLugar/$encTrans/$encTurno/$encVol?id=$id"
        }
    }
    object AccesoriosHeader : Screen("accesorios_header")
    object AccesoriosScan : Screen("accesorios_scan/{vale}/{empresa}/{lugar}/{transportista}/{turno}/{recibio}/{entrego}/{voladura}?id={id}") {
        fun createRoute(
            vale: String, empresa: String, lugar: String, transportista: String,
            turno: String, recibio: String, entrego: String, voladura: String, id: Long = 0L
        ): String {
            val encVale = RouteParamCodec.encode(vale)
            val encEmp = RouteParamCodec.encode(empresa)
            val encLugar = RouteParamCodec.encode(lugar)
            val encTrans = RouteParamCodec.encode(transportista)
            val encTurno = RouteParamCodec.encode(turno, fallback = "1")
            val encRec = RouteParamCodec.encode(recibio)
            val encEnt = RouteParamCodec.encode(entrego)
            val encVol = RouteParamCodec.encode(voladura)
            return "accesorios_scan/$encVale/$encEmp/$encLugar/$encTrans/$encTurno/$encRec/$encEnt/$encVol?id=$id"
        }
    }
    object DevolucionAccesoriosHeader : Screen("devolucion_accesorios_header")
    object DevolucionAccesoriosScan : Screen("devolucion_accesorios_scan/{vale}/{empresa}/{lugar}/{transportista}/{turno}/{recibio}/{entrego}/{voladura}?id={id}") {
        fun createRoute(
            vale: String, empresa: String, lugar: String, transportista: String,
            turno: String, recibio: String, entrego: String, voladura: String, id: Long = 0L
        ): String {
            val encVale = RouteParamCodec.encode(vale)
            val encEmp = RouteParamCodec.encode(empresa)
            val encLugar = RouteParamCodec.encode(lugar)
            val encTrans = RouteParamCodec.encode(transportista)
            val encTurno = RouteParamCodec.encode(turno, fallback = "1")
            val encRec = RouteParamCodec.encode(recibio)
            val encEnt = RouteParamCodec.encode(entrego)
            val encVol = RouteParamCodec.encode(voladura)
            return "devolucion_accesorios_scan/$encVale/$encEmp/$encLugar/$encTrans/$encTurno/$encRec/$encEnt/$encVol?id=$id"
        }
    }
    object Devoluciones : Screen("devoluciones?id={id}") {
        fun createRoute(id: Long = 0L): String = "devoluciones?id=$id"
    }
    object Settings : Screen("settings")
    object CatalogManagement : Screen("catalog_management")
    object ValesHistory : Screen("vales_history")
    object Inventory : Screen("inventory")
    object InventorySync : Screen("inventory_sync?tab={tab}") {
        fun createRoute(tab: Int = 0): String = "inventory_sync?tab=$tab"
    }
    object License : Screen("license?canBack={canBack}") {
        fun createRoute(canBack: Boolean = false): String = "license?canBack=$canBack"
    }
    object LicenseVault : Screen("license_vault")
}
