package com.vericore.core.config

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.github.oshai.kotlinlogging.KotlinLogging

@Serializable
data class StoredAIConfig(
    val provider: String = "gemini",
    val apiKey: String = "",
    val model: String = "gemini-3.8-flash"
)

@Serializable
data class UserConfig(
    val ai: StoredAIConfig = StoredAIConfig()
)

object UserConfigStore {
    private const val CONFIG_HOME_ENV = "VERICORE_CONFIG_HOME"
    private const val LEGACY_CONFIG_HOME_ENV = "CODECONTEXT_CONFIG_HOME"
    private const val CONFIG_HOME_PROPERTY = "vericore.config.home"
    private const val LEGACY_CONFIG_HOME_PROPERTY = "codecontext.config.home"

    private val logger = KotlinLogging.logger {}
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun configFile(): File = File(configDirectory(), "config.json")

    fun load(): UserConfig? {
        val canonical = configFile()
        if (canonical.isFile) return read(canonical)
        val legacy = legacyConfigFile()
        if (legacy.isFile) {
            val message = "Deprecated legacy user configuration detected at ${legacy.path}; migrate to ${canonical.path}."
            logger.warn { message }
            System.err.println("⚠️ $message")
            return read(legacy)
        }
        return null
    }

    fun saveAi(provider: String, apiKey: String, model: String) {
        require(apiKey.isNotBlank()) { "API key must not be blank" }
        val file = configFile()
        file.parentFile.mkdirs()
        file.writeText(json.encodeToString(UserConfig(StoredAIConfig(provider, apiKey, model))))
        restrictPermissions(file)
    }

    fun delete(): Boolean = configFile().delete() || legacyConfigFile().delete()

    private fun configDirectory(): File {
        val explicitProperty = System.getProperty(CONFIG_HOME_PROPERTY)?.trim().orEmpty()
        if (explicitProperty.isNotEmpty()) return File(explicitProperty)
        val explicitEnvironment = System.getenv(CONFIG_HOME_ENV)?.trim().orEmpty()
        if (explicitEnvironment.isNotEmpty()) return File(explicitEnvironment)
        val os = System.getProperty("os.name", "").lowercase()
        return when {
            os.contains("win") -> File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "Vericore")
            os.contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support/Vericore")
            else -> {
                val xdg = System.getenv("XDG_CONFIG_HOME")?.trim().orEmpty()
                File(if (xdg.isNotEmpty()) xdg else File(System.getProperty("user.home"), ".config").absolutePath, "vericore")
            }
        }
    }

    private fun legacyConfigFile(): File = File(legacyConfigDirectory(), "config.json")

    private fun legacyConfigDirectory(): File {
        val explicitProperty = System.getProperty(LEGACY_CONFIG_HOME_PROPERTY)?.trim().orEmpty()
        if (explicitProperty.isNotEmpty()) return File(explicitProperty)
        val explicitEnvironment = System.getenv(LEGACY_CONFIG_HOME_ENV)?.trim().orEmpty()
        if (explicitEnvironment.isNotEmpty()) return File(explicitEnvironment)
        val os = System.getProperty("os.name", "").lowercase()
        return when {
            os.contains("win") -> File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "CodeContext")
            os.contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support/CodeContext")
            else -> {
                val xdg = System.getenv("XDG_CONFIG_HOME")?.trim().orEmpty()
                File(if (xdg.isNotEmpty()) xdg else File(System.getProperty("user.home"), ".config").absolutePath, "codecontext")
            }
        }
    }

    private fun read(file: File): UserConfig? = runCatching { json.decodeFromString<UserConfig>(file.readText()) }.getOrNull()

    private fun restrictPermissions(file: File) {
        runCatching {
            Files.setPosixFilePermissions(file.toPath(), setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        }
    }
}
