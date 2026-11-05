package com.vericore.cli

import com.github.ajalt.clikt.testing.test
import com.vericore.core.intelligence.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.eclipse.jgit.api.Git

class CommandMatrixComprehensiveTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun cliPath(file: File): String = file.absolutePath.replace('\\', '/')

    private fun initGitRepo(dir: File): Git {
        dir.mkdirs()
        val git = Git.init().setDirectory(dir).call()
        val readme = File(dir, "README.md")
        readme.writeText("# Test Repo\n")
        git.add().addFilepattern(".").call()
        git.commit().setMessage("Initial commit").call()
        return git
    }

    @Test
    fun `testArchitectureDriftScenarios - handles drift, identical baseline, and missing baseline`() {
        val root = Files.createTempDirectory("vericore-test-drift").toFile()
        try {
            val git = initGitRepo(root)
            val srcDir = File(root, "src/main/kotlin/com/example").apply { mkdirs() }
            File(srcDir, "A.kt").writeText("package com.example\nclass A\n")
            git.add().addFilepattern(".").call()
            git.commit().setMessage("Add A").call()

            // 1. Missing baseline throws IllegalArgumentException
            assertFailsWith<IllegalArgumentException> {
                ArchitectureDriftCommand().test(listOf(cliPath(root), "--baseline", cliPath(File(root, "non-existent-baseline.json"))))
            }

            // 2. Generate baseline architecture artifact
            val archResult = ArchitectureCommand().test(listOf(cliPath(root), "--json"))
            assertEquals(0, archResult.statusCode)
            val baselineFile = File(root, "output/architecture.json")
            assertTrue(baselineFile.isFile)

            // 3. No drift against identical architecture baseline
            val noDriftResult = ArchitectureDriftCommand().test(listOf(cliPath(root), "--baseline", cliPath(baselineFile), "--json"))
            assertEquals(0, noDriftResult.statusCode)
            val noDriftEnvelope = json.parseToJsonElement(noDriftResult.stdout).jsonObject
            assertEquals("PASS", noDriftEnvelope["status"]?.jsonPrimitive?.content)

            // Human mode check for identical snapshot
            val humanNoDrift = ArchitectureDriftCommand().test(listOf(cliPath(root), "--baseline", cliPath(baselineFile)))
            assertEquals(0, humanNoDrift.statusCode)
            assertTrue(humanNoDrift.stdout.contains("Architecture Drift"))

            // 4. Actual architecture change detects drift
            File(srcDir, "B.kt").writeText("package com.example\nimport com.example.A\nclass B : A()\n")
            git.add().addFilepattern(".").call()
            git.commit().setMessage("Add B depending on A").call()

            val driftResult = ArchitectureDriftCommand().test(listOf(cliPath(root), "--baseline", cliPath(baselineFile), "--json"))
            assertEquals(0, driftResult.statusCode)
            val driftEnvelope = json.parseToJsonElement(driftResult.stdout).jsonObject
            assertEquals("WARNING", driftEnvelope["status"]?.jsonPrimitive?.content)
            assertTrue(driftEnvelope["summary"]?.jsonPrimitive?.content?.contains("Architecture drift:") == true)

            // 5. Corrupt baseline file throws exception
            val corruptBaseline = File(root, "corrupt-baseline.json").apply { writeText("invalid json content") }
            assertFailsWith<Exception> {
                ArchitectureDriftCommand().test(listOf(cliPath(root), "--baseline", cliPath(corruptBaseline)))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `testArchitectureContractScenarios - evaluates contract passing and violation`() {
        val root = Files.createTempDirectory("vericore-test-contract").toFile()
        try {
            initGitRepo(root)
            val srcDir = File(root, "src/main/kotlin/com/example").apply { mkdirs() }
            File(srcDir, "Service.kt").writeText("package com.example\nclass Service\n")

            // Run analyze to get graph
            ImprovedAnalyzeCommand().test(listOf(cliPath(root)))

            // Passing contract (default empty contract)
            val passResult = ArchitectureContractCommand().test(listOf(cliPath(root), "--json"))
            assertEquals(0, passResult.statusCode)
            val passEnvelope = json.parseToJsonElement(passResult.stdout).jsonObject
            assertEquals("PASS", passEnvelope["status"]?.jsonPrimitive?.content)

            // Human mode invocation on clean repo
            val humanPass = ArchitectureContractCommand().test(listOf(cliPath(root)))
            assertEquals(0, humanPass.statusCode)
            assertTrue(humanPass.stdout.contains("Architecture Contract"))

            // Genuine violating contract (require a non-existent rule or maxFindings < 0)
            val contractFile = File(root, ".vericore-architecture-contract.json")
            contractFile.writeText("""
                {
                    "schemaVersion": "1.0",
                    "maxFindings": -1
                }
            """.trimIndent())

            val ex = assertFailsWith<com.vericore.core.exceptions.ValidationException> {
                ArchitectureContractCommand().test(listOf(cliPath(root), "--contract", cliPath(contractFile)))
            }
            assertTrue(ex.message?.contains("Architecture contract failed") == true)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `testContextDiffScenarios - compares different and identical snapshots`() {
        val root = Files.createTempDirectory("vericore-test-context-diff").toFile()
        try {
            val snapshotA = EngineeringContextSnapshot(
                schemaVersion = "1.0",
                repositoryCommit = "commit1",
                files = listOf(ContextFile("A.kt", "hash1", 50L)),
                totalFiles = 1,
                totalBytes = 50L,
                languages = listOf("Kotlin"),
                dirty = false,
                changedPaths = emptyList(),
                snapshotDigest = "digestA"
            )
            val snapshotB = EngineeringContextSnapshot(
                schemaVersion = "1.0",
                repositoryCommit = "commit2",
                files = listOf(ContextFile("A.kt", "hash1", 50L), ContextFile("B.kt", "hash2", 50L)),
                totalFiles = 2,
                totalBytes = 100L,
                languages = listOf("Kotlin"),
                dirty = false,
                changedPaths = emptyList(),
                snapshotDigest = "digestB"
            )
            val fileA = File(root, "snapA.json").apply { writeText(json.encodeToString(snapshotA)) }
            val fileB = File(root, "snapB.json").apply { writeText(json.encodeToString(snapshotB)) }

            // Diff between two different snapshots in JSON mode
            val diffJson = EngineeringContextDiffCommand().test(listOf(cliPath(fileA), cliPath(fileB), "--json"))
            assertEquals(0, diffJson.statusCode)
            val envelope = json.parseToJsonElement(diffJson.stdout).jsonObject
            assertEquals("context-diff", envelope["command"]?.jsonPrimitive?.content)
            assertEquals("WARNING", envelope["status"]?.jsonPrimitive?.content)

            // Diff between identical snapshots
            val identicalJson = EngineeringContextDiffCommand().test(listOf(cliPath(fileA), cliPath(fileA), "--json"))
            assertEquals(0, identicalJson.statusCode)
            val identicalEnvelope = json.parseToJsonElement(identicalJson.stdout).jsonObject
            assertEquals("PASS", identicalEnvelope["status"]?.jsonPrimitive?.content)

            // Missing snapshot error
            assertFailsWith<Exception> {
                EngineeringContextDiffCommand().test(listOf(cliPath(File(root, "missing.json")), cliPath(fileA)))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `testEvidenceGraphScenarios - requires snapshot and emits graph envelope`() {
        val root = Files.createTempDirectory("vericore-test-evidence-graph").toFile()
        try {
            initGitRepo(root)
            val srcDir = File(root, "src/main/kotlin/com/example").apply { mkdirs() }
            File(srcDir, "App.kt").writeText("package com.example\nclass App\n")

            // 1. Missing snapshot fails
            assertFailsWith<IllegalArgumentException> {
                EvidenceGraphCommand().test(listOf(cliPath(root)))
            }

            // 2. Generate snapshot
            ImprovedAnalyzeCommand().test(listOf(cliPath(root)))

            // 3. Evidence graph human mode
            val humanResult = EvidenceGraphCommand().test(listOf(cliPath(root)))
            assertEquals(0, humanResult.statusCode)
            assertTrue(humanResult.stdout.contains("Semantic Evidence Graph"))

            // 4. Evidence graph JSON mode
            val jsonResult = EvidenceGraphCommand().test(listOf(cliPath(root), "--json"))
            assertEquals(0, jsonResult.statusCode)
            val envelope = json.parseToJsonElement(jsonResult.stdout).jsonObject
            assertEquals("evidence-graph", envelope["command"]?.jsonPrimitive?.content)
            assertEquals("PASS", envelope["status"]?.jsonPrimitive?.content)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `testRealityScenarios - produces unified reality summary`() {
        val root = Files.createTempDirectory("vericore-test-reality").toFile()
        try {
            initGitRepo(root)
            val srcDir = File(root, "src/main/kotlin/com/example").apply { mkdirs() }
            File(srcDir, "Main.kt").writeText("package com.example\nfun main() {}\n")

            // 1. Missing snapshot fails
            assertFailsWith<IllegalArgumentException> {
                RealityCommand().test(listOf(cliPath(root)))
            }

            // 2. Generate snapshot
            ImprovedAnalyzeCommand().test(listOf(cliPath(root)))

            // 3. Reality human mode
            val humanResult = RealityCommand().test(listOf(cliPath(root)))
            assertEquals(0, humanResult.statusCode)
            assertTrue(humanResult.stdout.contains("Engineering Reality"))

            // 4. Reality JSON mode
            val jsonResult = RealityCommand().test(listOf(cliPath(root), "--json"))
            assertEquals(0, jsonResult.statusCode)
            val envelope = json.parseToJsonElement(jsonResult.stdout).jsonObject
            assertEquals("reality", envelope["command"]?.jsonPrimitive?.content)
            assertEquals("PASS", envelope["status"]?.jsonPrimitive?.content)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `testPRIntelligenceAndReviewScenarios - clean working tree returns NO_CHANGES`() {
        val root = Files.createTempDirectory("vericore-test-pr-intel").toFile()
        try {
            val git = initGitRepo(root)
            val initialCommit = git.repository.resolve("HEAD").name

            // Clean working tree pr-intelligence in JSON mode
            val prIntelJson = PRIntelligenceCommand().test(listOf(cliPath(root), "--json"))
            assertEquals(0, prIntelJson.statusCode)
            val prEnvelope = json.parseToJsonElement(prIntelJson.stdout).jsonObject
            assertEquals("pr-intelligence", prEnvelope["command"]?.jsonPrimitive?.content)
            assertEquals("NO_CHANGES", prEnvelope["status"]?.jsonPrimitive?.content)

            // Clean working tree review in JSON mode
            val reviewJson = ReviewCommand().test(listOf("--path", cliPath(root), "--json"))
            assertEquals(0, reviewJson.statusCode)
            val reviewEnvelope = json.parseToJsonElement(reviewJson.stdout).jsonObject
            assertEquals("review", reviewEnvelope["command"]?.jsonPrimitive?.content)
            assertEquals("NO_CHANGES", reviewEnvelope["status"]?.jsonPrimitive?.content)

            // Now make a commit on a branch
            git.branchCreate().setName("feature").call()
            git.checkout().setName("feature").call()
            val newFile = File(root, "src/Feature.kt").apply { parentFile.mkdirs(); writeText("class Feature\n") }
            git.add().addFilepattern(".").call()
            val featureCommit = git.commit().setMessage("Add feature").call().name

            // Switch back to master so working tree is clean
            git.checkout().setName("master").call()
            assertFalse(newFile.exists())

            // Compare base/head when working tree is clean
            val branchDiffJson = PRIntelligenceCommand().test(listOf(cliPath(root), "--base", initialCommit, "--head", featureCommit, "--json"))
            assertEquals(0, branchDiffJson.statusCode)
            val branchEnvelope = json.parseToJsonElement(branchDiffJson.stdout).jsonObject
            // Should NOT be NO_CHANGES because revisions differ!
            assertEquals("PASS", branchEnvelope["status"]?.jsonPrimitive?.content)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `testPlanAndPrepareScenarios - read-only scope vs mutation scope`() {
        val root = Files.createTempDirectory("vericore-test-plan").toFile()
        try {
            initGitRepo(root)
            val srcDir = File(root, "src/main/kotlin/com/example").apply { mkdirs() }
            File(srcDir, "App.kt").writeText("package com.example\nclass App\n")

            // Analyze and generate grounded evidence first
            ImprovedAnalyzeCommand().test(listOf(cliPath(root)))
            RepositoryQACommand().test(listOf("What is this architecture?", "--path", cliPath(root), "--evidence-output", cliPath(File(root, "output/grounded-evidence.json"))))

            // 1. Engineering plan in JSON mode
            val planJson = EngineeringPlanCommand().test(listOf("Understand the application architecture", "--repository", cliPath(root), "--json"))
            assertEquals(0, planJson.statusCode)
            val planEnvelope = json.parseToJsonElement(planJson.stdout).jsonObject
            assertEquals("plan", planEnvelope["command"]?.jsonPrimitive?.content)
            assertEquals("PASS", planEnvelope["status"]?.jsonPrimitive?.content)

            // Human mode plan output
            val planHuman = EngineeringPlanCommand().test(listOf("Understand the application architecture", "--repository", cliPath(root)))
            assertEquals(0, planHuman.statusCode)
            assertTrue(planHuman.stdout.contains("Engineering Plan"))
            assertTrue(planHuman.stdout.contains("None (read-only / repository understanding)"))

            // 2. Prepare command in JSON mode
            val prepareJson = PrepareCommand().test(listOf("Understand the application architecture", "--path", cliPath(root), "--json"))
            assertEquals(0, prepareJson.statusCode)
            val prepEnvelope = json.parseToJsonElement(prepareJson.stdout).jsonObject
            assertEquals("prepare", prepEnvelope["command"]?.jsonPrimitive?.content)
            assertEquals("PASS", prepEnvelope["status"]?.jsonPrimitive?.content)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `testVerifyScenarios - clean verification passes, contract tamper fails with explicit reason`() {
        val root = Files.createTempDirectory("vericore-test-verify").toFile()
        try {
            initGitRepo(root)
            val srcDir = File(root, "src/main/kotlin/com/example").apply { mkdirs() }
            File(srcDir, "App.kt").writeText("package com.example\nclass App\n")

            // Analyze and prepare
            ImprovedAnalyzeCommand().test(listOf(cliPath(root)))
            PrepareCommand().test(listOf("Refactor App", "--path", cliPath(root)))

            // Clean verification with contract-only in JSON mode
            val verifyJson = VerifyCommand().test(listOf("--path", cliPath(root), "--contract-only", "--json"))
            assertEquals(0, verifyJson.statusCode)
            val envelope = json.parseToJsonElement(verifyJson.stdout).jsonObject
            assertEquals("verify", envelope["command"]?.jsonPrimitive?.content)
            assertEquals("PASS", envelope["status"]?.jsonPrimitive?.content)

            // Tamper contract file
            val contractFile = File(root, "output/agent-change-contract.json")
            assertTrue(contractFile.isFile)
            val contractObj = json.parseToJsonElement(contractFile.readText()).jsonObject
            // Change fingerprint to simulate tampering
            val tampered = contractFile.readText().replace(contractObj["fingerprint"]?.jsonPrimitive?.content.orEmpty(), "tampered-hash-12345")
            contractFile.writeText(tampered)

            // Verification must fail with non-empty reasons
            assertFailsWith<Exception> {
                VerifyCommand().test(listOf("--path", cliPath(root), "--contract-only"))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `testReportScenarios - generates report or fails when session missing`() {
        val root = Files.createTempDirectory("vericore-test-report").toFile()
        try {
            initGitRepo(root)
            // Report on repository without sessions fails cleanly
            assertFailsWith<IllegalArgumentException> {
                ReportCommand().test(listOf("--path", cliPath(root)))
            }

            // Run analysis to generate session
            val srcDir = File(root, "src/main/kotlin/com/example").apply { mkdirs() }
            File(srcDir, "App.kt").writeText("package com.example\nclass App\n")
            ImprovedAnalyzeCommand().test(listOf(cliPath(root)))

            // Report generation succeeds
            val reportResult = ReportCommand().test(listOf("--path", cliPath(root)))
            assertEquals(0, reportResult.statusCode)
            assertTrue(reportResult.stdout.contains("REPORT GENERATED"))

            // JSON mode
            val reportJson = ReportCommand().test(listOf("--path", cliPath(root), "--json"))
            assertEquals(0, reportJson.statusCode)
            val envelope = json.parseToJsonElement(reportJson.stdout).jsonObject
            assertEquals("report", envelope["command"]?.jsonPrimitive?.content)
            assertEquals("PASS", envelope["status"]?.jsonPrimitive?.content)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `testStandardizedOutputOption - format json with output writes identical envelope to destination file`() {
        val root = Files.createTempDirectory("vericore-test-output-opt").toFile()
        try {
            initGitRepo(root)
            val srcDir = File(root, "src/main/kotlin/com/example").apply { mkdirs() }
            File(srcDir, "App.kt").writeText("package com.example\nclass App\n")

            val outJsonFile = File(root, "custom-output.json")
            val result = ImprovedAnalyzeCommand().test(listOf(cliPath(root), "--format", "json", "--output", cliPath(outJsonFile)))
            assertEquals(0, result.statusCode)
            assertTrue(outJsonFile.isFile)

            val stdoutEnvelope = json.parseToJsonElement(result.stdout).jsonObject
            val fileEnvelope = json.parseToJsonElement(outJsonFile.readText()).jsonObject
            assertEquals(stdoutEnvelope["schemaVersion"]?.jsonPrimitive?.content, fileEnvelope["schemaVersion"]?.jsonPrimitive?.content)
            assertEquals(stdoutEnvelope["command"]?.jsonPrimitive?.content, fileEnvelope["command"]?.jsonPrimitive?.content)
            assertEquals(stdoutEnvelope["status"]?.jsonPrimitive?.content, fileEnvelope["status"]?.jsonPrimitive?.content)
            assertEquals(stdoutEnvelope["summary"]?.jsonPrimitive?.content, fileEnvelope["summary"]?.jsonPrimitive?.content)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `testDoctorSingleFailureCount - Gemini validation failure counts as exactly one failure`() {
        val root = Files.createTempDirectory("vericore-test-doctor-count").toFile()
        try {
            initGitRepo(root)
            val configFile = File(root, ".vericore.json")
            configFile.writeText("""
                {
                    "ai": {
                        "enabled": true,
                        "provider": "gemini",
                        "apiKey": "invalid-test-key-12345"
                    }
                }
            """.trimIndent())

            val ex = assertFailsWith<com.vericore.core.exceptions.ConfigurationException> {
                DoctorCommand().test(listOf("--path", cliPath(root), "--json"))
            }
            assertTrue(ex.message?.contains("1 error(s)") == true, "Gemini failure must count as 1 error, not 2: ${ex.message}")
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `testVerifyStatusAndExitCodeAgreement`() {
        val root = Files.createTempDirectory("vericore-test-verify-codes").toFile()
        try {
            initGitRepo(root)
            val srcDir = File(root, "src/main/kotlin/com/example").apply { mkdirs() }
            File(srcDir, "App.kt").writeText("package com.example\nclass App\n")

            ImprovedAnalyzeCommand().test(listOf(cliPath(root)))
            PrepareCommand().test(listOf("Refactor App", "--path", cliPath(root)))

            // PASS: exit code is 0 in envelope and command returns 0
            val passResult = VerifyCommand().test(listOf("--path", cliPath(root), "--contract-only", "--json"))
            assertEquals(0, passResult.statusCode)
            val passEnvelope = json.parseToJsonElement(passResult.stdout).jsonObject
            assertEquals("PASS", passEnvelope["status"]?.jsonPrimitive?.content)
            assertEquals("0", passEnvelope["execution"]?.jsonObject?.get("exitCode")?.jsonPrimitive?.content)

            // FAIL: tamper contract fingerprint
            val contractFile = File(root, "output/agent-change-contract.json")
            val contractObj = json.parseToJsonElement(contractFile.readText()).jsonObject
            val tampered = contractFile.readText().replace(contractObj["fingerprint"]?.jsonPrimitive?.content.orEmpty(), "tampered-hash-12345")
            contractFile.writeText(tampered)
            val failEx = assertFailsWith<com.vericore.core.exceptions.ValidationException> {
                VerifyCommand().test(listOf("--path", cliPath(root), "--contract-only", "--json"))
            }
            assertTrue(failEx.message?.contains("Change verification failed") == true)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `testSetupFailureInJsonModeThrowsValidationExceptionWithPureJsonStdout`() {
        val ex = assertFailsWith<com.vericore.core.exceptions.ValidationException> {
            SetupCommand().test(listOf("--json", "--force", "--api-key", "invalid-key-for-test"))
        }
        assertTrue(ex.message?.contains("Gemini validation failed") == true)
    }
}
