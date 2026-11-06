package com.vericore.cli

import com.vericore.core.ai.GroundedEvidence
import com.vericore.core.planner.ChangeSpecification
import com.vericore.core.planner.EngineeringPlan
import com.vericore.core.planner.EngineeringPlanRequest
import com.vericore.core.planner.EngineeringPlanner
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Generates a deterministic, repository-scoped, evidence-backed engineering plan. */
class EngineeringPlanCommand : CliktCommand(
    name = "plan"
) {
    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Generate an evidence-backed engineering plan for a proposed change"
    private val changeSummary by argument("change-summary", help = "Short description of the proposed change")
    private val repositoryPath by option(
        "--repository",
        help = "Repository path containing the evidence artifact and planned change"
    ).default(".")
    private val changedPaths by option(
        "--changed",
        help = "Repository-relative file path(s) explicitly planned for the change"
    ).multiple()
    private val requiredBehaviors by option("--require-behavior", help = "Behavior this change must provide").multiple()
    private val forbiddenBehaviors by option("--forbid-behavior", help = "Behavior this change must not introduce").multiple()
    private val protectedInvariants by option("--protect-invariant", help = "Existing invariant this change must preserve").multiple()
    private val acceptanceCriteria by option("--acceptance-criterion", help = "Observable criterion for accepting this change").multiple()
    private val requiredEvidence by option("--required-evidence", help = "Evidence that must be supplied/reviewed for acceptance").multiple()
    private val criterionEvidence by option("--criterion-evidence", help = "Bind a criterion to evidence, a verification-command-NNN ID, or junit-test-case:<class>#<name> as <criterion-id>=<evidence-id>; repeatable").multiple()
    private val criterionEvidenceRules by option("--criterion-evidence-rule", help = "Set a criterion evidence rule (ALL_BOUND_COMMANDS_SUCCEEDED or ALL_BOUND_JUNIT_CASES_PASSED) as <criterion-id>=<rule>; repeatable").multiple()
    private val evidenceFile by option("--evidence", help = "Path to a grounded evidence JSON artifact; relative paths resolve from the repository")
    private val outputFile by option("--output", help = "Write the plan JSON to this path instead of stdout")
    private val format by option("--format", help = "Output format (human, json)")
    private val jsonOutput by option("--json", help = "Write machine-readable plan JSON to stdout (alias for --format json)").flag()

    override fun run() {
        val root = File(repositoryPath).canonicalFile
        require(root.isDirectory) { "Repository path is not a directory: $repositoryPath" }

        val evidence = Json { ignoreUnknownKeys = true }
            .decodeFromString<GroundedEvidence>(resolveInput(root, evidenceFile ?: "output/grounded-evidence.json").readText())

        val plan = EngineeringPlanner().plan(
            EngineeringPlanRequest(
                changeSummary = changeSummary,
                changedPaths = changedPaths.toList(),
                evidence = evidence,
                repositoryPath = root.path,
                specification = ChangeSpecification(
                    requiredBehaviors = requiredBehaviors,
                    forbiddenBehaviors = forbiddenBehaviors,
                    protectedInvariants = protectedInvariants,
                    acceptanceCriteria = acceptanceCriteria,
                    requiredEvidence = requiredEvidence,
                    evidenceBindings = parseCriterionEvidenceBindings(criterionEvidence, criterionEvidenceRules)
                )
            )
        )

        val defaultOutput = root.resolve("output/engineering-plan.json")
        defaultOutput.parentFile?.mkdirs()
        defaultOutput.writeText(JsonEnvelope.json.encodeToString(plan))

        val outFormat = OutputFormat.resolve(format, jsonOutput)
        if (outFormat == OutputFormat.JSON) {
            val envelope = CommandEnvelope(
                schemaVersion = "1.0",
                command = "plan",
                status = CommandStatus.PASS,
                summary = "Engineering plan for '${plan.changeSummary}': risk ${plan.riskLevel}, ${plan.plannedPaths.size} planned paths, ${plan.affectedComponents.size} affected components",
                repository = RepositoryInfo(name = root.name, path = root.path),
                data = plan,
                findings = plan.affectedComponents.map { comp ->
                    EnvelopeFinding(
                        level = "INFO",
                        message = "Affected component in planned change scope",
                        path = repositoryRelativePath(comp, root)
                    )
                },
                artifacts = listOf(
                    EnvelopeArtifact("engineering-plan", defaultOutput.path)
                ),
                execution = ExecutionInfo(exitCode = 0)
            )
            val jsonText = JsonEnvelope.encode(envelope)
            if (outputFile != null) {
                val dest = resolveOutput(root, outputFile!!)
                dest.parentFile?.mkdirs()
                dest.writeText(jsonText)
            }
            echo(jsonText)
            return
        }

        val resolvedTarget = if (outputFile != null) {
            val dest = resolveOutput(root, outputFile!!)
            dest.parentFile?.mkdirs()
            dest.writeText(JsonEnvelope.json.encodeToString(plan))
            dest
        } else defaultOutput

        echo(renderPlanSummary(plan, root, resolvedTarget.path))
    }

    private fun resolveInput(root: File, value: String): File {
        val candidate = File(value).let { if (it.isAbsolute) it else File(root, value) }.canonicalFile
        require(candidate.toPath().startsWith(root.toPath())) { "Evidence path must be inside repository: $value" }
        require(candidate.isFile) { "Evidence file does not exist: ${candidate.path}" }
        return candidate
    }

    private fun resolveOutput(root: File, value: String): File {
        val candidate = File(value).let { if (it.isAbsolute) it else File(root, value) }.canonicalFile
        require(candidate.toPath().startsWith(root.toPath())) { "Output path must be inside repository: $value" }
        return candidate
    }
}

internal fun renderPlanSummary(
    plan: EngineeringPlan,
    root: File,
    artifactPath: String? = null
): String {
    val plannedPathsStr = if (plan.plannedPaths.isEmpty()) {
        "None (read-only / repository understanding)"
    } else {
        plan.plannedPaths.joinToString(", ") { repositoryRelativePath(it, root) }
    }
    val sections = buildList {
        artifactPath?.let { add(HumanCommandOutput.section("Artifact", "Engineering plan: $it")) }
        add(
            HumanCommandOutput.section(
                "Summary",
                "Change: ${plan.changeSummary}",
                "Risk: ${plan.riskLevel}",
                "Planned mutation scope: $plannedPathsStr",
                "Affected components: ${plan.affectedComponents.size}"
            )
        )
        if (plan.specification.requiredBehaviors.isNotEmpty() || plan.specification.acceptanceCriteria.isNotEmpty()) {
            add(
                HumanCommandOutput.section(
                    "Specification",
                    "Required behaviors: ${plan.specification.requiredBehaviors.size}",
                    "Forbidden behaviors: ${plan.specification.forbiddenBehaviors.size}",
                    "Protected invariants: ${plan.specification.protectedInvariants.size}",
                    "Acceptance criteria: ${plan.specification.acceptanceCriteria.size}"
                )
            )
        }
    }
    return HumanCommandOutput.render(
        title = "🧭 Engineering Plan",
        status = CommandStatus.PASS,
        sections = sections,
        nextStep = "Run `vericore prepare \"${plan.changeSummary}\"` to create the immutable change contract."
    )
}
