package com.vericore.core.planner

import java.io.File

enum class BuildSystem { MAVEN, GRADLE, UNKNOWN }

data class BuildSystemInfo(
    val system: BuildSystem,
    val executable: String?,
    val source: String
)

/** Detects the repository's declared build system and selects a usable wrapper when available. */
class BuildSystemDetector {
    fun detect(root: File): BuildSystemInfo {
        require(root.isDirectory) { "Repository path is not a directory: ${root.path}" }

        val mvnw = File(root, "mvnw")
        if (mvnw.isFile && mvnw.canExecute()) {
            return BuildSystemInfo(BuildSystem.MAVEN, "./mvnw", "mvnw")
        }

        val gradlew = File(root, "gradlew")
        if (gradlew.isFile && gradlew.canExecute()) {
            return BuildSystemInfo(BuildSystem.GRADLE, "./gradlew", "gradlew")
        }

        if (File(root, "pom.xml").isFile) {
            return BuildSystemInfo(BuildSystem.MAVEN, "mvn", "pom.xml")
        }

        if (File(root, "build.gradle").isFile || File(root, "build.gradle.kts").isFile ||
            File(root, "settings.gradle").isFile || File(root, "settings.gradle.kts").isFile) {
            return BuildSystemInfo(BuildSystem.GRADLE, "gradle", "Gradle build files")
        }

        return BuildSystemInfo(BuildSystem.UNKNOWN, null, "no recognized build descriptor")
    }
}
