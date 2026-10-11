package com.vericore.core.workflow

import com.vericore.core.planner.ChangeSpecification
import com.vericore.core.planner.CriterionEvidenceBinding
import com.vericore.core.planner.CriterionEvidenceRule
import com.vericore.core.planner.VerificationEvidenceIds
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
    fun `linked evidence remains inconclusive until criterion-specific evaluation exists`() {
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            ChangeSpecification(
                acceptanceCriteria = listOf("Invalid input returns 400"),
                evidenceBindings = listOf(
                    com.vericore.core.planner.CriterionEvidenceBinding("acceptance-criterion-001", listOf("file.1"))
                )
            ),
            availableEvidenceIds = setOf("file.1")
        )!!

        assertEquals(CriterionEvaluationStatus.INCONCLUSIVE, evaluation.status)
        assertEquals(CriterionEvaluationStatus.INCONCLUSIVE, evaluation.criteria.single().status)
        assertEquals(listOf("file.1"), evaluation.criteria.single().evidenceIds)
    }

    @Test
    fun `dangling evidence binding fails closed`() {
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            ChangeSpecification(
                acceptanceCriteria = listOf("Invalid input returns 400"),
                evidenceBindings = listOf(
                    com.vericore.core.planner.CriterionEvidenceBinding("acceptance-criterion-001", listOf("missing.evidence"))
                )
            ),
            availableEvidenceIds = setOf("file.1")
        )!!

        assertEquals(CriterionEvaluationStatus.FAIL, evaluation.status)
        assertEquals(CriterionEvaluationStatus.FAIL, evaluation.criteria.single().status)
        assertTrue(evaluation.criteria.single().reason.contains("missing.evidence"))
    }

    @Test
    fun `required evidence passes only when its bound command succeeds`() {
        val specification = ChangeSpecification(
            requiredEvidence = listOf("Unit-test command succeeds"),
            evidenceBindings = listOf(
                CriterionEvidenceBinding(
                    "required-evidence-001",
                    listOf(VerificationEvidenceIds.command(1)),
                    CriterionEvidenceRule.ALL_BOUND_COMMANDS_SUCCEEDED
                )
            )
        )
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            specification,
            availableEvidenceIds = setOf(VerificationEvidenceIds.command(1)),
            commandResults = listOf(
                VerificationCommandResult(command = "mvn -B test", executed = true, exitCode = 0)
            )
        )!!

        assertEquals(CriterionEvaluationStatus.PASS, evaluation.status)
        assertEquals(CriterionEvaluationStatus.PASS, evaluation.criteria.single().status)
        assertEquals(1, evaluation.evaluatedCount)
        assertEquals(0, evaluation.notEvaluatedCount)
    }

    @Test
    fun `required evidence fails when its bound command exits nonzero`() {
        val specification = ChangeSpecification(
            requiredEvidence = listOf("Unit-test command succeeds"),
            evidenceBindings = listOf(
                CriterionEvidenceBinding(
                    "required-evidence-001",
                    listOf(VerificationEvidenceIds.command(1)),
                    CriterionEvidenceRule.ALL_BOUND_COMMANDS_SUCCEEDED
                )
            )
        )
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            specification,
            availableEvidenceIds = setOf(VerificationEvidenceIds.command(1)),
            commandResults = listOf(
                VerificationCommandResult(command = "mvn -B test", executed = true, exitCode = 1)
            )
        )!!

        assertEquals(CriterionEvaluationStatus.FAIL, evaluation.status)
        assertEquals(CriterionEvaluationStatus.FAIL, evaluation.criteria.single().status)
    }

    @Test
    fun `contract-only verification leaves command evidence unevaluated`() {
        val specification = ChangeSpecification(
            requiredEvidence = listOf("Unit-test command succeeds"),
            evidenceBindings = listOf(
                CriterionEvidenceBinding(
                    "required-evidence-001",
                    listOf(VerificationEvidenceIds.command(1)),
                    CriterionEvidenceRule.ALL_BOUND_COMMANDS_SUCCEEDED
                )
            )
        )
        val evaluation = ChangeSpecificationEvaluator.evaluate(
            specification,
            availableEvidenceIds = setOf(VerificationEvidenceIds.command(1))
        )!!

        assertEquals(CriterionEvaluationStatus.NOT_EVALUATED, evaluation.status)
        assertEquals(CriterionEvaluationStatus.NOT_EVALUATED, evaluation.criteria.single().status)
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
