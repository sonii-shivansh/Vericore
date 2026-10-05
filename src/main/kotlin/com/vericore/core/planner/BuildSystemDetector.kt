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

        // Some real repositories are polyglot monorepos whose primary Java build lives
        // one directory below the repository root (for example java/pom.xml). Only
        // accept a nested build when exactly one supported build root exists; this
        // avoids guessing between unrelated modules.
        val nested = root.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.flatMap { child ->
                val candidates = mutableListOf<BuildSystemInfo>()
                val childMvnw = File(child, "mvnw")
                val childGradlew = File(child, "gradlew")
                if (childMvnw.isFile && childMvnw.canExecute()) {
                    candidates += BuildSystemInfo(BuildSystem.MAVEN, "./mvnw", "${child.name}/mvnw", child.name)
                } else if (childGradlew.isFile && childGradlew.canExecute()) {
                    candidates += BuildSystemInfo(BuildSystem.GRADLE, "./gradlew", "${child.name}/gradlew", child.name)
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
}
