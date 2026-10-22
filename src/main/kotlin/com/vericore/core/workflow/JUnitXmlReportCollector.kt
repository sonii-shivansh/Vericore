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
            val relativePath = relativeReportPath(repository, file) ?: return@mapNotNull null
            val digest = runCatching { sha256(file) }.getOrNull() ?: return@mapNotNull null
            relativePath to digest
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
            val relativePath = relativeReportPath(repository, file) ?: return@mapNotNull null
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
            .onEnter { directory ->
                directory.name.lowercase() !in setOf(".git", ".gradle", ".idea", "node_modules", ".vericore", ".codecontext")
            }
            .maxDepth(12)
            .filter { file ->
                if (!file.isFile || !file.name.endsWith(".xml", ignoreCase = true)) return@filter false
                val normalized = runCatching { file.canonicalFile }.getOrNull()
                    ?: return@filter false
                val relativePath = relativeReportPath(root, normalized) ?: return@filter false
                val segments = relativePath.split('/')
                val gradleReport = segments.size >= 4 &&
                    segments.takeLast(4).dropLast(1) == listOf("build", "test-results", "test")
                val mavenReport = segments.size >= 3 &&
                    segments[segments.size - 3] == "target" &&
                    segments[segments.size - 2] == "surefire-reports" &&
                    segments.last().startsWith("TEST-", ignoreCase = true)
                gradleReport || mavenReport
            }
            .map { it.canonicalFile }
            .distinctBy { it.path }
            .sortedBy { relativeReportPath(root, it) ?: it.path }
            .take(MAX_REPORTS + 1)
            .toList()
    }

    /**
     * Produces the exact same normalized repository-relative key for both the pre-run snapshot
     * and post-run collection. Never call File.relativeTo with the caller's possibly non-canonical
     * root: on Windows the walk uses the canonical root and spelling can differ from that argument.
     */
    private fun relativeReportPath(repository: File, report: File): String? = runCatching {
        val rootPath = repository.canonicalFile.toPath().normalize()
        val reportPath = report.canonicalFile.toPath().normalize()
        if (!reportPath.startsWith(rootPath)) return null
        val relative = rootPath.relativize(reportPath).toString().replace('\\', '/')
        if (relative.isBlank() || relative.startsWith("/") ||
            relative.split('/').any { it.isEmpty() || it == "." || it == ".." }
        ) return null
        relative
    }.getOrNull()

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
