package com.ttexplo.mobile

import com.example.BuildConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class VersionMetadataTest {

    @Test
    fun appVersionIsCurrent() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("2.14.3")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(59)
    }

    @Test
    fun generatorHtmlTargetsCurrentVersion() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "app/src/main/java").exists() }
        val html = File(root, "license_generator/index.html").readText()
        assertThat(html).contains(BuildConfig.VERSION_NAME)
        assertThat(html).contains("appVersion")
    }
}
