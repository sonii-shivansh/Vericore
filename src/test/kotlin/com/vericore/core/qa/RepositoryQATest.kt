package com.vericore.core.qa

import com.vericore.core.ai.EvidenceCitation
import com.vericore.core.ai.GroundedEvidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertFalse(retriever.matchesEntity("Main", mixedEvidence.citations[0]), "Should not match src/main path segment for PaymentService")
        assertTrue(retriever.matchesEntity("Main", mixedEvidence.citations[1]), "Should match Main.kt")
    }

    @Test
    fun `does not match directory segments like src test when entity is Test`() {
        val mixedEvidence = GroundedEvidence(
            citations = listOf(
                EvidenceCitation("file.1", "file-graph", path = "src/test/kotlin/PaymentServiceTest.kt", detail = "Located in src/test/kotlin"),
                EvidenceCitation("file.2", "file-graph", path = "src/test/kotlin/Test.kt", detail = "Test runner")
            )
        )
        val retriever = RepositoryEvidenceRetriever()
        assertFalse(retriever.matchesEntity("Test", mixedEvidence.citations[0]), "Should not match src/test path segment for PaymentServiceTest")
        assertTrue(retriever.matchesEntity("Test", mixedEvidence.citations[1]), "Should match Test.kt")
    }

    @Test
    fun `general question with unrelated citations produces insufficient evidence`() {
        val question = RepositoryQuestionClassifier.classify("What is the capital of France?")
        val retriever = RepositoryEvidenceRetriever()
        val result = retriever.retrieve(question, evidence)

        assertTrue(result.insufficientEvidence, "Unrelated general question must produce insufficient evidence")
        assertTrue(result.evidence.isEmpty(), "No unrelated citations should pass the relevance threshold")
    }

    @Test
    fun `question with no matching citations produces insufficient evidence`() {
        val question = RepositoryQuestionClassifier.classify("Where is `QuantumTeleporter` implemented?")
        val retriever = RepositoryEvidenceRetriever()
        val result = retriever.retrieve(question, evidence)

        assertTrue(result.insufficientEvidence, "Nonexistent entity must produce insufficient evidence")
        assertTrue(result.evidence.isEmpty())
    }

    @Test
    fun `correct Main entrypoint is ranked with high relevance`() {
        val mainEvidence = GroundedEvidence(
            citations = listOf(
                EvidenceCitation("file.other", "file-graph", path = "src/main/kotlin/Utils.kt", detail = "Utility helpers in src/main"),
                EvidenceCitation("file.main", "file-graph", path = "src/main/kotlin/Main.kt", detail = "Application entrypoint class Main")
            )
        )
        val question = RepositoryQuestionClassifier.classify("Where is the `Main` entrypoint located?")
        val result = RepositoryEvidenceRetriever().retrieve(question, mainEvidence)

        assertFalse(result.insufficientEvidence)
        assertEquals(1, result.evidence.size)
        assertEquals("file.main", result.evidence.first().citation.id)
        assertTrue(result.evidence.first().relevance >= 100)
    }

    @Test
    fun `question with genuine filename symbol and detail evidence matches correctly`() {
        val qaEvidence = GroundedEvidence(
            citations = listOf(
                EvidenceCitation("service.auth", "file-graph", path = "src/AuthService.kt", detail = "Handles JWT token verification and user login"),
                EvidenceCitation("service.billing", "file-graph", path = "src/BillingService.kt", detail = "Processes invoice payments and credit card charges")
            )
        )
        val question = RepositoryQuestionClassifier.classify("Where is JWT token verification handled?")
        val result = RepositoryEvidenceRetriever().retrieve(question, qaEvidence)

        assertFalse(result.insufficientEvidence)
        assertEquals("service.auth", result.evidence.first().citation.id)
    }
}
