package com.vericore.core.workflow

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class JUnitXmlReportParserTest {
    private val candidate = "0123456789abcdef0123456789abcdef01234567"

    private fun report(xml: String): File {
        val file = Files.createTempFile("vericore-junit-", ".xml").toFile()
        file.writeText(xml)
        file.deleteOnExit()
        return file
    }

    @Test
    fun `all passing cases produce digest-bound candidate evidence`() {
        val file = report(
            """<testsuites tests="2"><testsuite name="unit" tests="2">
                <testcase classname="PaymentTest" name="acceptsValidPayment"/>
                <testcase classname="PaymentTest" name="rejectsInvalidPayment"/>
            </testsuite></testsuites>"""
        )

        val evidence = JUnitXmlReportParser.parse(file, candidate, candidate, "run-42")

        assertEquals(JUnitReportStatus.ALL_PASSED, evidence.status)
        assertEquals(2, evidence.tests)
        assertEquals(2, evidence.passed)
        assertEquals(0, evidence.failed)
        assertEquals(0, evidence.skipped)
        assertEquals(candidate, evidence.candidateSha)
        assertEquals("run-42", evidence.executionId)
        assertTrue(evidence.reportSha256.matches(Regex("^[0-9a-f]{64}$")))
        assertEquals(JUnitXmlReportParser.PARSER_VERSION, evidence.parserVersion)
    }

    @Test
    fun `failure and error cases cannot be reported as passing`() {
        val file = report(
            """<testsuite tests="3">
                <testcase name="pass"/>
                <testcase name="assertion"><failure message="expected"/></testcase>
                <testcase name="exception"><error message="boom"/></testcase>
            </testsuite>"""
        )

        val evidence = JUnitXmlReportParser.parse(file, candidate, candidate, "run-43")

        assertEquals(JUnitReportStatus.FAILED, evidence.status)
        assertEquals(1, evidence.passed)
        assertEquals(2, evidence.failed)
    }

    @Test
    fun `skipped-only and mixed skipped reports are not passing evidence`() {
        val skipped = report("""<testsuite><testcase name="skipped"><skipped/></testcase></testsuite>""")
        val mixed = report(
            """<testsuite><testcase name="pass"/><testcase name="skip"><skipped/></testcase></testsuite>"""
        )

        assertEquals(
            JUnitReportStatus.SKIPPED_ONLY,
            JUnitXmlReportParser.parse(skipped, candidate, candidate, "run-skip").status
        )
        assertEquals(
            JUnitReportStatus.INCOMPLETE,
            JUnitXmlReportParser.parse(mixed, candidate, candidate, "run-mixed").status
        )
    }

    @Test
    fun `empty report is explicit no tests rather than a pass`() {
        val file = report("""<testsuite tests="0"/>""")
        val evidence = JUnitXmlReportParser.parse(file, candidate, candidate, "run-empty")
        assertEquals(JUnitReportStatus.NO_TESTS, evidence.status)
        assertEquals(0, evidence.tests)
    }

    @Test
    fun `candidate drift during execution is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            JUnitXmlReportParser.parse(report("""<testsuite><testcase name="x"/></testsuite>"""),
                candidate, "fedcba9876543210fedcba9876543210fedcba98", "run-stale")
        }
    }

    @Test
    fun `malformed XML and external entity declarations are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            JUnitXmlReportParser.parse(report("<testsuite><testcase>"), candidate, candidate, "run-bad")
        }
        assertFailsWith<IllegalArgumentException> {
            JUnitXmlReportParser.parse(
                report("""<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><testsuite><testcase name="&xxe;"/></testsuite>"""),
                candidate, candidate, "run-xxe"
            )
        }
    }

    @Test
    fun `invalid provenance and missing report are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            JUnitXmlReportParser.parse(report("""<testsuite><testcase name="x"/></testsuite>"""), "abc", "abc", "run")
        }
        assertFailsWith<IllegalArgumentException> {
            JUnitXmlReportParser.parse(File("missing-junit-report.xml"), candidate, candidate, "run")
        }
        assertFailsWith<IllegalArgumentException> {
            JUnitXmlReportParser.parse(report("""<testsuite><testcase name="x"/></testsuite>"""), candidate, candidate, " ")
        }
    }

    @Test
    fun `report digest changes when report content changes`() {
        val first = JUnitXmlReportParser.parse(report("""<testsuite><testcase name="one"/></testsuite>"""), candidate, candidate, "run")
        val second = JUnitXmlReportParser.parse(report("""<testsuite><testcase name="two"/></testsuite>"""), candidate, candidate, "run")
        assertNotEquals(first.reportSha256, second.reportSha256)
    }

    @Test
    fun `oversized report is rejected before XML parsing`() {
        val file = Files.createTempFile("vericore-junit-large-", ".xml").toFile()
        file.writeBytes(ByteArray(JUnitXmlReportParser.MAX_REPORT_BYTES + 1) { ' '.code.toByte() })
        file.deleteOnExit()
        assertFailsWith<IllegalArgumentException> {
            JUnitXmlReportParser.parse(file, candidate, candidate, "run-large")
        }
    }

    @Test
    fun `unsupported root element is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            JUnitXmlReportParser.parse(report("""<not-junit><testcase name="x"/></not-junit>"""), candidate, candidate, "run-root")
        }
    }
}
