package com.vericore.cli

import com.vericore.core.workflow.ChangeSpecificationEvaluation
import com.vericore.core.workflow.CriterionEvaluationStatus

/**
 * Explains which declared obligations were evaluated without implying that a command exit
 * proves arbitrary semantic correctness.
 */
internal object VerificationConsoleSummary {
    fun render(evaluation: ChangeSpecificationEvaluation?): String {
        if (evaluation == null) {
            return HumanCommandOutput.render(
                title = "Requirement evidence",
                status = CriterionEvaluationStatus.NOT_EVALUATED.name,
                sections = listOf(
                    HumanCommandOutput.section(
                        "Coverage",
                        "No structured change specification was declared.",
                        "Semantic requirement coverage was not evaluated."
                    )
                )
            )
        }

        val criteria = evaluation.criteria
        val summaryLines = listOf(
            "Total criteria: ${criteria.size}",
            "PASS: ${criteria.count { it.status == CriterionEvaluationStatus.PASS }}",
            "FAIL: ${criteria.count { it.status == CriterionEvaluationStatus.FAIL }}",
            "INCONCLUSIVE: ${criteria.count { it.status == CriterionEvaluationStatus.INCONCLUSIVE }}",
            "NOT_EVALUATED: ${criteria.count { it.status == CriterionEvaluationStatus.NOT_EVALUATED }}"
        )
        val sections = buildList {
            add(HumanCommandOutput.section("Summary", *summaryLines.toTypedArray()))
            criteria.forEach { criterion ->
                val lines = buildList {
                    add(criterion.statement)
                    add("Reason: ${criterion.reason}")
                    if (criterion.evidenceIds.isNotEmpty()) {
                        add("Evidence: ${criterion.evidenceIds.joinToString(", ")}")
                    }
                }
                add(HumanCommandOutput.Section(
                    title = "${criterion.status} — ${criterion.id}",
                    lines = lines
                ))
            }
        }

        return HumanCommandOutput.render(
            title = "Requirement evidence",
            status = evaluation.status.name,
            sections = sections
        )
    }
}
