package com.vericore.core.workflow

import com.vericore.core.ai.GroundedEvidence
import com.vericore.core.planner.BuildSystem
import com.vericore.core.planner.ChangeSpecification
import com.vericore.core.planner.CriterionEvidenceBinding
import com.vericore.core.planner.CriterionEvidenceRule
import com.vericore.core.planner.EngineeringPlan
import com.vericore.core.planner.EngineeringPlanRequest
import com.vericore.core.planner.EngineeringPlanner
import com.vericore.core.planner.VerificationEvidenceIds
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VerificationCommandOrderRegressionTest {

    private val planner = EngineeringPlanner()

    @Test
    fun `gradle planned command sequence survives contract creation`() {
        val repo = Files.createTempDirectory("vericore-gradle-order-").toFile()
        try {
            File(repo, "build.gradle.kts").writeText("plugins {}\n")
            createMockGradlew(repo)
            val plan = planner.plan(
                EngineeringPlanRequest(
                    changeSummary = "Update Gradle change validation",
                    changedPaths = listOf("src/main/kotlin/example/Target.kt"),
                    evidence = GroundedEvidence(citations = emptyList()),
                    repositoryPath = repo.path
                )
            )

            assertEquals(BuildSystem.GRADLE, plan.buildSystem)
            assertEquals(2, plan.verificationCommands.size)
            assertTrue(plan.verificationCommands[0].contains("clean test"))
            assertTrue(plan.verificationCommands[1].contains("build"))

            val contract = AgentChangeContract.fromPlan(
                plan = plan,
                repositoryPath = repo.path,
                preparedHead = "test-head",
                baselineManifest = CandidateStateSnapshotter.captureManifest(repo)
            )

            assertEquals("3.2", contract.schemaVersion, "New prepared contracts must use schema version 3.2")
            assertEquals(
                plan.verificationCommands,
                contract.verificationCommands,
                "AgentChangeContract must preserve the planner's verification command order without sorting"
            )
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `maven planned command sequence survives contract creation`() {
        val repo = Files.createTempDirectory("vericore-maven-order-").toFile()
        try {
            File(repo, "pom.xml").writeText("<project/>\n")
            createMockMvnw(repo)
            val plan = planner.plan(
                EngineeringPlanRequest(
                    changeSummary = "Update Maven change validation",
                    changedPaths = listOf("src/main/java/example/Target.java"),
                    evidence = GroundedEvidence(citations = emptyList()),
                    repositoryPath = repo.path
                )
            )

            assertEquals(BuildSystem.MAVEN, plan.buildSystem)
            assertEquals(2, plan.verificationCommands.size)
            assertTrue(plan.verificationCommands[0].contains("-B test"))
            assertTrue(plan.verificationCommands[1].contains("-B package"))

            val contract = AgentChangeContract.fromPlan(
                plan = plan,
                repositoryPath = repo.path,
                preparedHead = "test-head",
                baselineManifest = CandidateStateSnapshotter.captureManifest(repo)
            )

            assertEquals("3.2", contract.schemaVersion, "New prepared contracts must use schema version 3.2")
            assertEquals(
                plan.verificationCommands,
                contract.verificationCommands,
                "AgentChangeContract must preserve the planner's verification command order without sorting"
            )
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `failing test command bound to verification-command-001 correctly attributes failure`() = runBlocking {
        val repo = createGitFixture(failOnExactArg = "-B test")
        try {
            val plannedPath = "src/main/java/example/Target.java"
            val target = File(repo, plannedPath)
            val specification = ChangeSpecification(
                requiredEvidence = listOf("Unit test command succeeds"),
                evidenceBindings = listOf(
                    CriterionEvidenceBinding(
                        criterionId = "required-evidence-001",
                        evidenceIds = listOf(VerificationEvidenceIds.command(1)),
                        rule = CriterionEvidenceRule.ALL_BOUND_COMMANDS_SUCCEEDED
                    )
                )
            )

            val prep = EngineeringPreparation.prepare(
                repoPath = repo.path,
                changeSummary = "Update Target safely",
                plannedPaths = listOf(plannedPath),
                specification = specification
            )

            assertEquals("3.2", prep.contract.schemaVersion)

            // Make the planned mutation.
            target.writeText("package example;\nclass Target { String value() { return \"updated\"; } }\n")

            val result = EngineeringVerification.verify(repo.path, prep.plan, prep.contract)

            // Verify mock wrapper execution log in the generated output path.
            val executionLog = File(repo, "output/verification-executed.log")
            assertTrue(executionLog.isFile, "Verification commands should have written an execution log")
            val executedLines = executionLog.readLines().map(String::trim)
            assertEquals(2, executedLines.size, "Both verification commands must be executed")
            assertEquals("-B test", executedLines[0], "First executed command must be the test command")
            assertEquals("-B package -DskipTests", executedLines[1], "Second executed command must be the package command")

            // Assert exact command outcomes: package succeeded, test failed.
            val packageResult = result.verification.commands.single { it.command.contains("-B package") }
            val testResult = result.verification.commands.single { it.command.contains("-B test") }
            assertEquals(0, packageResult.exitCode, "Package command must exit 0")
            assertNotEquals(0, testResult.exitCode, "Test command must exit non-zero")

            // The overall status must FAIL because the test command exited non-zero.
            assertEquals(SafetyStatus.FAIL, result.status, "Overall verification status must be FAIL when a command fails")

            // Criterion bound to verification-command-001 must FAIL because the planned test command failed.
            val criterion = result.specificationEvaluation?.criteria?.single { it.id == "required-evidence-001" }
            assertEquals(
                CriterionEvaluationStatus.FAIL,
                criterion?.status,
                "Criterion bound to verification-command-001 must FAIL when the test command fails, but was: ${criterion?.status}"
            )
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `passing control - verification passes when all commands and criteria succeed`() = runBlocking {
        val repo = createGitFixture(failOnExactArg = null)
        try {
            val plannedPath = "src/main/java/example/Target.java"
            val target = File(repo, plannedPath)
            val specification = ChangeSpecification(
                requiredEvidence = listOf("Unit test command succeeds"),
                evidenceBindings = listOf(
                    CriterionEvidenceBinding(
                        criterionId = "required-evidence-001",
                        evidenceIds = listOf(VerificationEvidenceIds.command(1)),
                        rule = CriterionEvidenceRule.ALL_BOUND_COMMANDS_SUCCEEDED
                    )
                )
            )

            val prep = EngineeringPreparation.prepare(
                repoPath = repo.path,
                changeSummary = "Update Target safely",
                plannedPaths = listOf(plannedPath),
                specification = specification
            )

            assertEquals("3.2", prep.contract.schemaVersion)

            // Make the planned mutation.
            target.writeText("package example;\nclass Target { String value() { return \"updated\"; } }\n")

            val result = EngineeringVerification.verify(repo.path, prep.plan, prep.contract)

            // In control fixture, all commands pass and the candidate is valid.
            assertEquals(SafetyStatus.PASS, result.status)
            assertTrue(result.verification.allCommandsPassed)
            assertEquals(CriterionEvaluationStatus.PASS, result.specificationEvaluation?.status)
            val criterion = result.specificationEvaluation?.criteria?.single { it.id == "required-evidence-001" }
            assertEquals(CriterionEvaluationStatus.PASS, criterion?.status)

            // Verify actual execution-log order.
            val executionLog = File(repo, "output/verification-executed.log")
            assertTrue(executionLog.isFile, "Execution log must be generated")
            val executedLines = executionLog.readLines().map(String::trim)
            assertEquals(2, executedLines.size, "Both commands must be logged in execution log")
            assertEquals("-B test", executedLines[0], "First executed command in log must be the planned test command")
            assertEquals("-B package -DskipTests", executedLines[1], "Second executed command in log must be the planned package command")
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `order-only mutation of verification commands must invalidate contract fingerprint`() {
        val repo = Files.createTempDirectory("vericore-fingerprint-order-").toFile()
        try {
            File(repo, "pom.xml").writeText("<project/>\n")
            val cmdA = "cd /d \"${repo.canonicalPath}\" && mvnw.cmd -B test"
            val cmdB = "cd /d \"${repo.canonicalPath}\" && mvnw.cmd -B package -DskipTests"

            val plan = EngineeringPlan(
                changeSummary = "Order mutation test",
                repository = repo.canonicalPath,
                affectedComponents = listOf("src/Target.java"),
                plannedPaths = listOf("src/Target.java"),
                concerns = emptyList(),
                riskLevel = com.vericore.core.planner.RiskLevel.LOW,
                steps = emptyList(),
                verificationCommands = listOf(cmdA, cmdB),
                evidenceIds = listOf("e1"),
                uncertainties = emptyList()
            )

            val contract = AgentChangeContract.fromPlan(
                plan = plan,
                repositoryPath = repo.canonicalPath,
                preparedHead = "head",
                baselineManifest = CandidateStateSnapshotter.captureManifest(repo)
            )
            val reorderedCommands = contract.verificationCommands.reversed()
            assertNotEquals(contract.verificationCommands, reorderedCommands, "Reversed command list must differ from stored order")

            val reorderedContract = contract.copy(verificationCommands = reorderedCommands)
            assertNotEquals(
                contract.fingerprint,
                AgentChangeContract.fingerprintFor(reorderedContract),
                "Order-only mutation of verificationCommands must change the contract fingerprint"
            )
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `repeated verification commands preserve exact ordered sequence and repetition alters fingerprint`() {
        val repo = Files.createTempDirectory("vericore-repeated-commands-").toFile()
        try {
            val cmd1 = "cd /d \"${repo.canonicalPath}\" && gradlew.bat clean test"
            val cmd2 = "cd /d \"${repo.canonicalPath}\" && gradlew.bat build"

            val plan = EngineeringPlan(
                changeSummary = "Repeated commands test",
                repository = repo.canonicalPath,
                affectedComponents = listOf("src/Target.kt"),
                plannedPaths = listOf("src/Target.kt"),
                concerns = emptyList(),
                riskLevel = com.vericore.core.planner.RiskLevel.LOW,
                steps = emptyList(),
                verificationCommands = listOf(cmd1, cmd2, cmd1),
                evidenceIds = listOf("e1"),
                uncertainties = emptyList()
            )

            val contract = AgentChangeContract.fromPlan(
                plan = plan,
                repositoryPath = repo.canonicalPath,
                preparedHead = "head",
                baselineManifest = CandidateStateSnapshotter.captureManifest(repo)
            )

            assertEquals(
                listOf(cmd1, cmd2, cmd1),
                contract.verificationCommands,
                "AgentChangeContract must preserve the exact ordered sequence including repeated commands"
            )

            val withoutRepetition = contract.copy(verificationCommands = listOf(cmd1, cmd2))
            assertNotEquals(
                contract.fingerprint,
                AgentChangeContract.fingerprintFor(withoutRepetition),
                "Inserting or removing a repeated command must change the contract fingerprint"
            )
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `legacy contract schema versions require re-preparation`() = runBlocking {
        val repo = createGitFixture(failOnExactArg = null)
        try {
            val plannedPath = "src/main/java/example/Target.java"
            val target = File(repo, plannedPath)
            val prep = EngineeringPreparation.prepare(
                repoPath = repo.path,
                changeSummary = "Legacy contract test",
                plannedPaths = listOf(plannedPath)
            )
            target.writeText("package example;\nclass Target { String value() { return \"updated\"; } }\n")

            // Simulate legacy 3.1 contract.
            val legacyContract = prep.contract.copy(schemaVersion = "3.1")
            val legacyFingerprint = AgentChangeContract.fingerprintFor(legacyContract)
            val preparedLegacyContract = legacyContract.copy(fingerprint = legacyFingerprint)
            val legacyPlan = prep.plan.copy(contractFingerprint = legacyFingerprint)

            val result = EngineeringVerification.verify(repo.path, legacyPlan, preparedLegacyContract)

            assertEquals(SafetyStatus.FAIL, result.status)
            assertTrue(
                result.reasons.any { it.contains("legacy command-ordering semantics") },
                "Legacy contract schema versions must produce an actionable re-preparation requirement, but was: ${result.reasons}"
            )
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `unsupported contract schema versions are rejected explicitly`() = runBlocking {
        val repo = createGitFixture(failOnExactArg = null)
        try {
            val plannedPath = "src/main/java/example/Target.java"
            val target = File(repo, plannedPath)
            val prep = EngineeringPreparation.prepare(
                repoPath = repo.path,
                changeSummary = "Unsupported version test",
                plannedPaths = listOf(plannedPath)
            )
            target.writeText("package example;\nclass Target { String value() { return \"updated\"; } }\n")

            val unsupportedContract = prep.contract.copy(schemaVersion = "9.9")
            val result = EngineeringVerification.verify(repo.path, prep.plan, unsupportedContract)

            assertEquals(SafetyStatus.FAIL, result.status)
            assertTrue(
                result.reasons.any { it.contains("unsupported") },
                "Unsupported schema versions must be explicitly rejected, but was: ${result.reasons}"
            )
        } finally {
            repo.deleteRecursively()
        }
    }

    private fun createGitFixture(failOnExactArg: String?): File {
        val repo = Files.createTempDirectory("vericore-fixture-").toFile()
        File(repo, "pom.xml").writeText(
            """<project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>fixture</groupId>
              <artifactId>order-regression-fixture</artifactId>
              <version>1.0</version>
            </project>""".trimIndent()
        )
        val source = File(repo, "src/main/java/example/Target.java")
        source.parentFile.mkdirs()
        source.writeText("package example;\nclass Target { String value() { return \"initial\"; } }\n")
        createMockMvnw(repo, failOnExactArg)

        Git.init().setDirectory(repo).call().use { git ->
            git.add().addFilepattern(".").call()
            git.commit()
                .setMessage("initial fixture")
                .setAuthor("Vericore Test", "test@example.invalid")
                .setCommitter("Vericore Test", "test@example.invalid")
                .call()
        }
        return repo
    }

    private fun createMockMvnw(repo: File, failOnExactArg: String? = null) {
        val bat = File(repo, "mvnw.cmd")
        bat.writeText(
            buildString {
                appendLine("@echo off")
                appendLine("if not exist \"output\" mkdir output")
                appendLine("echo %*>> output\\verification-executed.log")
                if (failOnExactArg != null) {
                    appendLine("if \"%*\"==\"$failOnExactArg\" exit /b 1")
                }
                appendLine("exit /b 0")
            }
        )

        val sh = File(repo, "mvnw")
        sh.writeText(
            buildString {
                appendLine("#!/bin/sh")
                appendLine("mkdir -p output")
                appendLine("printf '%s\\n' \"$*\" >> output/verification-executed.log")
                if (failOnExactArg != null) {
                    appendLine("if [ \"$*\" = \"$failOnExactArg\" ]; then")
                    appendLine("    exit 1")
                    appendLine("fi")
                }
                appendLine("exit 0")
            }
        )
        sh.setExecutable(true)
    }

    private fun createMockGradlew(repo: File) {
        val bat = File(repo, "gradlew.bat")
        bat.writeText(
            """
            @echo off
            if not exist "output" mkdir output
            echo %*>> output\verification-executed.log
            exit /b 0
            """.trimIndent() + "\n"
        )
        val sh = File(repo, "gradlew")
        sh.writeText(
            """
            #!/bin/sh
            mkdir -p output
            printf '%s\n' "$*" >> output/verification-executed.log
            exit 0
            """.trimIndent() + "\n"
        )
        sh.setExecutable(true)
    }
}
