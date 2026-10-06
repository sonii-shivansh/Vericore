package com.vericore.core.planner

import com.vericore.core.ai.GroundedEvidence
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class EngineeringPlanRequest(
    val changeSummary: String,
    val changedPaths: List<String> = emptyList(),
    val evidence: GroundedEvidence,
    val repositoryPath: String = "."
)

@Serializable
data class EngineeringPlanStep(
    val id: String,
    val description: String,
    val rationale: String,
    val evidenceIds: List<String> = emptyList(),
    val verification: String
)

@Serializable
data class EngineeringPlan(
    val schemaVersion: String = "1.0",
    val changeSummary: String,
    val repository: String = "",
    val affectedComponents: List<String>,
    val plannedPaths: List<String> = emptyList(),
    val concerns: List<String>,
    val riskLevel: RiskLevel,
    val steps: List<EngineeringPlanStep>,
    val verificationCommands: List<String>,
    val evidenceIds: List<String>,
    val uncertainties: List<String>,
    val contractFingerprint: String = "",
    val buildSystem: BuildSystem = BuildSystem.UNKNOWN
)

@Serializable
enum class RiskLevel { LOW, MEDIUM, HIGH, UNKNOWN }

/** Builds a bounded, repository-scoped, evidence-backed implementation plan without requiring an AI provider. */
class EngineeringPlanner {
    private val buildSystemDetector = BuildSystemDetector()

    fun plan(request: EngineeringPlanRequest): EngineeringPlan {
        require(request.changeSummary.isNotBlank()) { "changeSummary must not be blank" }
        require(request.changeSummary.length <= 4000) { "changeSummary must not exceed 4000 characters" }
        require(request.changedPaths.size <= 500) { "changedPaths must not exceed 500 entries" }

        val root = File(request.repositoryPath).canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: ${request.repositoryPath}" }
        val repository = root.path
        val windowsPlatform = isWindows()
        val build = buildSystemDetector.detect(root, windowsPlatform)

        val citations = request.evidence.citations.sortedBy { it.id }
        val plannedPaths = request.changedPaths.map(::normalizePath).filter { it.isNotEmpty() && !it.startsWith("<outside-") && !isGeneratedPath(it) }.distinct().sorted().take(100)
        require(plannedPaths.all(::isRepositoryRelative)) { "changedPaths must be repository-relative paths without '..' traversal" }
        val evidencePaths = citations.mapNotNull { it.path }.map(::normalizePath).filter { it.isNotEmpty() && !it.startsWith("<outside-") && !isGeneratedPath(it) }.distinct().sorted()
        val affected = (if (plannedPaths.isNotEmpty()) plannedPaths else evidencePaths).take(100)
        val architecture = citations.filter { it.type.contains("architecture") }
        val hotspots = citations.filter { it.type.contains("hotspot") }
        val concerns = buildList {
            if (architecture.isNotEmpty()) add("Review architecture evidence before implementation.")
            if (hotspots.isNotEmpty()) add("Changed or related components include dependency-centrality hotspots.")
            if (plannedPaths.isNotEmpty() && evidencePaths.any { it !in plannedPaths }) add("Repository evidence references additional files; treat them as context, not planned mutation scope.")
            if (build.system == BuildSystem.UNKNOWN) add("No supported Maven or Gradle build descriptor was detected; verification commands cannot be generated safely.")
            if (build.source == "pom.xml" || build.source == "Gradle build files") add("Repository build tool was inferred from its build descriptor; verify the system tool is installed when no executable wrapper is present.")
            if (build.workingDirectory.isNotEmpty()) add("Build commands are scoped to the detected repository module '${build.workingDirectory}'.")
        }
        val risk = when {
            citations.any { it.type.contains("critical") } -> RiskLevel.HIGH
            hotspots.isNotEmpty() || architecture.isNotEmpty() -> RiskLevel.MEDIUM
            citations.isNotEmpty() -> RiskLevel.LOW
            else -> RiskLevel.UNKNOWN
        }
        val evidenceIds = citations.map { it.id }.distinct().sorted()
        val steps = buildList {
            add(EngineeringPlanStep("step-1", "Review the proposed change against the affected components.", "Establish the concrete repository scope before implementation.", evidenceIds.take(8), "Confirm every changed path belongs to the intended change scope."))
            if (architecture.isNotEmpty()) add(EngineeringPlanStep("step-${size + 1}", "Review architecture boundaries and dependency direction around the change.", "Architecture evidence indicates structural constraints that may affect the implementation.", architecture.map { it.id }.sorted(), "Run architecture analysis and confirm no new forbidden dependency is introduced."))
            if (hotspots.isNotEmpty()) add(EngineeringPlanStep("step-${size + 1}", "Review hotspot dependencies and downstream consumers before changing shared components.", "High-centrality components can expand the change blast radius.", hotspots.map { it.id }.sorted(), "Run impact analysis and inspect affected dependents."))
            add(EngineeringPlanStep("step-${size + 1}", "Implement the smallest change that satisfies the requested behavior.", "Keep the change bounded to the evidence-supported scope.", evidenceIds.take(8), "Run the project's unit and integration test suite from the repository root."))
        }
        val uncertainties = buildList {
            if (citations.isEmpty()) add("No repository evidence was supplied; implementation-specific conclusions cannot be established.")
            if (plannedPaths.isEmpty()) add("No explicit planned paths were supplied; change-scope safety can only evaluate evidence-derived context.")
            if (build.system == BuildSystem.UNKNOWN) add("Verification commands were intentionally omitted because the repository build system could not be identified safely.")
        }
        val verificationRoot = if (build.workingDirectory.isEmpty()) root else File(root, build.workingDirectory).canonicalFile
        val verificationCommands = when (build.system) {
            BuildSystem.MAVEN -> listOf(
                buildCommand(verificationRoot, build.executable!!, "-B test", windowsPlatform),
                buildCommand(verificationRoot, build.executable!!, "-B package -DskipTests", windowsPlatform)
            )
            BuildSystem.GRADLE -> listOf(
                buildCommand(verificationRoot, build.executable!!, "--no-daemon clean test", windowsPlatform),
                buildCommand(verificationRoot, build.executable!!, "--no-daemon build", windowsPlatform)
            )
            BuildSystem.UNKNOWN -> emptyList()
        }
        val provisional = EngineeringPlan(
            changeSummary = request.changeSummary.trim(),
            repository = repository,
            affectedComponents = affected,
            plannedPaths = plannedPaths,
            concerns = concerns.sorted(),
            riskLevel = risk,
            steps = steps,
            verificationCommands = verificationCommands,
            evidenceIds = evidenceIds,
            uncertainties = uncertainties,
            buildSystem = build.system
        )
        return provisional.copy(contractFingerprint = com.vericore.core.workflow.AgentChangeContract.fingerprintFor(provisional))
    }

    private fun buildCommand(root: File, executable: String, arguments: String, windowsPlatform: Boolean): String =
        if (windowsPlatform) {
            "cd /d \"${root.canonicalPath.replace(\"\"\", \"\\\\\\\"\")}\" && $executable $arguments"
        } else {
            "cd '${root.canonicalPath.replace(\"'\", \"'\\\\''\")} ' && $executable $arguments".replace("' &&", "' &&")
        }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")

    private fun normalizePath(path: String): String = path.replace('\\', '/').trim().removePrefix("./")
    private fun isRepositoryRelative(path: String): Boolean = path.isNotEmpty() && !path.startsWith('/') && !path.contains(":/") && path != ".." && !path.startsWith("../") && !path.contains("/../")
    private fun isGeneratedPath(path: String): Boolean {
        val normalized = normalizePath(path).trimStart('/')
        return normalized == ".vericore" || normalized.startsWith(".vericore/") ||
            normalized == ".codecontext" || normalized.startsWith(".codecontext/") ||
            normalized == "output" || normalized.startsWith("output/") ||
            normalized == "build" || normalized.startsWith("build/") ||
            normalized == "target" || normalized.startsWith("target/")
    }
}
