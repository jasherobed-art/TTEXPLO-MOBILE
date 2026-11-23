package com.ttexplo.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.ttexplo.mobile.ui.theme.Typography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DesignSystemTest {

    @Test
    fun `ningun rol tipografico baja de 11sp`() {
        val roles = listOf(
            Typography.displayLarge, Typography.displayMedium, Typography.displaySmall,
            Typography.headlineLarge, Typography.headlineMedium, Typography.headlineSmall,
            Typography.titleLarge, Typography.titleMedium, Typography.titleSmall,
            Typography.bodyLarge, Typography.bodyMedium, Typography.bodySmall,
            Typography.labelLarge, Typography.labelMedium, Typography.labelSmall
        )
        roles.forEach { role ->
            assertTrue("${role.fontSize} por debajo de 11sp", role.fontSize.value >= 11f)
        }
    }

    @Test
    fun `tipografia usa familias reales`() {
        assertNotNull(Typography.displayLarge.fontFamily)
        assertNotNull(Typography.bodyMedium.fontFamily)
        assertNotNull(Typography.labelSmall.fontFamily)
    }

    @Test
    fun `cadenas de escaneo y dialogos estan en strings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Imprimir", context.getString(R.string.scan_print))
        assertEquals("Imprimiendo...", context.getString(R.string.scan_printing))
        assertEquals("Ver todos", context.getString(R.string.scan_view_all))
        assertEquals("Guardar Entrada (%1\$d)", context.getString(R.string.scan_save_entrada))
        assertEquals("Sí, salir", context.getString(R.string.scan_exit_confirm_draft))
        assertEquals("SÍ, ELIMINAR (2/2)", context.getString(R.string.delete_confirm))
    }
}
