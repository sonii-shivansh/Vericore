package com.vericore

import com.github.ajalt.clikt.testing.test
import com.vericore.cli.EvolutionCommand
import com.vericore.cli.ImprovedAnalyzeCommand
import org.eclipse.jgit.api.Git
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class E2ETest {

    @Test
    fun analyzeCommandWritesSnapshotAndHtmlReportForFixture() {
        val repository = Files.createTempDirectory("vericore-analyze-e2e")
        try {
            Files.createDirectories(repository.resolve("src/main/kotlin/example"))
            Files.writeString(
                repository.resolve("src/main/kotlin/example/Test.kt"),
                "package example\nclass Test { fun hello(): String = \"hello\" }\n"
            )
            // Analysis records repository state and history, so the fixture must be a real Git repository.
            Git.init().setDirectory(repository.toFile()).call().use { git ->
                git.add().addFilepattern(".").call()
                git.commit().setMessage("fixture").setAuthor("Vericore Test", "test@example.com").call()
            }

            // Clikt's test helper parses command-line arguments; forward slashes avoid
            // treating Windows path separators as escapes. Windows accepts these paths.
            val repositoryPath = repository.toAbsolutePath().toString().replace('\\', '/')
            val result = ImprovedAnalyzeCommand().test(repositoryPath, "--no-cache")

            assertEquals(0, result.statusCode, result.stdout)
            assertTrue(result.stdout.contains("Analysis snapshot"), result.stdout)
            assertTrue(Files.isRegularFile(repository.resolve("output/analysis-snapshot.json")))
            assertTrue(Files.size(repository.resolve("output/analysis-snapshot.json")) > 0)
            assertTrue(Files.isRegularFile(repository.resolve("output/index.html")))
            assertTrue(Files.size(repository.resolve("output/index.html")) > 0)
            assertTrue(Files.isDirectory(repository.resolve(".vericore/sessions")))
        } finally {
            repository.toFile().deleteRecursively()
        }
    }

    @Test
    fun evolutionCommandExposesDocumentedOptions() {
        val result = EvolutionCommand().test("--help")

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("--months"), result.stdout)
        assertTrue(result.stdout.contains("--interval"), result.stdout)
    }
}
