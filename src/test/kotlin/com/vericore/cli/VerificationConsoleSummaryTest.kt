package com.vericore.cli

import com.vericore.core.workflow.ChangeSpecificationEvaluation
import com.vericore.core.workflow.CriterionEvaluationStatus
import com.vericore.core.workflow.SpecificationCriterionEvaluation
import com.vericore.core.workflow.SpecificationCriterionKind
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class VerificationConsoleSummaryTest {
    @Test
    fun `missing structured specification is explicitly not evaluated`() {
        val output = VerificationConsoleSummary.render(null)

        assertContains(output, "Status: NOT_EVALUATED")
        assertContains(output, "No structured change specification was declared.")
        assertContains(output, "Semantic requirement coverage was not evaluated.")
    }

    @Test
    fun `summary shows each criterion status and its explanation`() {
        val evaluation = ChangeSpecificationEvaluation(
            status = CriterionEvaluationStatus.NOT_EVALUATED,
            criteria = listOf(
                SpecificationCriterionEvaluation(
                    id = "acceptance-criterion-001",
                    kind = SpecificationCriterionKind.ACCEPTANCE_CRITERION,
                    statement = "Invalid input returns a client error",
                    status = CriterionEvaluationStatus.PASS,
                    reason = "The bound deterministic evaluator matched the candidate metric.",
                    evidenceIds = listOf("test.result.1")
                ),
                SpecificationCriterionEvaluation(
                    id = "protected-invariant-001",
                    kind = SpecificationCriterionKind.PROTECTED_INVARIANT,
                    statement = "Retries remain idempotent",
                    status = CriterionEvaluationStatus.NOT_EVALUATED,
                    reason = "No criterion-specific evaluator is available."
                ),
                SpecificationCriterionEvaluation(
                    id = "required-evidence-001",
                    kind = SpecificationCriterionKind.REQUIRED_EVIDENCE,
                    statement = "Required tests succeed",
                    status = CriterionEvaluationStatus.INCONCLUSIVE,
                    reason = "The required command timed out."
                )
            ),
            evaluatedCount = 2,
            notEvaluatedCount = 1
        )

        val output = VerificationConsoleSummary.render(evaluation)

        assertContains(output, "Status: NOT_EVALUATED")
        assertContains(output, "Total criteria: 3")
        assertContains(output, "PASS: 1")
        assertContains(output, "INCONCLUSIVE: 1")
        assertContains(output, "NOT_EVALUATED: 1")
        assertContains(output, "PASS — acceptance-criterion-001")
        assertContains(output, "NOT_EVALUATED — protected-invariant-001")
        assertContains(output, "INCONCLUSIVE — required-evidence-001")
        assertContains(output, "The required command timed out.")
        assertContains(output, "Evidence: test.result.1")
        assertTrue(output.indexOf("INCONCLUSIVE — required-evidence-001") > output.indexOf("PASS — acceptance-criterion-001"))
    }
}
