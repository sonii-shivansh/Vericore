package com.vericore.core.workflow

import com.vericore.core.planner.ChangeSpecification
import com.vericore.core.planner.CriterionEvidenceBinding
import com.vericore.core.planner.CriterionEvidenceRule
import com.vericore.core.planner.VerificationEvidenceIds
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
 * The only automated rule currently supported is ALL_BOUND_COMMANDS_SUCCEEDED for required-evidence
 * criteria. It proves only that the explicitly bound commands ran and exited 0; it does not infer
 * semantic success from tests, file changes, or the existence of a statement. Other criteria
 * remain NOT_EVALUATED or INCONCLUSIVE until a criterion-specific evidence evaluator is available.
 */
object ChangeSpecificationEvaluator {
    private const val NOT_EVALUATED_REASON =
        "No criterion-specific evidence evaluator is implemented; this obligation requires explicit review."

    fun evaluate(
        specification: ChangeSpecification,
        availableEvidenceIds: Set<String> = emptySet(),
        commandResults: List<VerificationCommandResult> = emptyList()
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
        val commandResultsById = commandResults.mapIndexed { index, result ->
            VerificationEvidenceIds.command(index + 1) to result
        }.toMap()
        val criteria = buildList {
            addAll(normalized.requiredBehaviors.withIds("required-behavior", SpecificationCriterionKind.REQUIRED_BEHAVIOR, bindings, availableIds, commandResultsById))
            addAll(normalized.forbiddenBehaviors.withIds("forbidden-behavior", SpecificationCriterionKind.FORBIDDEN_BEHAVIOR, bindings, availableIds, commandResultsById))
            addAll(normalized.protectedInvariants.withIds("protected-invariant", SpecificationCriterionKind.PROTECTED_INVARIANT, bindings, availableIds, commandResultsById))
            addAll(normalized.acceptanceCriteria.withIds("acceptance-criterion", SpecificationCriterionKind.ACCEPTANCE_CRITERION, bindings, availableIds, commandResultsById))
            addAll(normalized.requiredEvidence.withIds("required-evidence", SpecificationCriterionKind.REQUIRED_EVIDENCE, bindings, availableIds, commandResultsById))
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
        availableEvidenceIds: Set<String>,
        commandResultsById: Map<String, VerificationCommandResult>
    ): List<SpecificationCriterionEvaluation> = mapIndexed { index, statement ->
        val id = "$prefix-${(index + 1).toString().padStart(3, '0')}"
        val binding = bindings[id]
        val missingEvidence = binding?.evidenceIds.orEmpty().filterNot(availableEvidenceIds::contains)
        val boundCommandResults = binding?.evidenceIds.orEmpty().mapNotNull(commandResultsById::get)
        val allBoundCommandsHaveResults = binding != null && boundCommandResults.size == binding.evidenceIds.size
        val commandRuleAllowed = kind == SpecificationCriterionKind.REQUIRED_EVIDENCE &&
            binding?.rule == CriterionEvidenceRule.ALL_BOUND_COMMANDS_SUCCEEDED
        val status = when {
            binding == null -> CriterionEvaluationStatus.NOT_EVALUATED
            missingEvidence.isNotEmpty() -> CriterionEvaluationStatus.FAIL
            binding.rule == CriterionEvidenceRule.ALL_BOUND_COMMANDS_SUCCEEDED && !commandRuleAllowed ->
                CriterionEvaluationStatus.FAIL
            commandRuleAllowed && !allBoundCommandsHaveResults -> CriterionEvaluationStatus.NOT_EVALUATED
            commandRuleAllowed && boundCommandResults.any { it.executed && !it.timedOut && it.exitCode != null && it.exitCode != 0 } ->
                CriterionEvaluationStatus.FAIL
            commandRuleAllowed && boundCommandResults.any { !it.executed || it.timedOut || it.exitCode == null } ->
                CriterionEvaluationStatus.INCONCLUSIVE
            commandRuleAllowed -> CriterionEvaluationStatus.PASS
            else -> CriterionEvaluationStatus.INCONCLUSIVE
        }
        val reason = when {
            binding == null && kind == SpecificationCriterionKind.REQUIRED_EVIDENCE ->
                "No concrete artifact has been linked to this evidence obligation."
            binding == null -> NOT_EVALUATED_REASON
            missingEvidence.isNotEmpty() ->
                "Evidence binding references IDs absent from the prepared contract: ${missingEvidence.sorted().joinToString(", ")}."
            binding.rule == CriterionEvidenceRule.ALL_BOUND_COMMANDS_SUCCEEDED && !commandRuleAllowed ->
                "ALL_BOUND_COMMANDS_SUCCEEDED is only valid for required-evidence criteria."
            commandRuleAllowed && !allBoundCommandsHaveResults ->
                "Bound verification command evidence was declared but was not generated in this verification run."
            commandRuleAllowed && boundCommandResults.any { it.executed && !it.timedOut && it.exitCode != null && it.exitCode != 0 } ->
                "At least one bound verification command exited non-zero."
            commandRuleAllowed && boundCommandResults.any { !it.executed || it.timedOut || it.exitCode == null } ->
                "At least one bound verification command did not produce a conclusive result."
            commandRuleAllowed ->
                "All bound verification commands executed and exited 0; this evaluates command success only, not arbitrary behavior semantics."
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
