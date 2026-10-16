package com.vericore.core.workflow

import com.vericore.core.planner.ChangeSpecification
import com.vericore.core.planner.EngineeringPlan
import kotlinx.serialization.Serializable
import java.io.File
import java.security.MessageDigest

@Serializable
data class AgentChangeContract(
    val schemaVersion: String = "2.0",
    val changeSummary: String,
    val repository: String,
    val preparedHead: String,
    val plannedPaths: List<String>,
    val expectedChangeTypes: Map<String, List<String>> = emptyMap(),
    val expectedComponents: List<String>,
    val verificationCommands: List<String>,
    val evidenceIds: List<String>,
    val architectureExpectations: List<String> = emptyList(),
    val fingerprint: String,
    val specification: ChangeSpecification = ChangeSpecification(),
    /** Missing on legacy contracts. Verification must not infer a prepare-time baseline for them. */
    val baselineManifest: CandidateStateManifest? = null
) {
    companion object {
        fun fromPlan(plan: EngineeringPlan): AgentChangeContract = fromPlan(plan, plan.repository, "")

        fun fromPlan(
            plan: EngineeringPlan,
            repositoryPath: String,
            preparedHead: String,
            baselineManifest: CandidateStateManifest? = null
        ): AgentChangeContract {
            val requestedRepository = repositoryPath.ifBlank { plan.repository }
            val repository = requestedRepository.takeIf { it.isNotBlank() }?.let { File(it).canonicalPath } ?: ""
            val paths = plan.plannedPaths.map(::normalize).distinct().sorted()
            val components = plan.affectedComponents.map(::normalize).distinct().sorted()
            val commands = plan.verificationCommands.distinct().sorted()
            val evidence = plan.evidenceIds.distinct().sorted()
            val expectations = plan.concerns.distinct().sorted()
            val specification = plan.specification.normalized()
            val changeTypes = emptyMap<String, List<String>>()
            val schemaVersion = if (baselineManifest == null) "2.0" else "3.0"
            val canonical = canonicalize(
                schemaVersion, plan.changeSummary, repository, preparedHead, paths,
                changeTypes, components, commands, evidence, expectations, specification,
                baselineManifest?.digest
            )
            return AgentChangeContract(
                schemaVersion = schemaVersion,
                changeSummary = plan.changeSummary.trim(),
                repository = repository,
                preparedHead = preparedHead,
                plannedPaths = paths,
                expectedChangeTypes = changeTypes,
                expectedComponents = components,
                verificationCommands = commands,
                evidenceIds = evidence,
                architectureExpectations = expectations,
                fingerprint = sha256(canonical),
                specification = specification,
                baselineManifest = baselineManifest
            )
        }

        fun fingerprintFor(plan: EngineeringPlan): String = fromPlan(plan).fingerprint

        fun fingerprintFor(contract: AgentChangeContract): String = sha256(
            canonicalize(
                contract.schemaVersion,
                contract.changeSummary,
                contract.repository,
                contract.preparedHead,
                contract.plannedPaths.map(::normalize).distinct().sorted(),
                contract.expectedChangeTypes.mapValues { (_, values) -> values.distinct().sorted() }.toSortedMap(),
                contract.expectedComponents.map(::normalize).distinct().sorted(),
                contract.verificationCommands.distinct().sorted(),
                contract.evidenceIds.distinct().sorted(),
                contract.architectureExpectations.distinct().sorted(),
                contract.specification,
                contract.baselineManifest?.digest
            )
        )

        private fun canonicalize(
            schemaVersion: String,
            summary: String,
            repository: String,
            preparedHead: String,
            paths: List<String>,
            changeTypes: Map<String, List<String>>,
            components: List<String>,
            commands: List<String>,
            evidence: List<String>,
            expectations: List<String>,
            specification: ChangeSpecification = ChangeSpecification(),
            baselineDigest: String? = null
        ): String {
            val legacyCanonical = listOf(
                schemaVersion,
                summary.trim(),
                repository,
                preparedHead,
                paths.joinToString("\n"),
                changeTypes.entries.sortedBy { it.key }.joinToString("\n") { (path, types) -> "$path=${types.joinToString(",")}" },
                components.joinToString("\n"),
                commands.joinToString("\n"),
                evidence.joinToString("\n"),
                expectations.joinToString("\n")
            ).joinToString("\n---\n")
            // Preserve historical fingerprints for legacy contracts whose specification and baseline are empty.
            val withSpecification = if (specification.isEmpty()) legacyCanonical
            else "$legacyCanonical\n---\nSPECIFICATION\n${specification.canonicalForm()}"
            return if (baselineDigest == null) withSpecification
            else "$withSpecification\n---\nCANDIDATE-BASELINE\n$baselineDigest"
        }

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        private fun normalize(path: String): String = path.replace('\\', '/').trim().removePrefix("./")
    }
}

@Serializable
data class AgentChangeContractResult(
    val contract: AgentChangeContract,
    val valid: Boolean,
    val reasons: List<String> = emptyList()
)
