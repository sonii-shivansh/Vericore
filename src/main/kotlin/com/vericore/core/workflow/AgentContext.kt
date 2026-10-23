package com.vericore.core.workflow

import kotlinx.serialization.Serializable

/**
 * Stable, privacy-conscious identity metadata shared by coding-agent adapters.
 * IDs are opaque correlation values; never place prompts, credentials, or source contents here.
 * The source is assigned by the integration boundary, not by the agent.
 */
@Serializable
data class AgentContext(
    val schemaVersion: String = "1.0",
    val provider: String,
    val agentId: String? = null,
    val sessionId: String? = null,
    val eventId: String? = null,
    val source: String
) {
    init {
        require(schemaVersion == "1.0") { "Unsupported agent context schema version." }
        require(provider.matches(Regex("^[a-z0-9][a-z0-9._-]{0,63}$"))) {
            "Agent provider must be a lowercase identifier of 1-64 characters."
        }
        validateOpaqueId("agentId", agentId)
        validateOpaqueId("sessionId", sessionId)
        validateOpaqueId("eventId", eventId)
        require(source in setOf("cli", "mcp", "github-action", "rest", "other")) {
            "Agent context source must be cli, mcp, github-action, rest, or other."
        }
    }

    fun canonicalForm(): String = listOf(
        schemaVersion, provider, agentId.orEmpty(), sessionId.orEmpty(), eventId.orEmpty(), source
    ).joinToString("\n")

    companion object {
        fun create(
            provider: String,
            agentId: String? = null,
            sessionId: String? = null,
            eventId: String? = null,
            source: String
        ): AgentContext = AgentContext(
            provider = provider.trim().lowercase(),
            agentId = normalizeOpaqueId("agentId", agentId),
            sessionId = normalizeOpaqueId("sessionId", sessionId),
            eventId = normalizeOpaqueId("eventId", eventId),
            source = source
        )

        private fun normalizeOpaqueId(field: String, value: String?): String? {
            val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            validateOpaqueId(field, normalized)
            return normalized
        }

        private fun validateOpaqueId(field: String, value: String?) {
            require(value == null || (value.isNotBlank() && value.length <= 256 && value.none { Character.isISOControl(it) })) {
                "$field must be 1-256 non-control characters when supplied."
            }
        }
    }
}
