package com.vericore.core.workflow

import com.vericore.core.ai.EvidenceCitation
import com.vericore.core.planner.ChangeSpecification
import com.vericore.core.planner.CriterionEvidenceBinding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
}
