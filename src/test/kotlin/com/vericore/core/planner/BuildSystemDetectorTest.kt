package com.vericore.core.planner

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildSystemDetectorTest {
    private val detector = BuildSystemDetector()

    @Test
    fun `Windows Maven wrapper is selected when Windows platform is requested`() {
        val repo = Files.createTempDirectory("vericore-windows-maven").toFile()
        try {
            Files.writeString(repo.toPath().resolve("pom.xml"), "<project/>\n")
            Files.writeString(repo.toPath().resolve("mvnw.cmd"), "@echo off\r\nexit /b 0\r\n")

            val info = detector.detect(repo, windowsPlatform = true)

            assertEquals(BuildSystem.MAVEN, info.system)
            assertEquals("mvnw.cmd", info.executable)
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `Windows Gradle wrapper is selected when Windows platform is requested`() {
        val repo = Files.createTempDirectory("vericore-windows-gradle").toFile()
        try {
            Files.writeString(repo.toPath().resolve("build.gradle.kts"), "plugins {}\n")
            Files.writeString(repo.toPath().resolve("gradlew.bat"), "@echo off\r\nexit /b 0\r\n")

            val info = detector.detect(repo, windowsPlatform = true)

            assertEquals(BuildSystem.GRADLE, info.system)
            assertEquals("gradlew.bat", info.executable)
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `Windows wrapper detection works for a nested build root`() {
        val repo = Files.createTempDirectory("vericore-windows-nested").toFile()
        try {
            val module = repo.resolve("java").apply { mkdirs() }
            Files.writeString(module.toPath().resolve("pom.xml"), "<project/>\n")
            Files.writeString(module.toPath().resolve("mvnw.cmd"), "@echo off\r\nexit /b 0\r\n")

            val info = detector.detect(repo, windowsPlatform = true)

            assertEquals(BuildSystem.MAVEN, info.system)
            assertEquals("mvnw.cmd", info.executable)
            assertEquals("java", info.workingDirectory)
        } finally {
            repo.deleteRecursively()
        }
    }
}
