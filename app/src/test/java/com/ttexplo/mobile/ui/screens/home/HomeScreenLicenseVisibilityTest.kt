package com.ttexplo.mobile.ui.screens.home

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.ttexplo.mobile.license.GeneratorTokens
import com.ttexplo.mobile.license.LicenseManager
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeScreenLicenseVisibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    private val tarjetas = mapOf(
        1 to "1. Entradas de Alto y Agente",
        2 to "2. Salidas de Alto y Agente",
        3 to "3. Salida de Accesorios",
        4 to "4. CONTEO EN CAJA",
        5 to "5. Devolución de Alto y Agente",
        6 to "6. Devolución de Accesorios",
        7 to "7. Inventario en Polvorín (En Vivo)",
        8 to "8. Historial de Vales & Consolidado",
        9 to "9. Configuración y Personalización"
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        LicenseManager.clearLicense(context)
    }

    private fun mostrarInicioCon(token: String, vararg visibles: Int) {
        LicenseManager.saveLicense(context, token).getOrThrow()
        composeRule.setContent {
            HomeScreen(
                onNavigateToEntradas = {},
                onNavigateToDespacho = {},
                onNavigateToAccesorios = {},
                onNavigateToDevoluciones = {},
                onNavigateToSettings = {}
            )
        }
        tarjetas.forEach { (numero, titulo) ->
            val nodo = composeRule.onNodeWithText(titulo)
            if (numero in visibles) nodo.assertExists() else nodo.assertDoesNotExist()
        }
    }

    @Test
    fun basico_muestra_entradas_salidas_historial_y_configuracion() {
        mostrarInicioCon(GeneratorTokens.BASICO, 1, 2, 8, 9)
    }

    @Test
    fun operativo_oculta_conteo_e_inventario() {
        mostrarInicioCon(GeneratorTokens.OPERATIVO, 1, 2, 3, 5, 6, 8, 9)
    }

    @Test
    fun completo_muestra_todas_las_tarjetas() {
        mostrarInicioCon(GeneratorTokens.COMPLETO, 1, 2, 3, 4, 5, 6, 7, 8, 9)
    }
}
