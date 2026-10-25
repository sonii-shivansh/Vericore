package com.vericore.cli

import com.vericore.core.planner.ChangeSpecification
import com.vericore.core.workflow.AgentContext
import com.vericore.core.workflow.EngineeringPreparation
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Creates reusable evidence, plan, and repository-bound change-contract artifacts before implementation. */
class PrepareCommand : CliktCommand(name = "prepare", help = "Prepare an evidence-backed change plan before coding") {
    private val changeSummary by argument("change-summary", help = "Short description of the proposed change")
    private val path by option("--path", help = "Repository path").default(".")
    private val output by option("--output", help = "Preparation artifact path")
    private val planOutput by option("--plan-output", help = "Engineering plan artifact path")
    private val contractOutput by option("--contract-output", help = "Agent change contract artifact path")
    private val agentProvider by option("--agent-provider", help = "Coding agent provider slug, e.g. cursor, claude-code, codex")
    private val agentId by option("--agent-id", help = "Opaque stable agent identity; do not include secrets")
    private val agentSessionId by option("--agent-session-id", help = "Opaque agent session correlation ID")
    private val agentEventId by option("--agent-event-id", help = "Opaque caller event/correlation ID")
    private val plannedPaths by option("--planned-path", help = "Repository-relative path the agent expects to modify").multiple()
    private val requiredBehaviors by option("--require-behavior", help = "Behavior this change must provide").multiple()
    private val forbiddenBehaviors by option("--forbid-behavior", help = "Behavior this change must not introduce").multiple()
    private val protectedInvariants by option("--protect-invariant", help = "Existing invariant this change must preserve").multiple()
    private val acceptanceCriteria by option("--acceptance-criterion", help = "Observable criterion for accepting this change").multiple()
    private val requiredEvidence by option("--required-evidence", help = "Evidence that must be supplied/reviewed for acceptance").multiple()
    private val criterionEvidence by option("--criterion-evidence", help = "Bind a criterion to evidence, a verification-command-NNN ID, or junit-test-case:<class>#<name> as <criterion-id>=<evidence-id>; repeatable").multiple()
    private val criterionEvidenceRules by option("--criterion-evidence-rule", help = "Set a criterion evidence rule (ALL_BOUND_COMMANDS_SUCCEEDED or ALL_BOUND_JUNIT_CASES_PASSED) as <criterion-id>=<rule>; repeatable").multiple()

    override fun run() {
        val root = File(path).canonicalFile
        val specification = ChangeSpecification(
            requiredBehaviors = requiredBehaviors,
            forbiddenBehaviors = forbiddenBehaviors,
            protectedInvariants = protectedInvariants,
            acceptanceCriteria = acceptanceCriteria,
            requiredEvidence = requiredEvidence,
            evidenceBindings = parseCriterionEvidenceBindings(criterionEvidence, criterionEvidenceRules)
        )
        val agentMetadataSupplied = listOf(agentId, agentSessionId, agentEventId).any { !it.isNullOrBlank() }
        val agentContext = if (!agentProvider.isNullOrBlank()) {
            AgentContext.create(agentProvider!!, agentId, agentSessionId, agentEventId, if (System.getenv("GITHUB_ACTIONS") == "true") "github-action" else "cli")
        } else {
            require(!agentMetadataSupplied) { "--agent-provider is required when agent identity/session/event metadata is supplied." }
            null
        }
        val json = Json { prettyPrint = true; encodeDefaults = true }
        val artifactPath = output ?: root.resolve("output/engineering-preparation.json").path
        val artifact = File(artifactPath).let { if (it.isAbsolute) it else root.resolve(it.path) }
        val planPath = planOutput ?: root.resolve("output/engineering-plan.json").path
        val planFile = File(planPath).let { if (it.isAbsolute) it else root.resolve(it.path) }
        val contractPath = contractOutput ?: root.resolve("output/agent-change-contract.json").path
        val contractFile = File(contractPath).let { if (it.isAbsolute) it else root.resolve(it.path) }
        val rootPath = root.toPath()
        val excludedPaths = listOf(artifact, planFile, contractFile).mapNotNull { file ->
            val canonical = file.canonicalFile.toPath()
            if (canonical.startsWith(rootPath)) rootPath.relativize(canonical).toString().replace(File.separatorChar, '/') else null
        }.distinct().sorted()
        val result = runBlocking { EngineeringPreparation.prepare(root.path, changeSummary, plannedPaths, specification, excludedPaths, agentContext) }
        artifact.apply { parentFile?.mkdirs() }.writeText(json.encodeToString(result))
        planFile.apply { parentFile?.mkdirs() }.writeText(json.encodeToString(result.plan))
        contractFile.apply { parentFile?.mkdirs() }.writeText(json.encodeToString(result.contract))
        echo("Engineering context: ${artifact.path}")
        echo("Engineering plan: ${planFile.path}")
        echo("Agent change contract: ${contractFile.path}")
        echo("Risk: ${result.plan.riskLevel}")
        echo("Affected components: ${result.plan.affectedComponents.size}")
    }
}
