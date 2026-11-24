package com.ttexplo.mobile

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class InventoryLayoutTest {

    @Test
    fun quickLoadActionIsBottomBarNotFloatingButton() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "app/src/main/java").exists() }
        val content = File(root, "app/src/main/java/com/ttexplo/mobile/ui/screens/inventory/InventoryScreen.kt").readText()

        assertThat(content).contains("bottomBar = {")
        assertThat(content).doesNotContain("floatingActionButton = {")
        assertThat(content).contains("inventory_quick_load_button")
    }
}
