package com.vericore.core.workflow

import com.vericore.core.ai.EvidenceCitation
import com.vericore.core.planner.ChangeSpecification
import com.vericore.core.planner.CriterionEvidenceBinding
import com.vericore.core.planner.CriterionEvidenceRule
import com.vericore.core.planner.VerificationEvidenceIds
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ChangeSpecificationEvaluatorTest {
    @Test
    fun `every declared obligation receives an explicit unevaluated result`() {
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            ChangeSpecification(
                requiredBehaviors = listOf("Reject invalid input"),
                forbiddenBehaviors = listOf("Never log secrets"),
                protectedInvariants = listOf("Keep retries idempotent"),
                acceptanceCriteria = listOf("Invalid input returns a client error"),
                requiredEvidence = listOf("Unit test output for invalid input")
            )
        )!!

        assertEquals(CriterionEvaluationStatus.NOT_EVALUATED, evaluation.status)
        assertEquals(5, evaluation.criteria.size)
        assertEquals(0, evaluation.evaluatedCount)
        assertEquals(5, evaluation.notEvaluatedCount)
        assertTrue(evaluation.criteria.all { it.status == CriterionEvaluationStatus.NOT_EVALUATED })
        assertEquals(
            listOf(
                "required-behavior-001",
                "forbidden-behavior-001",
                "protected-invariant-001",
                "acceptance-criterion-001",
                "required-evidence-001"
            ),
            evaluation.criteria.map { it.id }
        )
        assertTrue(evaluation.criteria.all { it.reason.isNotBlank() })
    }

    @Test
    fun `linked free form evidence remains inconclusive`() {
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            ChangeSpecification(
                acceptanceCriteria = listOf("Invalid input returns 400"),
                evidenceBindings = listOf(CriterionEvidenceBinding("acceptance-criterion-001", listOf("file.1")))
            ),
            availableEvidenceIds = setOf("file.1")
        )!!

        assertEquals(CriterionEvaluationStatus.INCONCLUSIVE, evaluation.status)
        assertEquals(CriterionEvaluationStatus.INCONCLUSIVE, evaluation.criteria.single().status)
        assertEquals(listOf("file.1"), evaluation.criteria.single().evidenceIds)
    }

    private fun junitEvidence(
        className: String = "PaymentTest",
        name: String = "rejectsInvalidPayment",
        status: JUnitCaseStatus = JUnitCaseStatus.PASSED
    ) = JUnitTestReportEvidence(
        candidateSha = "0123456789abcdef0123456789abcdef01234567",
        candidateStateDigest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        executionId = "run-test:build/test-results/test/TEST-Payment.xml",
        reportSha256 = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
        status = if (status == JUnitCaseStatus.FAILED) JUnitReportStatus.FAILED else JUnitReportStatus.ALL_PASSED,
        tests = 1,
        passed = if (status == JUnitCaseStatus.PASSED) 1 else 0,
        failed = if (status == JUnitCaseStatus.FAILED) 1 else 0,
        skipped = if (status == JUnitCaseStatus.SKIPPED) 1 else 0,
        cases = listOf(JUnitTestCaseEvidence(className, name, status))
    )

    @Test
    fun `explicitly bound passing JUnit testcase evaluates the criterion and cites report provenance`() {
        val selector = VerificationEvidenceIds.junitTestCase("PaymentTest", "rejectsInvalidPayment")
        val specification = ChangeSpecification(
            acceptanceCriteria = listOf("Invalid payment is rejected"),
            evidenceBindings = listOf(CriterionEvidenceBinding(
                "acceptance-criterion-001", listOf(selector), CriterionEvidenceRule.ALL_BOUND_JUNIT_CASES_PASSED
            ))
        )

        val evaluation = ChangeSpecificationEvaluator.evaluate(
            specification,
            availableEvidenceIds = setOf(selector),
            currentCandidateEvidenceAvailable = true,
            junitTestReports = listOf(junitEvidence())
        )!!

        assertEquals(CriterionEvaluationStatus.PASS, evaluation.status)
        assertTrue(evaluation.criteria.single().reason.contains("run-test:build/test-results/test/TEST-Payment.xml"))
        assertTrue(evaluation.criteria.single().reason.contains("not that the tests fully cover the requirement"))
    }

    @Test
    fun `JUnit selector encoding handles delimiter characters without collisions`() {
        val className = "Payment#Test"
        val testName = "rejects#Payment"
        val selector = VerificationEvidenceIds.junitTestCase(className, testName)
        assertEquals("junit-test-case:Payment%23Test#rejects%23Payment", selector)

        val literalEscapeText = VerificationEvidenceIds.junitTestCase("Payment%23Test", "rejects%23Payment")
        assertEquals("junit-test-case:Payment%2523Test#rejects%2523Payment", literalEscapeText)
        assertTrue(selector != literalEscapeText)

        val evaluation = ChangeSpecificationEvaluator.evaluate(
            ChangeSpecification(
                acceptanceCriteria = listOf("Invalid payment is rejected"),
                evidenceBindings = listOf(CriterionEvidenceBinding(
                    "acceptance-criterion-001",
                    listOf(selector),
                    CriterionEvidenceRule.ALL_BOUND_JUNIT_CASES_PASSED
                ))
            ),
            availableEvidenceIds = setOf(selector),
            currentCandidateEvidenceAvailable = true,
            junitTestReports = listOf(junitEvidence(className = className, name = testName))
        )!!

        assertEquals(CriterionEvaluationStatus.PASS, evaluation.status)
    }

    @Test
    fun `bound failed JUnit testcase fails the criterion`() {
        val selector = VerificationEvidenceIds.junitTestCase("PaymentTest", "rejectsInvalidPayment")
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            ChangeSpecification(
                requiredBehaviors = listOf("Reject invalid payment"),
                evidenceBindings = listOf(CriterionEvidenceBinding(
                    "required-behavior-001", listOf(selector), CriterionEvidenceRule.ALL_BOUND_JUNIT_CASES_PASSED
                ))
            ),
            availableEvidenceIds = setOf(selector),
            currentCandidateEvidenceAvailable = true,
            junitTestReports = listOf(junitEvidence(status = JUnitCaseStatus.FAILED))
        )!!

        assertEquals(CriterionEvaluationStatus.FAIL, evaluation.status)
    }

    @Test
    fun `bound skipped JUnit testcase is inconclusive not passing`() {
        val selector = VerificationEvidenceIds.junitTestCase("PaymentTest", "rejectsInvalidPayment")
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            ChangeSpecification(
                acceptanceCriteria = listOf("Invalid payment is rejected"),
                evidenceBindings = listOf(CriterionEvidenceBinding(
                    "acceptance-criterion-001", listOf(selector), CriterionEvidenceRule.ALL_BOUND_JUNIT_CASES_PASSED
                ))
            ),
            availableEvidenceIds = setOf(selector),
            currentCandidateEvidenceAvailable = true,
            junitTestReports = listOf(junitEvidence(status = JUnitCaseStatus.SKIPPED))
        )!!

        assertEquals(CriterionEvaluationStatus.INCONCLUSIVE, evaluation.status)
    }

    @Test
    fun `ambiguous duplicate JUnit selectors cannot pass`() {
        val selector = VerificationEvidenceIds.junitTestCase("PaymentTest", "rejectsInvalidPayment")
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            ChangeSpecification(
                acceptanceCriteria = listOf("Invalid payment is rejected"),
                evidenceBindings = listOf(CriterionEvidenceBinding(
                    "acceptance-criterion-001", listOf(selector), CriterionEvidenceRule.ALL_BOUND_JUNIT_CASES_PASSED
                ))
            ),
            availableEvidenceIds = setOf(selector),
            currentCandidateEvidenceAvailable = true,
            junitTestReports = listOf(junitEvidence(), junitEvidence())
        )!!

        assertEquals(CriterionEvaluationStatus.INCONCLUSIVE, evaluation.status)
    }

    @Test
    fun `missing JUnit testcase without available selector remains not evaluated`() {
        val selector = VerificationEvidenceIds.junitTestCase("PaymentTest", "rejectsInvalidPayment")
        val specification = ChangeSpecification(
            acceptanceCriteria = listOf("Invalid payment is rejected"),
            evidenceBindings = listOf(CriterionEvidenceBinding(
                "acceptance-criterion-001", listOf(selector), CriterionEvidenceRule.ALL_BOUND_JUNIT_CASES_PASSED
            ))
        )

        val missing = ChangeSpecificationEvaluator.evaluate(
            specification,
            availableEvidenceIds = emptySet(),
            currentCandidateEvidenceAvailable = true,
            junitTestReports = emptyList()
        )!!
        assertEquals(CriterionEvaluationStatus.NOT_EVALUATED, missing.status)
        assertTrue(missing.criteria.single().reason.contains("not found in fresh reports"))

        val stale = ChangeSpecificationEvaluator.evaluate(
            specification,
            availableEvidenceIds = emptySet(),
            currentCandidateEvidenceAvailable = false,
            junitTestReports = emptyList()
        )!!
        assertEquals(CriterionEvaluationStatus.NOT_EVALUATED, stale.status)
        assertTrue(stale.criteria.single().reason.contains("unavailable"))
    }

    private fun commandEvidenceSpecification() = ChangeSpecification(
        requiredEvidence = listOf("Unit-test command succeeds"),
        evidenceBindings = listOf(CriterionEvidenceBinding(
            "required-evidence-001", listOf(VerificationEvidenceIds.command(1)), CriterionEvidenceRule.ALL_BOUND_COMMANDS_SUCCEEDED
        ))
    )

    @Test
    fun `command evidence IDs follow the same order as execution results`() {
        val firstId = VerificationEvidenceIds.command(1)
        val secondId = VerificationEvidenceIds.command(2)
        val specification = ChangeSpecification(
            requiredEvidence = listOf("Maven test command succeeds", "Maven package command succeeds"),
            evidenceBindings = listOf(
                CriterionEvidenceBinding("required-evidence-001", listOf(firstId), CriterionEvidenceRule.ALL_BOUND_COMMANDS_SUCCEEDED),
                CriterionEvidenceBinding("required-evidence-002", listOf(secondId), CriterionEvidenceRule.ALL_BOUND_COMMANDS_SUCCEEDED)
            )
        )
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            specification,
            availableEvidenceIds = setOf(firstId, secondId),
            commandResults = listOf(
                VerificationCommandResult(command = "./mvn -B test", executed = true, exitCode = 0),
                VerificationCommandResult(command = "./mvn -B package -DskipTests", executed = true, exitCode = 1)
            ),
            currentCandidateEvidenceAvailable = true
        )!!

        assertEquals(
            listOf(CriterionEvaluationStatus.PASS, CriterionEvaluationStatus.FAIL),
            evaluation.criteria.map { it.status }
        )
    }

    @Test
    fun `required evidence passes when its bound command exits zero`() {
        val id = VerificationEvidenceIds.command(1)
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            commandEvidenceSpecification(),
            availableEvidenceIds = setOf(id),
            commandResults = listOf(VerificationCommandResult(command = "./gradlew test", executed = true, exitCode = 0)),
            currentCandidateEvidenceAvailable = true
        )!!
        assertEquals(CriterionEvaluationStatus.PASS, evaluation.status)
        assertEquals(CriterionEvaluationStatus.PASS, evaluation.criteria.single().status)
        assertTrue(evaluation.criteria.single().reason.contains("command success only"))
    }

    @Test
    fun `required evidence fails when a bound command exits nonzero`() {
        val id = VerificationEvidenceIds.command(1)
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            commandEvidenceSpecification(),
            availableEvidenceIds = setOf(id),
            commandResults = listOf(VerificationCommandResult(command = "./gradlew test", executed = true, exitCode = 1)),
            currentCandidateEvidenceAvailable = true
        )!!
        assertEquals(CriterionEvaluationStatus.FAIL, evaluation.status)
        assertEquals(CriterionEvaluationStatus.FAIL, evaluation.criteria.single().status)
    }

    @Test
    fun `required evidence is inconclusive when a bound command times out`() {
        val id = VerificationEvidenceIds.command(1)
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            commandEvidenceSpecification(),
            availableEvidenceIds = setOf(id),
            commandResults = listOf(VerificationCommandResult(command = "./gradlew test", executed = true, timedOut = true)),
            currentCandidateEvidenceAvailable = true
        )!!
        assertEquals(CriterionEvaluationStatus.INCONCLUSIVE, evaluation.status)
        assertEquals(CriterionEvaluationStatus.INCONCLUSIVE, evaluation.criteria.single().status)
    }

    @Test
    fun `required evidence remains unevaluated when no command result exists`() {
        val id = VerificationEvidenceIds.command(1)
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            commandEvidenceSpecification(),
            availableEvidenceIds = setOf(id),
            currentCandidateEvidenceAvailable = false,
            commandResults = emptyList()
        )!!
        assertEquals(CriterionEvaluationStatus.NOT_EVALUATED, evaluation.status)
        assertEquals(CriterionEvaluationStatus.NOT_EVALUATED, evaluation.criteria.single().status)
    }

    @Test
    fun `stale command results cannot pass when current candidate evidence is unavailable`() {
        val id = VerificationEvidenceIds.command(1)
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            commandEvidenceSpecification(),
            availableEvidenceIds = setOf(id),
            currentCandidateEvidenceAvailable = false,
            // Even if a caller accidentally supplies a prior successful result, it is not
            // evidence for the current candidate and must not be accepted.
            commandResults = listOf(VerificationCommandResult(command = "./gradlew test", executed = true, exitCode = 0))
        )!!

        assertEquals(CriterionEvaluationStatus.NOT_EVALUATED, evaluation.status)
        assertEquals(CriterionEvaluationStatus.NOT_EVALUATED, evaluation.criteria.single().status)
        assertTrue(evaluation.criteria.single().reason.contains("rather than relying on stale command outcomes"))
        assertEquals(0, evaluation.evaluatedCount)
        assertEquals(1, evaluation.notEvaluatedCount)
    }

    @Test
    fun `omitted current candidate evidence flag fails closed for a successful command result`() {
        val id = VerificationEvidenceIds.command(1)
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            commandEvidenceSpecification(),
            availableEvidenceIds = setOf(id),
            commandResults = listOf(VerificationCommandResult(command = "./gradlew test", executed = true, exitCode = 0))
        )!!

        assertEquals(CriterionEvaluationStatus.NOT_EVALUATED, evaluation.status)
        assertEquals(CriterionEvaluationStatus.NOT_EVALUATED, evaluation.criteria.single().status)
        assertTrue(evaluation.criteria.single().reason.contains("Current-candidate command results are unavailable"))
        assertEquals(0, evaluation.evaluatedCount)
        assertEquals(1, evaluation.notEvaluatedCount)
    }

    @Test
    fun `duplicate current-candidate citations cannot make a metric criterion pass`() {
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            ChangeSpecification(
                acceptanceCriteria = listOf("metric:repo.metrics.files>=3"),
                evidenceBindings = listOf(CriterionEvidenceBinding("acceptance-criterion-001", listOf("repo.metrics")))
            ),
            availableEvidenceIds = setOf("repo.metrics"),
            evidenceCitations = listOf(
                EvidenceCitation("repo.metrics", "repository-metrics", detail = "first copy says one file", metrics = mapOf("files" to "1")),
                EvidenceCitation("repo.metrics", "repository-metrics", detail = "conflicting copy says five files", metrics = mapOf("files" to "5"))
            ),
            currentCandidateEvidenceAvailable = true
        )!!

        assertEquals(CriterionEvaluationStatus.FAIL, evaluation.status)
        assertEquals(CriterionEvaluationStatus.FAIL, evaluation.criteria.single().status)
        assertTrue(evaluation.criteria.single().reason.contains("appears more than once"))
    }

    @Test
    fun `numeric metric criterion passes when current candidate meets threshold`() {
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            ChangeSpecification(
                acceptanceCriteria = listOf("metric:repo.metrics.files>=3"),
                evidenceBindings = listOf(CriterionEvidenceBinding("acceptance-criterion-001", listOf("repo.metrics")))
            ),
            availableEvidenceIds = setOf("repo.metrics"),
            evidenceCitations = listOf(
                EvidenceCitation("repo.metrics", "repository-metrics", detail = "metrics", metrics = mapOf("files" to "5"))
            ),
            currentCandidateEvidenceAvailable = true
        )!!

        assertEquals(CriterionEvaluationStatus.PASS, evaluation.status)
        assertEquals(CriterionEvaluationStatus.PASS, evaluation.criteria.single().status)
        assertTrue(evaluation.criteria.single().reason.contains("is 5"))
        assertEquals(1, evaluation.evaluatedCount)
        assertEquals(0, evaluation.notEvaluatedCount)
    }

    @Test
    fun `numeric metric criterion fails when current candidate misses threshold`() {
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            ChangeSpecification(
                acceptanceCriteria = listOf("metric:repo.metrics.files>=10"),
                evidenceBindings = listOf(CriterionEvidenceBinding("acceptance-criterion-001", listOf("repo.metrics")))
            ),
            availableEvidenceIds = setOf("repo.metrics"),
            evidenceCitations = listOf(
                EvidenceCitation("repo.metrics", "repository-metrics", detail = "metrics", metrics = mapOf("files" to "5"))
            ),
            currentCandidateEvidenceAvailable = true
        )!!

        assertEquals(CriterionEvaluationStatus.FAIL, evaluation.status)
        assertEquals(CriterionEvaluationStatus.FAIL, evaluation.criteria.single().status)
    }

    @Test
    fun `metric criterion cannot read an unbound citation`() {
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            ChangeSpecification(
                acceptanceCriteria = listOf("metric:repo.metrics.files>=3"),
                evidenceBindings = listOf(CriterionEvidenceBinding("acceptance-criterion-001", listOf("file.1")))
            ),
            availableEvidenceIds = setOf("file.1", "repo.metrics"),
            evidenceCitations = listOf(
                EvidenceCitation("repo.metrics", "repository-metrics", detail = "metrics", metrics = mapOf("files" to "5"))
            )
        )!!

        assertEquals(CriterionEvaluationStatus.FAIL, evaluation.status)
        assertTrue(evaluation.criteria.single().reason.contains("not bound"))
    }

    @Test
    fun `metric criterion fails closed when metric is absent`() {
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            ChangeSpecification(
                acceptanceCriteria = listOf("metric:repo.metrics.files>=3"),
                evidenceBindings = listOf(CriterionEvidenceBinding("acceptance-criterion-001", listOf("repo.metrics")))
            ),
            availableEvidenceIds = setOf("repo.metrics"),
            evidenceCitations = listOf(
                EvidenceCitation("repo.metrics", "repository-metrics", detail = "metrics", metrics = mapOf("nodes" to "5"))
            ),
            currentCandidateEvidenceAvailable = true
        )!!

        assertEquals(CriterionEvaluationStatus.FAIL, evaluation.status)
        assertTrue(evaluation.criteria.single().reason.contains("absent"))
    }

    @Test
    fun `metric criterion is not evaluated when contract-only mode lacks candidate evidence`() {
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            ChangeSpecification(
                acceptanceCriteria = listOf("metric:repo.metrics.files>=3"),
                evidenceBindings = listOf(CriterionEvidenceBinding("acceptance-criterion-001", listOf("repo.metrics")))
            ),
            availableEvidenceIds = setOf("repo.metrics"),
            currentCandidateEvidenceAvailable = false
        )!!

        assertEquals(CriterionEvaluationStatus.NOT_EVALUATED, evaluation.status)
        assertEquals(CriterionEvaluationStatus.NOT_EVALUATED, evaluation.criteria.single().status)
        assertEquals(1, evaluation.notEvaluatedCount)
        assertTrue(evaluation.criteria.single().reason.contains("contract-only mode"))
    }

    @Test
    fun `metric criterion ignores supplied citations when candidate evidence is unavailable`() {
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            ChangeSpecification(
                acceptanceCriteria = listOf("metric:repo.metrics.files>=3"),
                evidenceBindings = listOf(CriterionEvidenceBinding("acceptance-criterion-001", listOf("repo.metrics")))
            ),
            availableEvidenceIds = setOf("repo.metrics"),
            evidenceCitations = listOf(
                EvidenceCitation("repo.metrics", "repository-metrics", detail = "stale metric", metrics = mapOf("files" to "500"))
            ),
            currentCandidateEvidenceAvailable = false
        )!!

        assertEquals(CriterionEvaluationStatus.NOT_EVALUATED, evaluation.status)
        assertEquals(CriterionEvaluationStatus.NOT_EVALUATED, evaluation.criteria.single().status)
        assertEquals(1, evaluation.notEvaluatedCount)
        assertTrue(evaluation.criteria.single().reason.contains("contract-only mode"))
    }

    @Test
    fun `boolean metric criterion passes when current candidate matches`() {
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            ChangeSpecification(
                acceptanceCriteria = listOf("metric:architecture.summary.cycles==false"),
                evidenceBindings = listOf(CriterionEvidenceBinding("acceptance-criterion-001", listOf("architecture.summary")))
            ),
            availableEvidenceIds = setOf("architecture.summary"),
            evidenceCitations = listOf(
                EvidenceCitation("architecture.summary", "architecture-summary", detail = "no cycles", metrics = mapOf("cycles" to "false"))
            ),
            currentCandidateEvidenceAvailable = true
        )!!

        assertEquals(CriterionEvaluationStatus.PASS, evaluation.status)
        assertEquals(CriterionEvaluationStatus.PASS, evaluation.criteria.single().status)
    }

    @Test
    fun `boolean metric criterion fails when current candidate contradicts it`() {
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            ChangeSpecification(
                acceptanceCriteria = listOf("metric:architecture.summary.cycles==false"),
                evidenceBindings = listOf(CriterionEvidenceBinding("acceptance-criterion-001", listOf("architecture.summary")))
            ),
            availableEvidenceIds = setOf("architecture.summary"),
            evidenceCitations = listOf(
                EvidenceCitation("architecture.summary", "architecture-summary", detail = "cycles exist", metrics = mapOf("cycles" to "true"))
            ),
            currentCandidateEvidenceAvailable = true
        )!!

        assertEquals(CriterionEvaluationStatus.FAIL, evaluation.status)
        assertEquals(CriterionEvaluationStatus.FAIL, evaluation.criteria.single().status)
    }

    @Test
    fun `boolean metric rejects ordered comparisons`() {
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            ChangeSpecification(
                acceptanceCriteria = listOf("metric:architecture.summary.cycles>=true"),
                evidenceBindings = listOf(CriterionEvidenceBinding("acceptance-criterion-001", listOf("architecture.summary")))
            ),
            availableEvidenceIds = setOf("architecture.summary"),
            evidenceCitations = listOf(
                EvidenceCitation("architecture.summary", "architecture-summary", detail = "cycles exist", metrics = mapOf("cycles" to "true"))
            ),
            currentCandidateEvidenceAvailable = true
        )!!

        assertEquals(CriterionEvaluationStatus.FAIL, evaluation.status)
        assertTrue(evaluation.criteria.single().reason.contains("supports only == and !="))
    }

    @Test
    fun `boolean criterion fails closed for non-boolean evidence value`() {
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            ChangeSpecification(
                acceptanceCriteria = listOf("metric:architecture.summary.cycles==true"),
                evidenceBindings = listOf(CriterionEvidenceBinding("acceptance-criterion-001", listOf("architecture.summary")))
            ),
            availableEvidenceIds = setOf("architecture.summary"),
            evidenceCitations = listOf(
                EvidenceCitation("architecture.summary", "architecture-summary", detail = "invalid metric", metrics = mapOf("cycles" to "sometimes"))
            ),
            currentCandidateEvidenceAvailable = true
        )!!

        assertEquals(CriterionEvaluationStatus.FAIL, evaluation.status)
        assertTrue(evaluation.criteria.single().reason.contains("not a boolean"))
    }

    @Test
    fun `dangling evidence binding fails closed`() {
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            ChangeSpecification(
                acceptanceCriteria = listOf("Invalid input returns 400"),
                evidenceBindings = listOf(CriterionEvidenceBinding("acceptance-criterion-001", listOf("missing.evidence")))
            ),
            availableEvidenceIds = setOf("file.1")
        )!!

        assertEquals(CriterionEvaluationStatus.FAIL, evaluation.status)
        assertEquals(CriterionEvaluationStatus.FAIL, evaluation.criteria.single().status)
        assertTrue(evaluation.criteria.single().reason.contains("missing.evidence"))
    }

    @Test
    fun `malformed metric syntax fails closed`() {
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            ChangeSpecification(
                acceptanceCriteria = listOf("metric:repo.metrics.files"),
                evidenceBindings = listOf(CriterionEvidenceBinding("acceptance-criterion-001", listOf("repo.metrics")))
            ),
            availableEvidenceIds = setOf("repo.metrics"),
            evidenceCitations = listOf(
                EvidenceCitation("repo.metrics", "repository-metrics", detail = "metrics", metrics = mapOf("files" to "5"))
            )
        )!!

        assertEquals(CriterionEvaluationStatus.FAIL, evaluation.status)
        assertTrue(evaluation.criteria.single().reason.contains("Malformed metric criterion"))
    }

    @Test
    fun `malformed persisted specification fails closed`() {
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            ChangeSpecification(requiredBehaviors = listOf("   "))
        )!!

        assertEquals(CriterionEvaluationStatus.FAIL, evaluation.status)
        assertEquals(1, evaluation.evaluatedCount)
        assertEquals(0, evaluation.notEvaluatedCount)
        assertEquals(SpecificationCriterionKind.SPECIFICATION_VALIDITY, evaluation.criteria.single().kind)
    }

    @Test
    fun `empty specification does not change legacy verification behavior`() {
        assertNull(ChangeSpecificationEvaluator.evaluate(
            ChangeSpecification()
        ))
    }

    @Test
    fun `criterion identity is deterministic for the same ordered specification`() {
        val spec = ChangeSpecification(
            requiredBehaviors = listOf("One", "Two"),
            acceptanceCriteria = listOf("A")
        )
        val first = ChangeSpecificationEvaluator.evaluate(
            spec
        )!!
        val second = ChangeSpecificationEvaluator.evaluate(
            spec,
            currentCandidateEvidenceAvailable = true
        )!!

        assertEquals(first, second)
    }

    @Test
    fun `benchmark evaluator vectors guard false acceptance and false rejection`() {
        data class Outcome(
            val id: String,
            val classification: String,
            val expectedStatus: String,
            val observedStatus: String,
            val passed: Boolean,
            val elapsedMs: Double,
            val reason: String
        )

        val fixture = File("benchmark/agent-change-v0.2/evaluator-cases.json")
        val fixtureBytes = fixture.readBytes()
        val fixtureDigest = MessageDigest.getInstance("SHA-256")
            .digest(fixtureBytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val root = Json.parseToJsonElement(fixtureBytes.toString(Charsets.UTF_8)).jsonObject
        val cases = root.getValue("cases").jsonArray
        val outcomes = mutableListOf<Outcome>()

        for (element in cases) {
            val started = System.nanoTime()
            val case = element.jsonObject
            val caseId = case.getValue("id").jsonPrimitive.content
            val classification = case.getValue("classification").jsonPrimitive.content
            val criterion = case.getValue("criterion").jsonPrimitive.content
            val criterionKind = case["criterionKind"]?.jsonPrimitive?.content ?: "ACCEPTANCE_CRITERION"
            val bindingId = case.getValue("bindingCriterionId").jsonPrimitive.content
            val boundIds = case.getValue("boundEvidenceIds").jsonArray.map { it.jsonPrimitive.content }
            val availableIds = case.getValue("availableEvidenceIds").jsonArray.map { it.jsonPrimitive.content }
            val bindingRule = case["bindingRule"]?.jsonPrimitive?.content
                ?.let(CriterionEvidenceRule::valueOf)
                ?: CriterionEvidenceRule.MANUAL_REVIEW
            val currentCandidateEvidenceAvailable =
                case["currentCandidateEvidenceAvailable"]?.jsonPrimitive?.content?.toBoolean()
            val commandResults = case["commandResults"]?.jsonArray?.map { resultElement ->
                val result = resultElement.jsonObject
                VerificationCommandResult(
                    command = result.getValue("command").jsonPrimitive.content,
                    executed = result.getValue("executed").jsonPrimitive.content.toBoolean(),
                    exitCode = result["exitCode"]?.jsonPrimitive?.content?.toIntOrNull(),
                    timedOut = result["timedOut"]?.jsonPrimitive?.content?.toBoolean() ?: false
                )
            }.orEmpty()
            val junitTestReports = case["junitTestReports"]?.jsonArray?.map { reportElement ->
                val report = reportElement.jsonObject
                val testCases = report.getValue("cases").jsonArray.map { testCaseElement ->
                    val testCase = testCaseElement.jsonObject
                    JUnitTestCaseEvidence(
                        className = testCase["className"]?.jsonPrimitive?.content,
                        name = testCase.getValue("name").jsonPrimitive.content,
                        status = JUnitCaseStatus.valueOf(testCase.getValue("status").jsonPrimitive.content)
                    )
                }
                JUnitTestReportEvidence(
                    candidateSha = report.getValue("candidateSha").jsonPrimitive.content,
                    candidateStateDigest = report.getValue("candidateStateDigest").jsonPrimitive.content,
                    executionId = report.getValue("executionId").jsonPrimitive.content,
                    reportSha256 = report.getValue("reportSha256").jsonPrimitive.content,
                    status = JUnitReportStatus.valueOf(report.getValue("status").jsonPrimitive.content),
                    tests = report.getValue("tests").jsonPrimitive.content.toInt(),
                    passed = report.getValue("passed").jsonPrimitive.content.toInt(),
                    failed = report.getValue("failed").jsonPrimitive.content.toInt(),
                    skipped = report.getValue("skipped").jsonPrimitive.content.toInt(),
                    cases = testCases,
                    reportPath = report.getValue("reportPath").jsonPrimitive.content
                )
            }.orEmpty()
            val citations = case.getValue("citations").jsonArray.map { citationElement ->
                val citation = citationElement.jsonObject
                EvidenceCitation(
                    id = citation.getValue("id").jsonPrimitive.content,
                    type = citation.getValue("type").jsonPrimitive.content,
                    detail = citation.getValue("detail").jsonPrimitive.content,
                    metrics = citation.getValue("metrics").jsonObject.mapValues { (_, value) ->
                        value.jsonPrimitive.content
                    }
                )
            }
            val specification = ChangeSpecification(
                acceptanceCriteria = if (criterionKind == "ACCEPTANCE_CRITERION") listOf(criterion) else emptyList(),
                requiredEvidence = if (criterionKind == "REQUIRED_EVIDENCE") listOf(criterion) else emptyList(),
                evidenceBindings = listOf(CriterionEvidenceBinding(bindingId, boundIds, bindingRule))
            )
            val evaluation = if (currentCandidateEvidenceAvailable == null) {
                // Deliberately omit the freshness flag so this vector exercises the evaluator's real API default.
                ChangeSpecificationEvaluator.evaluate(
                    specification,
                    availableEvidenceIds = availableIds.toSet(),
                    evidenceCitations = citations,
                    commandResults = commandResults,
                    junitTestReports = junitTestReports
                )!!
            } else {
                ChangeSpecificationEvaluator.evaluate(
                    specification,
                    availableEvidenceIds = availableIds.toSet(),
                    evidenceCitations = citations,
                    currentCandidateEvidenceAvailable = currentCandidateEvidenceAvailable,
                    commandResults = commandResults,
                    junitTestReports = junitTestReports
                )!!
            }
            val expectedStatus = case.getValue("expectedStatus").jsonPrimitive.content
            val observedStatus = evaluation.criteria.single().status.name
            outcomes += Outcome(
                id = caseId,
                classification = classification,
                expectedStatus = expectedStatus,
                observedStatus = observedStatus,
                passed = expectedStatus == observedStatus,
                elapsedMs = (System.nanoTime() - started) / 1_000_000.0,
                reason = evaluation.criteria.single().reason
            )
        }

        val candidateSha = System.getenv("GITHUB_SHA")
            ?.takeIf { it.matches(Regex("^[a-f0-9]{40}$")) }
            ?: "unknown"
        val report = buildJsonObject {
            put("schemaVersion", "1.0")
            put("benchmarkId", root.getValue("benchmarkId").jsonPrimitive.content)
            put("benchmarkVersion", root.getValue("benchmarkVersion").jsonPrimitive.content)
            put("candidateSha", candidateSha)
            put("fixtureSha256", fixtureDigest)
            put("generatedAtUtc", Instant.now().toString())
            put("environment", buildJsonObject {
                put("javaVersion", System.getProperty("java.version").orEmpty())
                put("osName", System.getProperty("os.name").orEmpty())
                put("osArch", System.getProperty("os.arch").orEmpty())
            })
            put("cases", buildJsonArray {
                outcomes.forEach { outcome ->
                    add(buildJsonObject {
                        put("id", outcome.id)
                        put("classification", outcome.classification)
                        put("expectedStatus", outcome.expectedStatus)
                        put("observedStatus", outcome.observedStatus)
                        put("passed", outcome.passed)
                        put("elapsedMs", outcome.elapsedMs)
                        put("reason", outcome.reason)
                    })
                }
            })
            put("summary", buildJsonObject {
                put("executed", outcomes.size)
                put("passed", outcomes.count { it.passed })
                put("failed", outcomes.count { !it.passed })
                put("overallPass", outcomes.isNotEmpty() && outcomes.all { it.passed })
            })
            put("limitations", "Deterministic evaluator vectors only; not a comparative leaderboard or proof of arbitrary semantic correctness.")
        }
        val reportFile = File("build/reports/vericore-evaluator-benchmark.json")
        check(reportFile.parentFile.mkdirs() || reportFile.parentFile.isDirectory) {
            "Could not create evaluator benchmark report directory: ${reportFile.parentFile}"
        }
        reportFile.writeText(
            Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), report) + "\n",
            Charsets.UTF_8
        )

        outcomes.forEach { outcome ->
            assertEquals(
                outcome.expectedStatus,
                outcome.observedStatus,
                "Benchmark vector ${outcome.id}: ${outcome.reason}"
            )
        }
        assertTrue(outcomes.isNotEmpty(), "Evaluator benchmark must execute at least one vector.")
        assertTrue(reportFile.isFile, "Evaluator benchmark report must be written before assertions complete.")
    }
}
