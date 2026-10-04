package com.vericore.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class VersionTest : StringSpec({
    "application version should be exposed from one version contract" {
        Version.current shouldBe "0.8.2"
    }
})
