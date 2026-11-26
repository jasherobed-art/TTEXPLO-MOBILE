package com.ttexplo.mobile

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class BundledCatalogTest {

    @Test
    fun bundledCatalogHasFullProductList() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "app/src/main/java").exists() }
        val csv = File(root, "app/src/main/assets/catalogo_productos.csv").readText()
        val lines = csv.lines().filter { it.isNotBlank() }
        assertThat(lines.first()).contains("IdProducto")
        assertThat(lines.size).isAtLeast(300)
    }
}
