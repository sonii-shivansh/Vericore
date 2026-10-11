package com.vericore.core.workflow

import com.vericore.core.planner.ChangeSpecification
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

    fun evaluate(specification: ChangeSpecification): ChangeSpecificationEvaluation? {
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

        val criteria = buildList {
            addAll(normalized.requiredBehaviors.withIds("required-behavior", SpecificationCriterionKind.REQUIRED_BEHAVIOR))
            addAll(normalized.forbiddenBehaviors.withIds("forbidden-behavior", SpecificationCriterionKind.FORBIDDEN_BEHAVIOR))
            addAll(normalized.protectedInvariants.withIds("protected-invariant", SpecificationCriterionKind.PROTECTED_INVARIANT))
            addAll(normalized.acceptanceCriteria.withIds("acceptance-criterion", SpecificationCriterionKind.ACCEPTANCE_CRITERION))
            addAll(normalized.requiredEvidence.withIds("required-evidence", SpecificationCriterionKind.REQUIRED_EVIDENCE))
        }
        val notEvaluatedCount = criteria.count { it.status == CriterionEvaluationStatus.NOT_EVALUATED }
        val status = when {
            criteria.any { it.status == CriterionEvaluationStatus.FAIL } -> CriterionEvaluationStatus.FAIL
            criteria.any { it.status == CriterionEvaluationStatus.INCONCLUSIVE } -> CriterionEvaluationStatus.INCONCLUSIVE
            notEvaluatedCount > 0 -> CriterionEvaluationStatus.NOT_EVALUATED
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
        kind: SpecificationCriterionKind
    ): List<SpecificationCriterionEvaluation> = mapIndexed { index, statement ->
        SpecificationCriterionEvaluation(
            id = "$prefix-${(index + 1).toString().padStart(3, '0')}",
            kind = kind,
            statement = statement,
            status = CriterionEvaluationStatus.NOT_EVALUATED,
            reason = if (kind == SpecificationCriterionKind.REQUIRED_EVIDENCE) {
                "No concrete artifact has been linked to this evidence obligation."
            } else {
                NOT_EVALUATED_REASON
            }
        )
    }
}
