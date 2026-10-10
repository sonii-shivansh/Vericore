package com.vericore.core.workflow

import com.vericore.core.ai.EvidenceCitation
import com.vericore.core.ai.GROUNDED_EVIDENCE_SCHEMA_VERSION
import com.vericore.core.ai.GroundedEvidence
import com.vericore.core.config.ConfigLoader
import com.vericore.core.intelligence.ANALYSIS_SCHEMA_VERSION
import com.vericore.core.intelligence.AnalysisMetrics
import com.vericore.core.intelligence.AnalysisSnapshot
import com.vericore.core.intelligence.ArchitectureSnapshot
import com.vericore.core.intelligence.EngineeringContextEngine
import com.vericore.core.intelligence.FileSnapshot
import com.vericore.core.intelligence.RepositorySnapshot
import com.vericore.core.scanner.RepositoryScanner
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.eclipse.jgit.api.Git
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EngineeringPreparationTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun rejectsCachedSnapshotWhenSourceChangesWithoutChangingHead() = runBlocking {
        val root = gitFixture()
        try {
            val before = currentState(root)
            writeCachedInputs(root, before.snapshotDigest, before.snapshotDigest)
            writeSource(root, "after")
            val after = currentState(root)

            assertEquals(before.repositoryCommit, after.repositoryCommit)
            assertNotEquals(before.snapshotDigest, after.snapshotDigest)

            val result = EngineeringPreparation.prepare(
                root.path,
                "validate the service change",
                listOf("src/main/java/example/Service.java")
            )

            assertFalse(result.evidence.citations.any { it.id == "stale.evidence" })
            assertEquals(after.snapshotDigest, result.evidence.repositoryStateDigest)
            assertEquals(after.repositoryCommit, result.evidence.repositoryCommit)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsEvidenceThatDoesNotMatchAnOtherwiseCurrentSnapshot() = runBlocking {
        val root = gitFixture()
        try {
            val before = currentState(root)
            writeSource(root, "after")
            Git.open(root).use { git ->
                git.add().addFilepattern("src/main/java/example/Service.java").call()
                git.commit()
                    .setMessage("change service source")
                    .setAuthor("Vericore Test", "vericore-test@example.invalid")
                    .setCommitter("Vericore Test", "vericore-test@example.invalid")
                    .call()
            }
            val current = currentState(root)
            assertNotEquals(before.repositoryCommit, current.repositoryCommit)
            assertNotEquals(before.snapshotDigest, current.snapshotDigest)

            writeCachedInputs(root, current.snapshotDigest, before.snapshotDigest)
            val result = EngineeringPreparation.prepare(
                root.path,
                "validate the service change",
                listOf("src/main/java/example/Service.java")
            )

            assertFalse(result.evidence.citations.any { it.id == "stale.evidence" })
            assertEquals(current.snapshotDigest, result.evidence.repositoryStateDigest)
            assertEquals(current.repositoryCommit, result.evidence.repositoryCommit)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun invalidatesCacheWhenProjectConfigChangesWithoutChangingSourceDigest() = runBlocking {
        val root = gitFixture()
        try {
            val before = currentState(root)
            writeCachedInputs(root, before.snapshotDigest, before.snapshotDigest)
            root.resolve(".vericore.json").writeText("""{"excludePaths":["generated"]}""")
            val after = currentState(root)

            assertEquals(before.repositoryCommit, after.repositoryCommit)
            assertEquals(before.snapshotDigest, after.snapshotDigest)
            assertTrue(after.changedPaths.contains(".vericore.json"))

            val result = EngineeringPreparation.prepare(
                root.path,
                "validate the service change",
                listOf("src/main/java/example/Service.java")
            )

            assertFalse(result.evidence.citations.any { it.id == "stale.evidence" })
            assertEquals(after.snapshotDigest, result.evidence.repositoryStateDigest)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun gitFixture(): File {
        val root = Files.createTempDirectory("vericore-preparation-freshness-").toFile()
        root.resolve("pom.xml").writeText(
            """<project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>fixture</groupId>
              <artifactId>preparation-freshness</artifactId>
              <version>1.0</version>
            </project>""".trimIndent()
        )
        root.resolve(".vericore.json").writeText("""{"excludePaths":["build"]}""")
        writeSource(root, "before")
        Git.init().setDirectory(root).call().use { git ->
            git.add().addFilepattern(".").call()
            git.commit()
                .setMessage("initial source state")
                .setAuthor("Vericore Test", "vericore-test@example.invalid")
                .setCommitter("Vericore Test", "vericore-test@example.invalid")
                .call()
        }
        return root
    }

    private fun writeSource(root: File, value: String) {
        root.resolve("src/main/java/example/Service.java").apply {
            parentFile.mkdirs()
            writeText("package example; class Service { String value() { return \"$value\"; } }")
        }
    }

    private data class RepositoryStateFixture(
        val repositoryCommit: String,
        val snapshotDigest: String
    )

    private fun currentState(root: File): RepositoryStateFixture {
        val commit = RepositoryState.head(root.path) ?: error("Git HEAD is missing from the fixture")
        val config = ConfigLoader.loadForRepository(root.path)
        val context = EngineeringContextEngine.snapshot(root, RepositoryScanner(config))
        return RepositoryStateFixture(commit, context.snapshotDigest)
    }

    private fun writeCachedInputs(root: File, snapshotDigest: String, evidenceDigest: String) {
        val source = root.resolve("src/main/java/example/Service.java")
        val head = RepositoryState.head(root.path) ?: error("Git HEAD is missing from the fixture")
        val snapshot = AnalysisSnapshot(
            schemaVersion = ANALYSIS_SCHEMA_VERSION,
            repository = RepositorySnapshot(
                path = root.canonicalPath,
                analyzedAtEpochMillis = 1L,
                languages = listOf("Java"),
                repositoryCommit = head,
                repositoryStateDigest = snapshotDigest
            ),
            metrics = AnalysisMetrics(totalFiles = 1, totalNodes = 1, totalEdges = 0, cycleDetected = false),
            files = listOf(
                FileSnapshot(
                    path = source.canonicalPath,
                    packageName = "example",
                    importCount = 0,
                    churn = 0,
                    authors = emptyList(),
                    pageRank = 0.0,
                    dependents = 0,
                    dependencies = 0
                )
            ),
            hotspots = emptyList(),
            architecture = ArchitectureSnapshot(hasCycles = false, packageCount = 1, crossPackageEdges = 0)
        )
        val evidence = GroundedEvidence(
            schemaVersion = GROUNDED_EVIDENCE_SCHEMA_VERSION,
            citations = listOf(
                EvidenceCitation(
                    id = "stale.evidence",
                    type = "test-evidence",
                    path = "src/main/java/example/Service.java",
                    detail = "This citation comes from a stale cached evidence artifact."
                )
            ),
            repositoryCommit = head,
            repositoryStateDigest = evidenceDigest,
            analysisSchemaVersion = snapshot.schemaVersion
        )
        root.resolve("output").mkdirs()
        root.resolve("output/analysis-snapshot.json").writeText(json.encodeToString(snapshot))
        root.resolve("output/grounded-evidence.json").writeText(json.encodeToString(evidence))
    }
}
