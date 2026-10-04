package com.vericore.core.planner

import java.io.File

data class VerificationCommandSet(
    val commands: List<String>,
    val notes: List<String> = emptyList(),
    val uncertainties: List<String> = emptyList()
)

/** Detects the repository's concrete build tooling before generating agent-facing verification commands. */
object VerificationCommandGenerator {
    fun generate(root: File): VerificationCommandSet {
        val repository = root.canonicalFile
        require(repository.isDirectory) { "Repository path is not a directory: ${repository.path}" }

        val gradleWrapper = repository.resolve("gradlew").takeIf { it.isFile }
            ?: repository.resolve("gradlew.bat").takeIf { it.isFile }
        val hasGradleBuild = repository.resolve("build.gradle.kts").isFile || repository.resolve("build.gradle").isFile

        val mavenPom = findMavenPom(repository)
        val mavenWrapper = repository.resolve("mvnw").takeIf { it.isFile }

        return when {
            gradleWrapper != null || hasGradleBuild -> {
                val launcher = if (repository.resolve("gradlew").isFile) "./gradlew" else "gradlew.bat"
                VerificationCommandSet(
                    commands = listOf(
                        "cd ${shellQuote(repository.path)} && $launcher --no-daemon clean test",
                        "cd ${shellQuote(repository.path)} && $launcher --no-daemon build installDist"
                    ),
                    notes = if (mavenPom != null) {
                        listOf("Both Gradle and Maven project files were detected; the Gradle build was selected because a Gradle build/wrapper is present at repository root.")
                    } else {
                        emptyList()
                    }
                )
            }

            mavenPom != null -> {
                val launcher = if (mavenWrapper != null) "./mvnw" else "mvn"
                val pomPath = repository.toPath().relativize(mavenPom.toPath()).toString().replace(File.separatorChar, '/')
                VerificationCommandSet(
                    commands = listOf(
                        "cd ${shellQuote(repository.path)} && $launcher -f ${shellQuote(pomPath)} clean test",
                        "cd ${shellQuote(repository.path)} && $launcher -f ${shellQuote(pomPath)} -DskipTests package"
                    ),
                    notes = listOf("Maven verification commands were generated from $pomPath; no Gradle command is assumed."),
                    uncertainties = if (mavenWrapper == null) {
                        listOf("The repository has no Maven wrapper; verification assumes 'mvn' is available in the execution environment.")
                    } else {
                        emptyList()
                    }
                )
            }

            else -> VerificationCommandSet(
                commands = emptyList(),
                uncertainties = listOf(
                    "No supported Gradle or Maven build entry point was detected; Vericore intentionally generated no build commands rather than inventing commands."
                )
            )
        }
    }

    private fun findMavenPom(root: File): File? {
        root.resolve("pom.xml").takeIf { it.isFile }?.let { return it }

        return root.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory && it.name != ".git" && !it.name.startsWith(".") }
            .map { it.resolve("pom.xml") }
            .filter { it.isFile }
            .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
            .firstOrNull()
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"
}
