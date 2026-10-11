package com.vericore.core.workflow

import com.vericore.core.planner.ChangeSpecification
import com.vericore.core.planner.CriterionEvidenceBinding
import kotlinx.serialization.Serializable

@Serializable
enum class SpecificationCriterionKind {
    REQUIRED_BEHAVIOR,
    FORBIDDEN_BEHAVIOR,
    PROTECTED_INVARIANT,
    ACCEPTANCE_CRITERION,
    REQUIRED_EVIDENCE,
    SPECIFICATION_VALIDITY
}

@Serializable
enum class CriterionEvaluationStatus {
    PASS,
    FAIL,
    INCONCLUSIVE,
    NOT_EVALUATED
}

@Serializable
data class SpecificationCriterionEvaluation(
    val id: String,
    val kind: SpecificationCriterionKind,
    val statement: String,
    val status: CriterionEvaluationStatus,
    val reason: String,
    val evidenceIds: List<String> = emptyList()
)

@Serializable
data class ChangeSpecificationEvaluation(
    val schemaVersion: String = "1.0",
    val status: CriterionEvaluationStatus,
    val criteria: List<SpecificationCriterionEvaluation>,
    val evaluatedCount: Int,
    val notEvaluatedCount: Int
)

/**
 * Converts declared obligations into explicit evaluation records.
 *
 * This first evaluator intentionally does not infer semantic success from tests, file changes,
 * or the existence of a statement. Until a criterion-specific evidence evaluator is available,
 * each declared obligation remains NOT_EVALUATED and must not silently count as a pass.
 */
object ChangeSpecificationEvaluator {
    private const val NOT_EVALUATED_REASON =
        "No criterion-specific evidence evaluator is implemented; this obligation requires explicit review."

    fun evaluate(
        specification: ChangeSpecification,
        availableEvidenceIds: Set<String> = emptySet()
    ): ChangeSpecificationEvaluation? {
        val normalized = try {
            specification.normalized()
        } catch (error: IllegalArgumentException) {
            val invalid = SpecificationCriterionEvaluation(
                id = "specification-validity-001",
                kind = SpecificationCriterionKind.SPECIFICATION_VALIDITY,
                statement = "The persisted specification must satisfy the documented size and content limits.",
                status = CriterionEvaluationStatus.FAIL,
                reason = "Invalid persisted specification: ${error.message ?: "validation failed"}"
            )
            return ChangeSpecificationEvaluation(
                status = CriterionEvaluationStatus.FAIL,
                criteria = listOf(invalid),
                evaluatedCount = 1,
                notEvaluatedCount = 0
            )
        }
        if (normalized.isEmpty()) return null

        val bindings = normalized.evidenceBindings.associateBy { it.criterionId }
        val availableIds = availableEvidenceIds.toSet()
        val criteria = buildList {
            addAll(normalized.requiredBehaviors.withIds("required-behavior", SpecificationCriterionKind.REQUIRED_BEHAVIOR, bindings, availableIds))
            addAll(normalized.forbiddenBehaviors.withIds("forbidden-behavior", SpecificationCriterionKind.FORBIDDEN_BEHAVIOR, bindings, availableIds))
            addAll(normalized.protectedInvariants.withIds("protected-invariant", SpecificationCriterionKind.PROTECTED_INVARIANT, bindings, availableIds))
            addAll(normalized.acceptanceCriteria.withIds("acceptance-criterion", SpecificationCriterionKind.ACCEPTANCE_CRITERION, bindings, availableIds))
            addAll(normalized.requiredEvidence.withIds("required-evidence", SpecificationCriterionKind.REQUIRED_EVIDENCE, bindings, availableIds))
        }
        val notEvaluatedCount = criteria.count { it.status == CriterionEvaluationStatus.NOT_EVALUATED }
        val status = when {
            criteria.any { it.status == CriterionEvaluationStatus.FAIL } -> CriterionEvaluationStatus.FAIL
            notEvaluatedCount > 0 -> CriterionEvaluationStatus.NOT_EVALUATED
            criteria.any { it.status == CriterionEvaluationStatus.INCONCLUSIVE } -> CriterionEvaluationStatus.INCONCLUSIVE
            else -> CriterionEvaluationStatus.PASS
        }
        return ChangeSpecificationEvaluation(
            status = status,
            criteria = criteria,
            evaluatedCount = criteria.size - notEvaluatedCount,
            notEvaluatedCount = notEvaluatedCount
        )
    }

    private fun List<String>.withIds(
        prefix: String,
        kind: SpecificationCriterionKind,
        bindings: Map<String, CriterionEvidenceBinding>,
        availableEvidenceIds: Set<String>
    ): List<SpecificationCriterionEvaluation> = mapIndexed { index, statement ->
        val id = "$prefix-${(index + 1).toString().padStart(3, '0')}"
        val binding = bindings[id]
        val missingEvidence = binding?.evidenceIds.orEmpty().filterNot(availableEvidenceIds::contains)
        val status = when {
            binding == null -> CriterionEvaluationStatus.NOT_EVALUATED
            missingEvidence.isNotEmpty() -> CriterionEvaluationStatus.FAIL
            else -> CriterionEvaluationStatus.INCONCLUSIVE
        }
        val reason = when {
            binding == null && kind == SpecificationCriterionKind.REQUIRED_EVIDENCE ->
                "No concrete artifact has been linked to this evidence obligation."
            binding == null -> NOT_EVALUATED_REASON
            missingEvidence.isNotEmpty() ->
                "Evidence binding references IDs absent from the prepared contract: ${missingEvidence.sorted().joinToString(", ")}."
            else ->
                "Evidence IDs are bound to this criterion, but their content has not been semantically evaluated."
        }
        SpecificationCriterionEvaluation(
            id = id,
            kind = kind,
            statement = statement,
            status = status,
            reason = reason,
            evidenceIds = binding?.evidenceIds.orEmpty()
        )
    }
}
