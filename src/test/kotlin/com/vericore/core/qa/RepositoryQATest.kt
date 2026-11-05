package com.vericore.core.qa

import com.vericore.core.ai.EvidenceCitation
import com.vericore.core.ai.GroundedEvidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepositoryQATest {
    private val evidence = GroundedEvidence(
        citations = listOf(
            EvidenceCitation("repo.metrics", "repository-metrics", detail = "Repository metrics"),
            EvidenceCitation("hotspot.1", "hotspot", path = "src/PaymentService.kt", detail = "Dependency-centrality hotspot"),
            EvidenceCitation("architecture.summary", "architecture-summary", detail = "Architecture snapshot reports cycles=false")
        )
    )

    @Test
    fun `classifies architecture question and extracts quoted entity`() {
        val question = RepositoryQuestionClassifier.classify("Why is `PaymentService` a high coupling hotspot?")
        assertEquals(QuestionIntent.ARCHITECTURE, question.intent)
        assertEquals("PaymentService", question.entity)
    }

    @Test
    fun `does not treat question words as entities`() {
        val question = RepositoryQuestionClassifier.classify("Which files are the main architectural hotspots?")
        assertEquals(QuestionIntent.ARCHITECTURE, question.intent)
        assertEquals(null, question.entity)
    }

    @Test
    fun `ranks exact entity evidence first`() {
        val question = RepositoryQuestionClassifier.classify("What is the risk of `PaymentService`?")
        val result = RepositoryEvidenceRetriever().retrieve(question, evidence)
        assertEquals("hotspot.1", result.evidence.first().citation.id)
        assertTrue(
            result.evidence.first().relevance >= result.evidence.drop(1).maxOfOrNull { it.relevance } ?: 0
        )
        assertTrue(!result.insufficientEvidence)
    }

    @Test
    fun `architecture hotspot questions retain hotspot evidence`() {
        val question = RepositoryQuestionClassifier.classify("Which files are the main architectural hotspots?")
        val result = RepositoryEvidenceRetriever().retrieve(question, evidence)

        assertEquals(QuestionIntent.ARCHITECTURE, result.question.intent)
        assertTrue(result.evidence.any { it.citation.id == "hotspot.1" })
        assertTrue(result.evidence.any { it.citation.id == "architecture.summary" })
        assertTrue(!result.insufficientEvidence)
    }

    @Test
    fun `returns insufficient evidence when no citation matches intent`() {
        val question = RepositoryQuestionClassifier.classify("Which tests should I run?")
        val result = RepositoryEvidenceRetriever().retrieve(
            question,
            GroundedEvidence(citations = emptyList())
        )
        assertTrue(result.insufficientEvidence)
        assertTrue(result.evidence.isEmpty())
    }

    @Test
    fun `retrieval ordering is deterministic`() {
        val question = RepositoryQuestionClassifier.classify("Explain the architecture")
        val retriever = RepositoryEvidenceRetriever()
        assertEquals(retriever.retrieve(question, evidence), retriever.retrieve(question, evidence))
    }

    @Test
    fun `does not match directory segments like src main when entity is Main`() {
        val mixedEvidence = GroundedEvidence(
            citations = listOf(
                EvidenceCitation("file.1", "file-graph", path = "src/main/kotlin/PaymentService.kt", detail = "PaymentService in src/main/kotlin"),
                EvidenceCitation("file.2", "file-graph", path = "src/main/kotlin/Main.kt", detail = "Main entrypoint in src/main/kotlin")
            )
        )
        val retriever = RepositoryEvidenceRetriever()
        assertTrue(!retriever.matchesEntity("Main", mixedEvidence.citations[0]), "Should not match src/main path segment for PaymentService")
        assertTrue(retriever.matchesEntity("Main", mixedEvidence.citations[1]), "Should match Main.kt")
    }
}
