package com.ttexplo.mobile.license

object LicenseModules {
    const val ENTRADAS = "ENTRADAS"
    const val SALIDAS = "SALIDAS"
    const val ACCESORIOS = "ACCESORIOS"
    const val DEVOLUCIONES = "DEVOLUCIONES"
    const val DEVOLUCIONES_ACCESORIOS = "DEVOLUCIONES_ACCESORIOS"
    const val CONTEO_EN_CAJA = "CONTEO_EN_CAJA"
    const val INVENTARIO_POLVORIN = "INVENTARIO_POLVORIN"
    const val HISTORIAL = "HISTORIAL"
    const val CONFIGURACION = "CONFIGURACION"

    /** Versión del campo `mv` del token largo. Los tokens sin `mv` son anteriores a los módulos por licencia. */
    const val MODS_VERSION = 2
    const val BLOCKED_MESSAGE = "Función no incluida en esta licencia. Contacte a soporte para actualizar."

    val ALL: List<String> = listOf(
        ENTRADAS, SALIDAS, ACCESORIOS, DEVOLUCIONES,
        DEVOLUCIONES_ACCESORIOS, CONTEO_EN_CAJA, INVENTARIO_POLVORIN, HISTORIAL
    )

    /** Sin `mv` la app nunca restringió módulos, así que esas licencias conservan acceso total. */
    fun fromToken(mods: List<String>, modsVersion: Int): List<String> =
        if (modsVersion >= MODS_VERSION) mods else ALL

    fun moduleForVale(tipo: String): String = when (tipo) {
        "DESPACHO" -> SALIDAS
        "ENTRADA" -> ENTRADAS
        "ACCESORIOS" -> ACCESORIOS
        "DEV. ACCESORIOS" -> DEVOLUCIONES_ACCESORIOS
        "DEVOLUCION" -> DEVOLUCIONES
        "CONTEO" -> CONTEO_EN_CAJA
        else -> HISTORIAL
    }

    fun isAllowed(state: LicenseCheckResult, module: String): Boolean {
        if (module == CONFIGURACION) return true
        val active = state.status == LicenseStatus.VALID || state.status == LicenseStatus.EXPIRING_SOON
        return active && state.info?.modules?.contains(module) == true
    }
}
