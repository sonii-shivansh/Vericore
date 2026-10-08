package com.vericore.core.intelligence

import kotlinx.serialization.Serializable

@Serializable
enum class RecommendationPriority { CRITICAL, HIGH, MEDIUM, LOW }

@Serializable
data class EngineeringRecommendation(
    val id: String,
    val priority: RecommendationPriority,
    val title: String,
    val reason: String,
    val evidencePaths: List<String> = emptyList(),
    val action: String
)

/** Converts deterministic repository signals into prioritized engineering actions. */
object RecommendationEngine {
    fun generate(snapshot: AnalysisSnapshot): List<EngineeringRecommendation> {
        val risks = EngineeringRiskEngine.calculate(snapshot)
        val recommendations = mutableListOf<EngineeringRecommendation>()

        val critical = risks.filter { it.level == RiskLevel.CRITICAL }.take(5)
        if (critical.isNotEmpty()) {
            recommendations += EngineeringRecommendation(
                id = "RISK_CRITICAL_HOTSPOTS",
                priority = RecommendationPriority.CRITICAL,
                title = "Review critical engineering hotspots",
                reason = "${critical.size} files have critical deterministic risk signals.",
                evidencePaths = critical.map { it.path },
                action = "Review the listed files before making broad changes and add focused tests around their highest-risk behavior."
            )
        }

        val high = risks.filter { it.level == RiskLevel.HIGH }.take(5)
        if (high.isNotEmpty()) {
            recommendations += EngineeringRecommendation(
                id = "RISK_HIGH_HOTSPOTS",
                priority = RecommendationPriority.HIGH,
                title = "Review high-risk components",
                reason = "${risks.count { it.level == RiskLevel.HIGH }} files have high deterministic risk signals.",
                evidencePaths = high.map { it.path },
                action = "Prefer small, isolated changes and verify impacted dependents after modification."
            )
        }

        if (snapshot.architecture.hasCycles) {
            recommendations += EngineeringRecommendation(
                id = "ARCHITECTURE_CYCLES",
                priority = RecommendationPriority.HIGH,
                title = "Break dependency cycles",
                reason = "The analyzed dependency graph contains cycles.",
                action = "Identify the smallest cycle boundaries and remove unnecessary bidirectional dependencies before adding more coupling."
            )
        }

        if (snapshot.metrics.parseFailures > 0) {
            recommendations += EngineeringRecommendation(
                id = "PARSER_FAILURES",
                priority = RecommendationPriority.HIGH,
                title = "Resolve parser diagnostics",
                reason = "${snapshot.metrics.parseFailures} source files reported parser diagnostics during analysis.",
                action = "Resolve or explicitly account for parser diagnostics before relying on repository-wide dependency or risk conclusions."
            )
        }

        if (snapshot.architecture.crossPackageEdges > 0) {
            recommendations += EngineeringRecommendation(
                id = "CROSS_PACKAGE_COUPLING",
                priority = RecommendationPriority.MEDIUM,
                title = "Review cross-package coupling",
                reason = "${snapshot.architecture.crossPackageEdges} dependency edges cross package boundaries.",
                action = "Review the highest-centrality cross-package paths and keep new dependencies aligned with the intended architecture."
            )
        }

        if (recommendations.isEmpty()) {
            recommendations += EngineeringRecommendation(
                id = "BASELINE_HEALTHY",
                priority = RecommendationPriority.LOW,
                title = "Maintain the current engineering baseline",
                reason = "No high-priority deterministic recommendation was triggered.",
                action = "Keep the analysis baseline current and run review/verify before significant changes."
            )
        }

        return recommendations.sortedWith(compareBy<EngineeringRecommendation> { it.priority.ordinal }.thenBy { it.id })
    }
}
