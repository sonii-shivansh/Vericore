package com.vericore.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File

class VersionTest : StringSpec({
    "application version should match the Gradle project version" {
        val buildFile = File("build.gradle.kts")
        val match = Regex("""(?m)^version\s*=\s*"([^"]+)"\s*$""")
            .find(buildFile.readText())
            ?: error("Could not find project version in build.gradle.kts")

        Version.current shouldBe match.groupValues[1]
    }
})
