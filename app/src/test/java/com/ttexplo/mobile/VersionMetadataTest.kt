package com.ttexplo.mobile

import com.example.BuildConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class VersionMetadataTest {

    @Test
    fun appVersionIs282() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("2.8.2")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(40)
    }

    @Test
    fun generatorHtmlTargets282() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "app/src/main/java").exists() }
        val html = File(root, "license_generator/index.html").readText()
        assertThat(html).contains("2.8.2")
        assertThat(html).contains("appVersion")
    }
}
