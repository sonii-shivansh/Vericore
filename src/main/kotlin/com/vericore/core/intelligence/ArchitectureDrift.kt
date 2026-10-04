package com.vericore.core.intelligence

import kotlinx.serialization.Serializable

@Serializable
data class ArchitectureDriftSummary(
    val addedFindings: Int,
    val removedFindings: Int,
    val newCycles: Int,
    val removedCycles: Int,
    val changedLayers: Int,
    /** Positive values indicate newly observed dependency edges; negative values indicate removals. */
    val dependencyEdgesDelta: Int = 0
)

@Serializable
data class ArchitectureDriftChange(
    val type: String,
    val key: String,
    val detail: String
)

@Serializable
data class ArchitectureDriftResult(
    val schemaVersion: String,
    val baselineSchemaVersion: String,
    val currentSchemaVersion: String,
    val summary: ArchitectureDriftSummary,
    val changes: List<ArchitectureDriftChange>
)

object ArchitectureDriftEngine {
    fun compare(
        baseline: ArchitectureIntelligenceResult,
        current: ArchitectureIntelligenceResult
    ): ArchitectureDriftResult {
        require(baseline.schemaVersion.isNotBlank()) { "Baseline architecture schemaVersion must not be blank" }
        require(current.schemaVersion.isNotBlank()) { "Current architecture schemaVersion must not be blank" }

        val baselineFindings = baseline.findings.map(::findingKey).toSet()
        val currentFindings = current.findings.map(::findingKey).toSet()
        val addedFindings = currentFindings - baselineFindings
        val removedFindings = baselineFindings - currentFindings

        val baselineCycles = baseline.cycles.map(::cycleKey).toSet()
        val currentCycles = current.cycles.map(::cycleKey).toSet()
        val newCycles = currentCycles - baselineCycles
        val removedCycles = baselineCycles - currentCycles

        val layerNames = (baseline.layers.keys + current.layers.keys).toSortedSet()
        val changedLayers = layerNames.filter { baseline.layers[it] != current.layers[it] }
        val dependencyEdgesDelta = current.summary.dependencyEdges - baseline.summary.dependencyEdges

        val changes = buildList {
            addedFindings.sorted().forEach { key ->
                add(ArchitectureDriftChange("FINDING_ADDED", key, "Architecture finding is present in the current snapshot but not the baseline"))
            }
            removedFindings.sorted().forEach { key ->
                add(ArchitectureDriftChange("FINDING_REMOVED", key, "Architecture finding is present in the baseline but not the current snapshot"))
            }
            newCycles.sorted().forEach { key ->
                add(ArchitectureDriftChange("CYCLE_ADDED", key, "Strongly connected architecture cycle is new in the current snapshot"))
            }
            removedCycles.sorted().forEach { key ->
                add(ArchitectureDriftChange("CYCLE_REMOVED", key, "Strongly connected architecture cycle is absent from the current snapshot"))
            }
            changedLayers.forEach { layer ->
                add(
                    ArchitectureDriftChange(
                        "LAYER_COUNT_CHANGED",
                        layer,
                        "Layer file count changed from ${baseline.layers[layer] ?: 0} to ${current.layers[layer] ?: 0}"
                    )
                )
            }
            if (dependencyEdgesDelta != 0) {
                val direction = if (dependencyEdgesDelta > 0) "added" else "removed"
                add(
                    ArchitectureDriftChange(
                        "DEPENDENCY_EDGE_COUNT_CHANGED",
                        "dependency-edges",
                        "Dependency edge count changed from ${baseline.summary.dependencyEdges} to ${current.summary.dependencyEdges} ($direction ${kotlin.math.abs(dependencyEdgesDelta)})"
                    )
                )
            }
        }

        return ArchitectureDriftResult(
            schemaVersion = "1.1",
            baselineSchemaVersion = baseline.schemaVersion,
            currentSchemaVersion = current.schemaVersion,
            summary = ArchitectureDriftSummary(
                addedFindings = addedFindings.size,
                removedFindings = removedFindings.size,
                newCycles = newCycles.size,
                removedCycles = removedCycles.size,
                changedLayers = changedLayers.size,
                dependencyEdgesDelta = dependencyEdgesDelta
            ),
            changes = changes
        )
    }

    private fun findingKey(finding: ArchitectureFinding): String =
        listOf(finding.ruleId, finding.source, finding.target.orEmpty(), finding.relationship, finding.evidence).joinToString("|")

    private fun cycleKey(cycle: ArchitectureCycle): String = cycle.members.sorted().joinToString("|")
}
