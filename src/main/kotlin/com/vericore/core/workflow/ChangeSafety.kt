package com.vericore.core.workflow

import com.vericore.core.intelligence.ChangedFile
import kotlinx.serialization.Serializable

@Serializable
data class ChangeSafetyResult(
    val schemaVersion: String = "1.0",
    val changedPaths: List<String>,
    val plannedPaths: List<String>,
    val unexpectedPaths: List<String>,
    val deletedPaths: List<String>,
    val status: SafetyStatus,
    val reasons: List<String>
)

@Serializable
enum class SafetyStatus { PASS, REVIEW_REQUIRED, FAIL }

/** Compares actual source changes with an explicit evidence-backed mutation plan. */
object ChangeSafetyAnalyzer {
    private val generatedPrefixes = listOf(".vericore/", ".codecontext/", "output/", "build/", "target/")

    fun verify(changes: List<ChangedFile>, plannedPaths: Collection<String>): ChangeSafetyResult {
        val actual = changes.map { normalize(it.path) }.filterNot(::isGeneratedPath).distinct().sorted()
        val planned = plannedPaths.map(::normalize).filter { it.isNotBlank() && !isGeneratedPath(it) }.distinct().sorted()
        val plannedSet = planned.toSet()
        val unexpected = actual.filterNot(plannedSet::contains)
        val deleted = changes.filter { it.changeType.name == "DELETED" }
            .map { normalize(it.path) }
            .filterNot(::isGeneratedPath)
            .distinct().sorted()

        val reasons = buildList {
            if (actual.isEmpty()) add("No source working-tree changes were detected.")
            if (planned.isEmpty() && actual.isNotEmpty()) add("No explicit source paths were supplied in the engineering plan; source mutation is not permitted without an explicit scope.")
            if (unexpected.isNotEmpty() && planned.isNotEmpty()) add("One or more changed paths are outside the supplied engineering plan.")
            if (deleted.isNotEmpty()) add("Deleted files require explicit review before the change is considered safe.")
        }
        val status = when {
            planned.isEmpty() && actual.isNotEmpty() -> SafetyStatus.FAIL
            unexpected.isNotEmpty() -> SafetyStatus.FAIL
            deleted.isNotEmpty() -> SafetyStatus.REVIEW_REQUIRED
            else -> SafetyStatus.PASS
        }
        return ChangeSafetyResult(
            schemaVersion = "1.0",
            changedPaths = actual,
            plannedPaths = planned,
            unexpectedPaths = unexpected,
            deletedPaths = deleted,
            status = status,
            reasons = reasons
        )
    }

    private fun normalize(path: String): String = path.replace('\\', '/').trim().removePrefix("./")

    private fun isGeneratedPath(path: String): Boolean {
        val normalized = normalize(path).trimStart('/')
        return normalized == ".vericore" || normalized == ".codecontext" ||
            generatedPrefixes.any { normalized.startsWith(it) } ||
            normalized == "output" || normalized == "build" || normalized == "target"
    }
}
