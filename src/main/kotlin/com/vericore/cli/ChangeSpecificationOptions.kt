package com.vericore.cli

import com.vericore.core.planner.CriterionEvidenceBinding
import com.vericore.core.planner.CriterionEvidenceRule

/**
 * Parses repeatable CLI arguments in the form criterion-id=evidence-id, where the evidence ID may be a prepared citation or a planned verification-command ID.
 * Multiple arguments for the same criterion are grouped into one binding.
 */
internal fun parseCriterionEvidenceBindings(
    values: List<String>,
    ruleValues: List<String> = emptyList()
): List<CriterionEvidenceBinding> {
    val grouped = linkedMapOf<String, MutableList<String>>()
    val rules = linkedMapOf<String, CriterionEvidenceRule>()
    ruleValues.forEach { raw ->
        val separator = raw.indexOf('=')
        require(separator > 0 && separator < raw.lastIndex) {
            "Invalid --criterion-evidence-rule '$raw'; expected <criterion-id>=<rule>"
        }
        val criterionId = raw.substring(0, separator).trim()
        val ruleName = raw.substring(separator + 1).trim()
        require(criterionId.isNotEmpty() && ruleName.isNotEmpty()) {
            "Invalid --criterion-evidence-rule '$raw'; criterion and rule must not be blank"
        }
        val rule = runCatching { CriterionEvidenceRule.valueOf(ruleName.uppercase()) }.getOrElse {
            throw IllegalArgumentException("Unknown criterion evidence rule '$ruleName'")
        }
        require(!rules.containsKey(criterionId)) {
            "A criterion may have only one --criterion-evidence-rule"
        }
        rules[criterionId] = rule
    }
    values.forEach { raw ->
        val separator = raw.indexOf('=')
        require(separator > 0 && separator < raw.lastIndex) {
            "Invalid --criterion-evidence '$raw'; expected <criterion-id>=<evidence-id>"
        }
        val criterionId = raw.substring(0, separator).trim()
        val evidenceId = raw.substring(separator + 1).trim()
        require(criterionId.isNotEmpty() && evidenceId.isNotEmpty()) {
            "Invalid --criterion-evidence '$raw'; criterion and evidence IDs must not be blank"
        }
        grouped.getOrPut(criterionId) { mutableListOf() }.add(evidenceId)
    }
    require(rules.keys.all { it in grouped }) {
        "--criterion-evidence-rule must reference a criterion with at least one --criterion-evidence binding"
    }
    return grouped.map { (criterionId, evidenceIds) ->
        CriterionEvidenceBinding(
            criterionId,
            evidenceIds,
            rules[criterionId] ?: CriterionEvidenceRule.MANUAL_REVIEW
        )
    }
}
