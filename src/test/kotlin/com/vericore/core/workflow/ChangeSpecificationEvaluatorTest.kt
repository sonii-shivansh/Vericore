package com.vericore.core.workflow

import com.vericore.core.ai.EvidenceCitation
import com.vericore.core.planner.ChangeSpecification
import com.vericore.core.planner.CriterionEvidenceBinding
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.content

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
            )
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
            )
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
            )
        )!!

        assertEquals(CriterionEvaluationStatus.FAIL, evaluation.status)
        assertTrue(evaluation.criteria.single().reason.contains("absent"))
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
            )
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
            )
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
            )
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
            )
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
        assertNull(ChangeSpecificationEvaluator.evaluate(ChangeSpecification()))
    }

    @Test
    fun `criterion identity is deterministic for the same ordered specification`() {
        val spec = ChangeSpecification(
            requiredBehaviors = listOf("One", "Two"),
            acceptanceCriteria = listOf("A")
        )
        val first = ChangeSpecificationEvaluator.evaluate(spec)!!
        val second = ChangeSpecificationEvaluator.evaluate(spec)!!

        assertEquals(first, second)
    }

    @Test
    fun `benchmark evaluator vectors guard false acceptance and false rejection`() {
        val fixture = File("benchmark/agent-change-v0.2/evaluator-cases.json")
        val root = Json.parseToJsonElement(fixture.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray

        for (element in cases) {
            val case = element.jsonObject
            val caseId = case.getValue("id").jsonPrimitive.content
            val criterion = case.getValue("criterion").jsonPrimitive.content
            val bindingId = case.getValue("bindingCriterionId").jsonPrimitive.content
            val boundIds = case.getValue("boundEvidenceIds").jsonArray.map { it.jsonPrimitive.content }
            val availableIds = case.getValue("availableEvidenceIds").jsonArray.map { it.jsonPrimitive.content }
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
                acceptanceCriteria = listOf(criterion),
                evidenceBindings = listOf(CriterionEvidenceBinding(bindingId, boundIds))
            )
            val evaluation = ChangeSpecificationEvaluator.evaluate(
                specification,
                availableEvidenceIds = availableIds.toSet(),
                evidenceCitations = citations
            )!!

            assertEquals(
                CriterionEvaluationStatus.valueOf(case.getValue("expectedStatus").jsonPrimitive.content),
                evaluation.criteria.single().status,
                "Benchmark vector $caseId: ${evaluation.criteria.single().reason}"
            )
        }
    }
}
