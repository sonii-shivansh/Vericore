package com.vericore.core.planner

import kotlinx.serialization.Serializable

/**
 * Explicit, user/agent-authored constraints for one proposed change.
 *
 * Vericore records these declarations and binds them into the plan/contract fingerprint.
 * Their presence does not imply that each criterion has already been semantically evaluated.
 */
@Serializable
data class CriterionEvidenceBinding(
    val criterionId: String,
    val evidenceIds: List<String>
)

@Serializable
data class ChangeSpecification(
    val requiredBehaviors: List<String> = emptyList(),
    val forbiddenBehaviors: List<String> = emptyList(),
    val protectedInvariants: List<String> = emptyList(),
    val acceptanceCriteria: List<String> = emptyList(),
    val requiredEvidence: List<String> = emptyList(),
    val evidenceBindings: List<CriterionEvidenceBinding> = emptyList()
) {
    fun isEmpty(): Boolean =
        requiredBehaviors.isEmpty() &&
            forbiddenBehaviors.isEmpty() &&
            protectedInvariants.isEmpty() &&
            acceptanceCriteria.isEmpty() &&
            requiredEvidence.isEmpty() &&
            evidenceBindings.isEmpty()

    /**
     * Validates caller input, trims entries, and removes duplicates without reordering them.
     * List order is preserved because callers may use it as an intentional review sequence.
     */
    fun normalized(): ChangeSpecification {
        val groups = listOf(
            "requiredBehaviors" to requiredBehaviors,
            "forbiddenBehaviors" to forbiddenBehaviors,
            "protectedInvariants" to protectedInvariants,
            "acceptanceCriteria" to acceptanceCriteria,
            "requiredEvidence" to requiredEvidence
        )
        require(groups.all { it.second.size <= MAX_ITEMS_PER_GROUP }) {
            "Each change-specification field may contain at most $MAX_ITEMS_PER_GROUP entries"
        }
        val normalizedGroups = groups.map { (name, values) ->
            name to values.map { value ->
                val trimmed = value.trim()
                require(trimmed.isNotEmpty()) { "$name entries must not be blank" }
                require(trimmed.length <= MAX_ITEM_LENGTH) {
                    "$name entries must not exceed $MAX_ITEM_LENGTH characters"
                }
                trimmed
            }.distinct()
        }
        require(normalizedGroups.sumOf { it.second.size } <= MAX_TOTAL_ITEMS) {
            "A change specification may contain at most $MAX_TOTAL_ITEMS total entries"
        }
        require(normalizedGroups.sumOf { (_, values) -> values.sumOf { it.length } } <= MAX_TOTAL_CHARACTERS) {
            "A change specification may contain at most $MAX_TOTAL_CHARACTERS total characters"
        }
        require(evidenceBindings.size <= MAX_TOTAL_ITEMS) {
            "A change specification may contain at most $MAX_TOTAL_ITEMS evidence bindings"
        }
        val validCriterionIds = normalizedGroups.flatMap { (name, entries) ->
            val prefix = when (name) {
                "requiredBehaviors" -> "required-behavior"
                "forbiddenBehaviors" -> "forbidden-behavior"
                "protectedInvariants" -> "protected-invariant"
                "acceptanceCriteria" -> "acceptance-criterion"
                "requiredEvidence" -> "required-evidence"
                else -> error("Unknown specification group: $name")
            }
            entries.indices.map { index -> "$prefix-${(index + 1).toString().padStart(3, '0')}" }
        }.toSet()
        val normalizedBindings = evidenceBindings.map { binding ->
            val criterionId = binding.criterionId.trim()
            require(criterionId.isNotEmpty() && criterionId.length <= MAX_CRITERION_ID_LENGTH) {
                "Evidence binding criterionId must contain 1-$MAX_CRITERION_ID_LENGTH characters"
            }
            require(criterionId in validCriterionIds) {
                "Evidence binding references unknown criterion ID '$criterionId'"
            }
            require(binding.evidenceIds.isNotEmpty() && binding.evidenceIds.size <= MAX_EVIDENCE_IDS_PER_BINDING) {
                "Evidence binding '$criterionId' must reference 1-$MAX_EVIDENCE_IDS_PER_BINDING evidence IDs"
            }
            val ids = binding.evidenceIds.map { evidenceId ->
                val trimmed = evidenceId.trim()
                require(trimmed.isNotEmpty() && trimmed.length <= MAX_EVIDENCE_ID_LENGTH) {
                    "Evidence IDs must contain 1-$MAX_EVIDENCE_ID_LENGTH characters"
                }
                trimmed
            }.distinct().sorted()
            CriterionEvidenceBinding(criterionId, ids)
        }.sortedBy { it.criterionId }
        require(normalizedBindings.map { it.criterionId }.distinct().size == normalizedBindings.size) {
            "Each criterion may have only one evidence binding"
        }
        val values = normalizedGroups.associate { it.first to it.second }
        return ChangeSpecification(
            requiredBehaviors = values.getValue("requiredBehaviors"),
            forbiddenBehaviors = values.getValue("forbiddenBehaviors"),
            protectedInvariants = values.getValue("protectedInvariants"),
            acceptanceCriteria = values.getValue("acceptanceCriteria"),
            requiredEvidence = values.getValue("requiredEvidence"),
            evidenceBindings = normalizedBindings
        )
    }

    /**
     * Length-prefixed canonical representation avoids delimiter ambiguity in user-authored text.
     * Fixed field order makes the fingerprint stable across map/order/runtime differences.
     */
    fun canonicalForm(): String = buildString {
        appendGroup("requiredBehaviors", requiredBehaviors)
        appendGroup("forbiddenBehaviors", forbiddenBehaviors)
        appendGroup("protectedInvariants", protectedInvariants)
        appendGroup("acceptanceCriteria", acceptanceCriteria)
        appendGroup("requiredEvidence", requiredEvidence)
        // Preserve fingerprints of previously stored specifications when no bindings exist.
        if (evidenceBindings.isNotEmpty()) {
            appendGroup("evidenceBindings", evidenceBindings.sortedBy { it.criterionId }.map { binding ->
                buildString {
                    append(binding.criterionId.length).append(':').append(binding.criterionId)
                    val ids = binding.evidenceIds.distinct().sorted()
                    append(ids.size).append(':')
                    ids.forEach { id -> append(id.length).append(':').append(id) }
                }
            })
        }
    }

    private fun StringBuilder.appendGroup(name: String, values: List<String>) {
        append(name.length).append(':').append(name)
        append(values.size).append(':')
        values.forEach { value -> append(value.length).append(':').append(value) }
    }

    companion object {
        private const val MAX_ITEMS_PER_GROUP = 100
        private const val MAX_TOTAL_ITEMS = 200
        private const val MAX_ITEM_LENGTH = 2_000
        private const val MAX_TOTAL_CHARACTERS = 20_000
        private const val MAX_CRITERION_ID_LENGTH = 100
        private const val MAX_EVIDENCE_IDS_PER_BINDING = 20
        private const val MAX_EVIDENCE_ID_LENGTH = 200
    }
}
