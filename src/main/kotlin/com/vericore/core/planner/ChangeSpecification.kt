package com.vericore.core.planner

import kotlinx.serialization.Serializable

/**
 * Explicit, user/agent-authored constraints for one proposed change.
 *
 * Vericore records these declarations and binds them into the plan/contract fingerprint.
 * Their presence does not imply that each criterion has already been semantically evaluated.
 */
@Serializable
data class ChangeSpecification(
    val requiredBehaviors: List<String> = emptyList(),
    val forbiddenBehaviors: List<String> = emptyList(),
    val protectedInvariants: List<String> = emptyList(),
    val acceptanceCriteria: List<String> = emptyList(),
    val requiredEvidence: List<String> = emptyList()
) {
    fun isEmpty(): Boolean =
        requiredBehaviors.isEmpty() &&
            forbiddenBehaviors.isEmpty() &&
            protectedInvariants.isEmpty() &&
            acceptanceCriteria.isEmpty() &&
            requiredEvidence.isEmpty()

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
        val values = normalizedGroups.associate { it.first to it.second }
        return ChangeSpecification(
            requiredBehaviors = values.getValue("requiredBehaviors"),
            forbiddenBehaviors = values.getValue("forbiddenBehaviors"),
            protectedInvariants = values.getValue("protectedInvariants"),
            acceptanceCriteria = values.getValue("acceptanceCriteria"),
            requiredEvidence = values.getValue("requiredEvidence")
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
    }
}
