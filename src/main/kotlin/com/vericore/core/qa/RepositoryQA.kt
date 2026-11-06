package com.vericore.core.qa

import com.vericore.core.ai.EvidenceCitation
import com.vericore.core.ai.GroundedEvidence
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
enum class QuestionIntent {
    DEPENDENCY,
    RISK,
    IMPACT,
    ARCHITECTURE,
    PR_CHANGE,
    TEST,
    GENERAL,
    UNKNOWN
}

@Serializable
data class RepositoryQuestion(
    val question: String,
    val intent: QuestionIntent,
    val entity: String? = null
)

@Serializable
data class RetrievedEvidence(
    val citation: EvidenceCitation,
    val relevance: Int
)

@Serializable
data class RepositoryQAResult(
    val question: RepositoryQuestion,
    val evidence: List<RetrievedEvidence>,
    val insufficientEvidence: Boolean
)

object RepositoryQuestionClassifier {
    private val dependencyWords = setOf("depend", "dependency", "dependencies", "uses", "imports", "call", "calls")
    private val riskWords = setOf("risk", "danger", "hotspot", "critical", "risky")
    private val impactWords = setOf("impact", "break", "affected", "affect", "blast radius")
    private val architectureWords = setOf("architecture", "architectural", "architecturally", "layer", "boundary", "cycle", "module", "coupling")
    private val prWords = setOf("pr", "pull request", "change", "changed", "diff")
    private val testWords = setOf("test", "tests", "testing", "coverage", "verify")

    private val questionWords = setOf(
        "what", "which", "who", "where", "when", "why", "how", "can", "could", "does", "do", "is", "are", "should"
    )

    fun classify(question: String): RepositoryQuestion {
        require(question.isNotBlank()) { "question must not be blank" }
        require(question.length <= 2000) { "question must not exceed 2000 characters" }
        val normalized = question.lowercase()
        val intent = when {
            prWords.any { normalized.contains(it) } -> QuestionIntent.PR_CHANGE
            architectureWords.any { normalized.contains(it) } -> QuestionIntent.ARCHITECTURE
            impactWords.any { normalized.contains(it) } -> QuestionIntent.IMPACT
            riskWords.any { normalized.contains(it) } -> QuestionIntent.RISK
            dependencyWords.any { normalized.contains(it) } -> QuestionIntent.DEPENDENCY
            testWords.any { normalized.contains(it) } -> QuestionIntent.TEST
            normalized.isNotBlank() -> QuestionIntent.GENERAL
            else -> QuestionIntent.UNKNOWN
        }
        return RepositoryQuestion(question.trim(), intent, extractEntity(question))
    }

    private fun extractEntity(question: String): String? {
        Regex("`([^`]+)`").find(question)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { return it }

        return Regex("\\b[A-Z][A-Za-z0-9_$.]{2,}\\b")
            .findAll(question)
            .map { it.value }
            .firstOrNull { it.lowercase() !in questionWords }
    }
}

class RepositoryEvidenceRetriever {
    companion object {
        const val MIN_RELEVANCE_THRESHOLD = 20

        private val stopWords = setOf(
            "what", "which", "who", "where", "when", "why", "how", "can", "could", "does", "do", "is", "are", "should",
            "the", "a", "an", "this", "that", "these", "those", "for", "from", "with", "about", "into", "through",
            "over", "under", "above", "below", "there", "here", "all", "any", "some", "none", "not", "and", "or", "but",
            "in", "on", "at", "to", "of", "by"
        )
    }

    fun retrieve(question: RepositoryQuestion, evidence: GroundedEvidence, maxResults: Int = 8): RepositoryQAResult {
        require(maxResults in 1..32) { "maxResults must be between 1 and 32" }
        val ranked = evidence.citations.map { citation ->
            RetrievedEvidence(citation, score(question, citation))
        }.filter { it.relevance >= MIN_RELEVANCE_THRESHOLD }
            .sortedWith(compareByDescending<RetrievedEvidence> { it.relevance }.thenBy { it.citation.id })
            .take(maxResults)
        return RepositoryQAResult(question, ranked, ranked.isEmpty())
    }

    internal fun score(question: RepositoryQuestion, citation: EvidenceCitation): Int {
        var score = 0
        if (matchesEntity(question.entity, citation)) {
            score += 100
        }

        val keywords = extractKeywords(question.question)
        var keywordMatches = 0
        for (kw in keywords) {
            if (matchesKeyword(kw, citation)) {
                keywordMatches++
            }
        }
        score += keywordMatches * 20

        val intentScore = when (question.intent) {
            QuestionIntent.RISK -> if (citation.type.contains("hotspot") || citation.type.contains("risk")) 40 else 0
            QuestionIntent.DEPENDENCY -> if (citation.type.contains("file-graph") || citation.type.contains("hotspot")) 35 else 0
            QuestionIntent.ARCHITECTURE -> when {
                citation.type.contains("architecture") -> 50
                citation.type.contains("hotspot") -> 30
                citation.type.contains("file-graph") -> 15
                else -> 0
            }
            QuestionIntent.IMPACT, QuestionIntent.PR_CHANGE -> if (citation.type.contains("file-graph") || citation.type.contains("hotspot")) 25 else 0
            QuestionIntent.TEST -> if (citation.type.contains("test") || citation.type.contains("file-graph")) 25 else 0
            QuestionIntent.GENERAL -> 0
            QuestionIntent.UNKNOWN -> 0
        }
        score += intentScore

        return score
    }

    internal fun extractKeywords(text: String): Set<String> {
        return Regex("[A-Za-z0-9_$-]{3,}")
            .findAll(text)
            .map { it.value.lowercase() }
            .filter { it !in stopWords }
            .toSet()
    }

    internal fun matchesKeyword(keyword: String, citation: EvidenceCitation): Boolean {
        if (keyword.isBlank()) return false
        val kw = keyword.lowercase()
        if (kw == "main" || kw == "test") {
            val fileName = citation.path?.replace('\\', '/')
                ?.substringAfterLast('/')?.substringBeforeLast('.')?.lowercase()
            if (fileName == kw) return true

            val wordRegex = Regex("\\b${Regex.escape(kw)}\\b", RegexOption.IGNORE_CASE)
            for (match in wordRegex.findAll(citation.detail)) {
                if (isPathSegment(citation.detail, match.range.first, match.range.last + 1)) continue
                return true
            }
            return false
        }

        if (citation.id.lowercase().contains(kw)) return true
        citation.path?.let { p ->
            val normPath = p.replace('\\', '/')
            val fileName = normPath.substringAfterLast('/').lowercase()
            if (fileName.contains(kw)) return true
        }
        if (citation.relatedPaths.any { rp -> rp.replace('\\', '/').substringAfterLast('/').lowercase().contains(kw) }) return true
        if (citation.detail.lowercase().contains(kw)) return true
        return false
    }

    internal fun matchesEntity(rawEntity: String?, citation: EvidenceCitation): Boolean {
        if (rawEntity.isNullOrBlank()) return false
        val entity = rawEntity.trim('`', ' ')
        if (entity.isBlank()) return false

        // Check exact match in citation ID tokens (separated by colons, hashes, dots)
        val idTokens = citation.id.split(':', '#', '.')
        if (idTokens.any { it.equals(entity, ignoreCase = true) }) return true

        // Check path: match filename or filename without extension
        citation.path?.let { p ->
            val normPath = p.replace('\\', '/')
            val fileName = normPath.substringAfterLast('/')
            val nameWithoutExt = fileName.substringBeforeLast('.')
            if (nameWithoutExt.equals(entity, ignoreCase = true) || fileName.equals(entity, ignoreCase = true)) {
                return true
            }
            if (entity.contains('/') || entity.contains('\\')) {
                val normEntity = entity.replace('\\', '/').trim('/')
                if (normPath.contains(normEntity, ignoreCase = true)) return true
            }
        }

        // Check related paths (e.g. imported or dependent files)
        if (citation.relatedPaths.any { rp ->
            val normRp = rp.replace('\\', '/')
            val fileName = normRp.substringAfterLast('/')
            val nameWithoutExt = fileName.substringBeforeLast('.')
            nameWithoutExt.equals(entity, ignoreCase = true) || fileName.equals(entity, ignoreCase = true)
        }) return true

        // Check word-boundary match in detail, avoiding false positive on path segments like /main/ or /test/
        val wordRegex = Regex("\\b${Regex.escape(entity)}\\b", RegexOption.IGNORE_CASE)
        val detailMatches = wordRegex.findAll(citation.detail)
        for (match in detailMatches) {
            val start = match.range.first
            val end = match.range.last + 1
            if (isPathSegment(citation.detail, start, end)) {
                continue
            }
            return true
        }

        return false
    }

    private fun isPathSegment(text: String, start: Int, end: Int): Boolean {
        val charBefore = if (start > 0) text[start - 1] else ' '
        val charAfter = if (end < text.length) text[end] else ' '
        val prefix = if (start >= 4) text.substring(start - 4, start).lowercase() else ""

        if (prefix == "src/" || prefix == "src\\") return true
        if ((charBefore == '/' || charBefore == '\\') && (charAfter == '/' || charAfter == '\\')) return true
        if ((charBefore == '/' || charBefore == '\\') && charAfter != '.') return true
        if (charAfter == '/' || charAfter == '\\') return true

        return false
    }
}
