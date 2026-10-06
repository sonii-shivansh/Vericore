package com.vericore.core.planner

import java.io.File

enum class BuildSystem { MAVEN, GRADLE, UNKNOWN }

data class BuildSystemInfo(
    val system: BuildSystem,
    val executable: String?,
    val source: String,
    val workingDirectory: String = ""
)

/** Detects repository build descriptors, preferring executable wrappers and explicit root descriptors. */
class BuildSystemDetector {
    fun detect(root: File, windowsPlatform: Boolean = isWindows()): BuildSystemInfo {
        require(root.isDirectory) { "Repository path is not a directory: ${root.path}" }

        val mvnw = File(root, if (windowsPlatform) "mvnw.cmd" else "mvnw")
        if (mvnw.isFile && (windowsPlatform || mvnw.canExecute())) {
            return BuildSystemInfo(BuildSystem.MAVEN, if (windowsPlatform) "mvnw.cmd" else "./mvnw", mvnw.name)
        }

        val gradlew = File(root, if (windowsPlatform) "gradlew.bat" else "gradlew")
        if (gradlew.isFile && (windowsPlatform || gradlew.canExecute())) {
            return BuildSystemInfo(BuildSystem.GRADLE, if (windowsPlatform) "gradlew.bat" else "./gradlew", gradlew.name)
        }

        if (File(root, "pom.xml").isFile) {
            return BuildSystemInfo(BuildSystem.MAVEN, "mvn", "pom.xml")
        }

        if (File(root, "build.gradle").isFile || File(root, "build.gradle.kts").isFile ||
            File(root, "settings.gradle").isFile || File(root, "settings.gradle.kts").isFile) {
            return BuildSystemInfo(BuildSystem.GRADLE, "gradle", "Gradle build files")
        }

        val nested = root.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.flatMap { child ->
                val candidates = mutableListOf<BuildSystemInfo>()
                val childMvnw = File(child, if (windowsPlatform) "mvnw.cmd" else "mvnw")
                val childGradlew = File(child, if (windowsPlatform) "gradlew.bat" else "gradlew")
                if (childMvnw.isFile && (windowsPlatform || childMvnw.canExecute())) {
                    candidates += BuildSystemInfo(BuildSystem.MAVEN, if (windowsPlatform) "mvnw.cmd" else "./mvnw", "${child.name}/${childMvnw.name}", child.name)
                } else if (childGradlew.isFile && (windowsPlatform || childGradlew.canExecute())) {
                    candidates += BuildSystemInfo(BuildSystem.GRADLE, if (windowsPlatform) "gradlew.bat" else "./gradlew", "${child.name}/${childGradlew.name}", child.name)
                } else if (File(child, "pom.xml").isFile) {
                    candidates += BuildSystemInfo(BuildSystem.MAVEN, "mvn", "${child.name}/pom.xml", child.name)
                } else if (File(child, "build.gradle").isFile || File(child, "build.gradle.kts").isFile ||
                    File(child, "settings.gradle").isFile || File(child, "settings.gradle.kts").isFile) {
                    candidates += BuildSystemInfo(BuildSystem.GRADLE, "gradle", "${child.name}/Gradle build files", child.name)
                }
                candidates
            }
            ?.distinctBy { "${it.system}:${it.workingDirectory}" }
            ?: emptyList()

        return if (nested.size == 1) nested.single()
        else BuildSystemInfo(BuildSystem.UNKNOWN, null, "no recognized build descriptor")
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")
}
