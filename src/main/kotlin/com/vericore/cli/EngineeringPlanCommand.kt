package com.vericore.cli

import com.vericore.core.ai.GroundedEvidence
import com.vericore.core.planner.ChangeSpecification
import com.vericore.core.planner.EngineeringPlanRequest
import com.vericore.core.planner.EngineeringPlanner
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.multiple
import java.io.File
import kotlinx.serialization.json.Json

/** Generates a deterministic, repository-scoped, evidence-backed engineering plan. */
class EngineeringPlanCommand : CliktCommand(
    name = "plan",
    help = "Generate an evidence-backed engineering plan for a proposed change"
) {
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
    private val criterionEvidence by option("--criterion-evidence", help = "Bind a criterion to a prepared evidence citation or verification-command-NNN ID as <criterion-id>=<evidence-id>; repeatable").multiple()
    private val criterionEvidenceRules by option("--criterion-evidence-rule", help = "Set a criterion evidence rule as <criterion-id>=<rule>; repeatable").multiple()
    private val evidenceFile by option("--evidence", help = "Path to a grounded evidence JSON artifact; relative paths resolve from the repository")
    private val outputFile by option("--output", help = "Write the plan JSON to this path instead of stdout")

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
        val json = Json { prettyPrint = true; encodeDefaults = true }
            .encodeToString(plan)

        if (outputFile != null) {
            val output = resolveOutput(root, outputFile!!)
            output.parentFile?.mkdirs()
            output.writeText(json)
            echo("🧭 Engineering plan: ${output.path}")
            echo("   Risk: ${plan.riskLevel}")
            echo("   Affected components: ${plan.affectedComponents.size}")
            echo("   Planned paths: ${plan.plannedPaths.size}")
        } else {
            echo(json)
        }
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
