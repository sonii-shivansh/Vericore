package com.vericore.core.workflow

import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Finds only conventional Gradle/Maven JUnit XML outputs and accepts a report only when its
 * bytes changed during the current verification invocation. This deliberately fails closed:
 * unchanged reports are not evidence that the current command run executed those tests.
 */
internal object JUnitXmlReportCollector {
    private const val MAX_REPORTS = 100

    fun snapshot(repository: File): Map<String, String> =
        candidateReports(repository).associate { file ->
            file.relativeTo(repository).invariantSeparatorsPath to sha256(file)
        }

    fun collectFresh(
        repository: File,
        before: Map<String, String>,
        candidateShaBefore: String,
        candidateShaAfter: String,
        candidateStateDigestBefore: String,
        candidateStateDigestAfter: String
    ): List<JUnitTestReportEvidence> {
        val candidates = candidateReports(repository)
        require(candidates.size <= MAX_REPORTS) {
            "JUnit report collection exceeds the $MAX_REPORTS-report limit."
        }
        val executionId = UUID.randomUUID().toString()
        return candidates.mapNotNull { file ->
            val relativePath = file.relativeTo(repository).invariantSeparatorsPath
            val digest = runCatching { sha256(file) }.getOrNull() ?: return@mapNotNull null
            if (before[relativePath] == digest) return@mapNotNull null
            runCatching {
                JUnitXmlReportParser.parse(
                    reportFile = file,
                    candidateShaBefore = candidateShaBefore,
                    candidateShaAfter = candidateShaAfter,
                    candidateStateDigestBefore = candidateStateDigestBefore,
                    candidateStateDigestAfter = candidateStateDigestAfter,
                    executionId = "$executionId:$relativePath"
                )
            }.getOrNull()
        }
    }

    private fun candidateReports(repository: File): List<File> {
        val root = repository.canonicalFile
        return root.walkTopDown()
            .maxDepth(12)
            .filter { file ->
                if (!file.isFile || !file.name.endsWith(".xml", ignoreCase = true)) return@filter false
                val path = runCatching { file.canonicalFile.relativeTo(root).invariantSeparatorsPath }.getOrNull()
                    ?: return@filter false
                path.startsWith("build/test-results/test/") ||
                    Regex("(^|/)build/test-results/test/").containsMatchIn(path) ||
                    Regex("(^|/)target/surefire-reports/TEST-[^/]+\\.xml$").containsMatchIn(path)
            }
            .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
            .take(MAX_REPORTS + 1)
            .toList()
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
}
