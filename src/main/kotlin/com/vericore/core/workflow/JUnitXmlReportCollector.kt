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
        candidateReports(repository).mapNotNull { file ->
            val digest = runCatching { sha256(file) }.getOrNull() ?: return@mapNotNull null
            file.relativeTo(repository).invariantSeparatorsPath to digest
        }.toMap()

    fun collectFresh(
        repository: File,
        before: Map<String, String>,
        candidateShaBefore: String,
        candidateShaAfter: String,
        candidateStateDigestBefore: String,
        candidateStateDigestAfter: String
    ): List<JUnitTestReportEvidence> {
        val candidates = candidateReports(repository)
        if (candidates.size > MAX_REPORTS) return emptyList()
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
                    executionId = executionId,
                    reportPath = relativePath
                )
            }.getOrNull()
        }
    }

    private fun candidateReports(repository: File): List<File> {
        val root = repository.canonicalFile
        return root.walkTopDown()
            .onEnter { directory -> directory.name !in setOf(".git", ".gradle", ".idea", "node_modules", ".vericore", ".codecontext") }
            .maxDepth(12)
            .filter { file ->
                if (!file.isFile || !file.name.endsWith(".xml", ignoreCase = true)) return@filter false
                val normalized = runCatching { file.canonicalFile }.getOrNull()
                    ?: return@filter false
                if (!normalized.toPath().startsWith(root.toPath())) return@filter false
                val gradleReport = hasAncestorChain(normalized, "test", "test-results", "build")
                val mavenReport = normalized.name.startsWith("TEST-", ignoreCase = true) &&
                    hasAncestorChain(normalized, "surefire-reports", "target")
                gradleReport || mavenReport
            }
            .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
            .take(MAX_REPORTS + 1)
            .toList()
    }


    private fun hasAncestorChain(file: File, vararg expectedParentNames: String): Boolean {
        var current = file.parentFile ?: return false
        for (expectedName in expectedParentNames) {
            if (!current.name.equals(expectedName, ignoreCase = true)) return false
            current = current.parentFile ?: return expectedName == expectedParentNames.last()
        }
        return true
    }

    private fun sha256(file: File): String {
        require(file.length() in 1..JUnitXmlReportParser.MAX_REPORT_BYTES.toLong()) {
            "JUnit XML report is empty or exceeds the parser size limit."
        }
        val bytes = file.readBytes()
        require(bytes.size <= JUnitXmlReportParser.MAX_REPORT_BYTES) {
            "JUnit XML report exceeds the parser size limit."
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
