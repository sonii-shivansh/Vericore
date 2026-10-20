package com.vericore.core.workflow

import java.io.File
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import kotlinx.serialization.Serializable

/** Result of interpreting a JUnit XML report; only ALL_PASSED may be considered successful evidence. */
@Serializable
enum class JUnitReportStatus {
    ALL_PASSED,
    FAILED,
    NO_TESTS,
    SKIPPED_ONLY,
    INCOMPLETE
}

@Serializable
enum class JUnitCaseStatus {
    PASSED,
    FAILED,
    SKIPPED
}

@Serializable
data class JUnitTestCaseEvidence(
    val className: String?,
    val name: String,
    val status: JUnitCaseStatus
)

@Serializable
data class JUnitTestReportEvidence(
    val schemaVersion: String = "1.0",
    val parserVersion: String = JUnitXmlReportParser.PARSER_VERSION,
    val candidateSha: String,
    val candidateStateDigest: String,
    val executionId: String,
    val reportSha256: String,
    val status: JUnitReportStatus,
    val tests: Int,
    val passed: Int,
    val failed: Int,
    val skipped: Int,
    val cases: List<JUnitTestCaseEvidence>
)

/**
 * Bounded, offline parser for JUnit XML. Provenance is supplied by the caller that brackets the
 * test execution with candidate snapshots; this parser refuses a report if those snapshots differ.
 *
 * This class does not itself prove that the tests exercise a natural-language requirement. Callers
 * must bind this evidence explicitly to a criterion and must preserve the report digest and execution ID.
 */
object JUnitXmlReportParser {
    const val PARSER_VERSION = "1.0"
    const val MAX_REPORT_BYTES = 10 * 1024 * 1024
    private val shaPattern = Regex("^[0-9a-fA-F]{40}$")
    private const val MAX_TEST_CASES = 100_000

    fun parse(
        reportFile: File,
        candidateShaBefore: String,
        candidateShaAfter: String,
        candidateStateDigestBefore: String,
        candidateStateDigestAfter: String,
        executionId: String
    ): JUnitTestReportEvidence {
        require(candidateShaBefore.matches(shaPattern)) {
            "Candidate SHA before test execution must be a full 40-character Git SHA."
        }
        require(candidateShaAfter.matches(shaPattern)) {
            "Candidate SHA after test execution must be a full 40-character Git SHA."
        }
        require(candidateShaBefore.equals(candidateShaAfter, ignoreCase = true)) {
            "Git HEAD changed during test execution; test report evidence is stale."
        }
        val stateDigestPattern = Regex("^[0-9a-fA-F]{64}$")
        require(candidateStateDigestBefore.matches(stateDigestPattern)) {
            "Candidate state digest before test execution must be a full 64-character SHA-256 digest."
        }
        require(candidateStateDigestAfter.matches(stateDigestPattern)) {
            "Candidate state digest after test execution must be a full 64-character SHA-256 digest."
        }
        require(candidateStateDigestBefore.equals(candidateStateDigestAfter, ignoreCase = true)) {
            "Repository candidate inputs changed during test execution; test report evidence is stale."
        }
        require(executionId.isNotBlank() && executionId.length <= 256) {
            "Test execution ID must be non-blank and at most 256 characters."
        }
        require(reportFile.isFile) { "JUnit XML report does not exist: ${reportFile.path}" }
        val length = reportFile.length()
        require(length in 1..MAX_REPORT_BYTES.toLong()) {
            "JUnit XML report must be between 1 byte and ${MAX_REPORT_BYTES} bytes."
        }
        val bytes = reportFile.readBytes()
        require(bytes.size <= MAX_REPORT_BYTES) {
            "JUnit XML report exceeds the ${MAX_REPORT_BYTES}-byte limit."
        }

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isXIncludeAware = false
            setExpandEntityReferences(false)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
        val document = try {
            factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        } catch (error: Exception) {
            throw IllegalArgumentException("JUnit XML report is malformed or uses unsupported XML features.", error)
        }
        val root = document.documentElement
        require(root != null && (root.tagName == "testsuite" || root.tagName == "testsuites")) {
            "JUnit XML root must be <testsuite> or <testsuites>."
        }

        val nodes = root.getElementsByTagName("testcase")
        require(nodes.length <= MAX_TEST_CASES) {
            "JUnit XML report exceeds the ${MAX_TEST_CASES}-test-case limit."
        }
        val cases = (0 until nodes.length).map { index ->
            val element = nodes.item(index) as? Element
                ?: throw IllegalArgumentException("JUnit XML contains an invalid testcase node.")
            val name = element.getAttribute("name").trim()
            require(name.isNotEmpty()) { "Every JUnit testcase must have a non-blank name." }
            val childStatuses = (0 until element.childNodes.length).mapNotNull { childIndex ->
                val child = element.childNodes.item(childIndex)
                if (child.nodeType != Node.ELEMENT_NODE) null
                else when ((child as Element).tagName) {
                    "failure", "error" -> JUnitCaseStatus.FAILED
                    "skipped" -> JUnitCaseStatus.SKIPPED
                    else -> null
                }
            }
            val status = when {
                childStatuses.any { it == JUnitCaseStatus.FAILED } -> JUnitCaseStatus.FAILED
                childStatuses.any { it == JUnitCaseStatus.SKIPPED } -> JUnitCaseStatus.SKIPPED
                else -> JUnitCaseStatus.PASSED
            }
            JUnitTestCaseEvidence(
                className = element.getAttribute("classname").trim().ifEmpty { null },
                name = name,
                status = status
            )
        }

        val passed = cases.count { it.status == JUnitCaseStatus.PASSED }
        val failed = cases.count { it.status == JUnitCaseStatus.FAILED }
        val skipped = cases.count { it.status == JUnitCaseStatus.SKIPPED }
        val status = when {
            cases.isEmpty() -> JUnitReportStatus.NO_TESTS
            failed > 0 -> JUnitReportStatus.FAILED
            skipped == cases.size -> JUnitReportStatus.SKIPPED_ONLY
            skipped > 0 -> JUnitReportStatus.INCOMPLETE
            passed == cases.size -> JUnitReportStatus.ALL_PASSED
            else -> JUnitReportStatus.INCOMPLETE
        }

        return JUnitTestReportEvidence(
            candidateSha = candidateShaBefore.lowercase(),
            candidateStateDigest = candidateStateDigestBefore.lowercase(),
            executionId = executionId,
            reportSha256 = MessageDigest.getInstance("SHA-256")
                .digest(bytes).joinToString("") { "%02x".format(it) },
            status = status,
            tests = cases.size,
            passed = passed,
            failed = failed,
            skipped = skipped,
            cases = cases
        )
    }
}
