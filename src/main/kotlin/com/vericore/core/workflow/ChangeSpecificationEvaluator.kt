package com.vericore.core.workflow

import com.vericore.core.ai.EvidenceCitation
import com.vericore.core.planner.ChangeSpecification
import com.vericore.core.planner.CriterionEvidenceBinding
import java.math.BigDecimal
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
 * Evaluates only explicitly supported, machine-readable criteria.
 *
 * The initial syntax is:
 *   metric:<citation-id>.<metric-key><operator><number>
 *
 * Example: metric:repo.metrics.files>=10
 *
 * A metric criterion must bind the named citation, and the citation must be present in the
 * evidence generated for the candidate currently under verification. Free-form statements
 * remain unevaluated/inconclusive; citation presence alone never proves them.
 */
object ChangeSpecificationEvaluator {
    private const val NOT_EVALUATED_REASON =
        "No criterion-specific evidence evaluator is implemented; this obligation requires explicit review."
    private const val METRIC_PREFIX = "metric:"
    private val metricPattern = Regex("^metric:(.+?)(>=|<=|==|!=|>|<)((?:true|false)|-?(?:\\d+(?:\\.\\d+)?|\\.\\d+))$")

    fun evaluate(
        specification: ChangeSpecification,
        availableEvidenceIds: Set<String> = emptySet(),
        evidenceCitations: List<EvidenceCitation> = emptyList(),
        currentCandidateEvidenceAvailable: Boolean = true
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
        val citationsById = evidenceCitations.associateBy { it.id }
        val criteria = buildList {
            addAll(normalized.requiredBehaviors.withIds("required-behavior", SpecificationCriterionKind.REQUIRED_BEHAVIOR, bindings, availableIds, citationsById, currentCandidateEvidenceAvailable))
            addAll(normalized.forbiddenBehaviors.withIds("forbidden-behavior", SpecificationCriterionKind.FORBIDDEN_BEHAVIOR, bindings, availableIds, citationsById, currentCandidateEvidenceAvailable))
            addAll(normalized.protectedInvariants.withIds("protected-invariant", SpecificationCriterionKind.PROTECTED_INVARIANT, bindings, availableIds, citationsById, currentCandidateEvidenceAvailable))
            addAll(normalized.acceptanceCriteria.withIds("acceptance-criterion", SpecificationCriterionKind.ACCEPTANCE_CRITERION, bindings, availableIds, citationsById, currentCandidateEvidenceAvailable))
            addAll(normalized.requiredEvidence.withIds("required-evidence", SpecificationCriterionKind.REQUIRED_EVIDENCE, bindings, availableIds, citationsById, currentCandidateEvidenceAvailable))
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
        citationsById: Map<String, EvidenceCitation>,
        currentCandidateEvidenceAvailable: Boolean
    ): List<SpecificationCriterionEvaluation> = mapIndexed { index, statement ->
        val id = "$prefix-${(index + 1).toString().padStart(3, '0')}"
        val binding = bindings[id]
        val missingEvidence = binding?.evidenceIds.orEmpty().filterNot(availableEvidenceIds::contains)
        val metricEvaluation = if (binding != null && missingEvidence.isEmpty() && statement.startsWith(METRIC_PREFIX)) {
            evaluateMetric(statement, binding.evidenceIds, citationsById, currentCandidateEvidenceAvailable)
        } else {
            null
        }
        val status = when {
            binding == null -> CriterionEvaluationStatus.NOT_EVALUATED
            missingEvidence.isNotEmpty() -> CriterionEvaluationStatus.FAIL
            metricEvaluation != null -> metricEvaluation.first
            else -> CriterionEvaluationStatus.INCONCLUSIVE
        }
        val reason = when {
            binding == null && kind == SpecificationCriterionKind.REQUIRED_EVIDENCE ->
                "No concrete artifact has been linked to this evidence obligation."
            binding == null -> NOT_EVALUATED_REASON
            missingEvidence.isNotEmpty() ->
                "Evidence binding references IDs absent from the prepared contract: ${missingEvidence.sorted().joinToString(", ")}."
            metricEvaluation != null -> metricEvaluation.second
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

    private fun evaluateMetric(
        statement: String,
        boundEvidenceIds: List<String>,
        citationsById: Map<String, EvidenceCitation>,
        currentCandidateEvidenceAvailable: Boolean
    ): Pair<CriterionEvaluationStatus, String> {
        val match = metricPattern.matchEntire(statement)
            ?: return CriterionEvaluationStatus.FAIL to
                "Malformed metric criterion. Expected metric:<citation-id>.<metric-key><operator><number-or-boolean>."
        val leftHandSide = match.groupValues[1]
        val separator = leftHandSide.lastIndexOf('.')
        if (separator <= 0 || separator == leftHandSide.lastIndex) {
            return CriterionEvaluationStatus.FAIL to
                "Malformed metric reference. Include both a citation ID and metric key."
        }
        val citationId = leftHandSide.substring(0, separator)
        val metricKey = leftHandSide.substring(separator + 1)
        val operator = match.groupValues[2]
        val expectedText = match.groupValues[3]
        if (citationId !in boundEvidenceIds) {
            return CriterionEvaluationStatus.FAIL to
                "Metric citation '$citationId' is not bound to this criterion."
        }
        if (!currentCandidateEvidenceAvailable) {
            return CriterionEvaluationStatus.NOT_EVALUATED to
                "Current-candidate evidence is not rebuilt in contract-only mode; rerun full verify to evaluate this metric."
        }
        val citation = citationsById[citationId] ?: return CriterionEvaluationStatus.FAIL to
            "Metric citation '$citationId' is absent from the current candidate's evidence."
        val actualText = citation.metrics[metricKey]
            ?: return CriterionEvaluationStatus.FAIL to
                "Metric '$metricKey' is absent from evidence citation '$citationId'."
        val satisfied = if (expectedText == "true" || expectedText == "false") {
            if (operator != "==" && operator != "!=") {
                return CriterionEvaluationStatus.FAIL to
                    "Boolean metric '$citationId.$metricKey' supports only == and != comparisons."
            }
            val actualBoolean = actualText.toBooleanStrictOrNull()
                ?: return CriterionEvaluationStatus.FAIL to
                    "Metric '$metricKey' in evidence citation '$citationId' is not a boolean."
            val expectedBoolean = expectedText == "true"
            if (operator == "==") actualBoolean == expectedBoolean else actualBoolean != expectedBoolean
        } else {
            val actual = actualText.toBigDecimalOrNull()
                ?: return CriterionEvaluationStatus.FAIL to
                    "Metric '$metricKey' in evidence citation '$citationId' is not numeric."
            val expected = expectedText.toBigDecimalOrNull()
                ?: return CriterionEvaluationStatus.FAIL to "Metric threshold is not a valid decimal number."
            val comparison = actual.compareTo(expected)
            when (operator) {
                ">=" -> comparison >= 0
                "<=" -> comparison <= 0
                "==" -> comparison == 0
                "!=" -> comparison != 0
                ">" -> comparison > 0
                "<" -> comparison < 0
                else -> false
            }
        }
        val result = if (satisfied) CriterionEvaluationStatus.PASS else CriterionEvaluationStatus.FAIL
        val reason = "Current candidate metric '$citationId.$metricKey' is $actualText; criterion requires $operator$expectedText."
        return result to reason
    }
}
