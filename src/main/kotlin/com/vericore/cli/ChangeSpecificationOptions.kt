package com.vericore.cli

import com.vericore.core.planner.CriterionEvidenceBinding

/**
 * Parses repeatable CLI arguments in the form criterion-id=evidence-id.
 * Multiple arguments for the same criterion are grouped into one binding.
 */
internal fun parseCriterionEvidenceBindings(values: List<String>): List<CriterionEvidenceBinding> {
    val grouped = linkedMapOf<String, MutableList<String>>()
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
    return grouped.map { (criterionId, evidenceIds) ->
        CriterionEvidenceBinding(criterionId, evidenceIds)
    }
}
