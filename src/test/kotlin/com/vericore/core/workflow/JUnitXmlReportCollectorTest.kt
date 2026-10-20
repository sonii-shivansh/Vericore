package com.vericore.core.workflow

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JUnitXmlReportCollectorTest {
    private val candidateSha = "0123456789abcdef0123456789abcdef01234567"
    private val stateDigest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    @Test
    fun `only reports whose bytes change during verification are collected`() {
        val repository = Files.createTempDirectory("vericore-junit-collector-").toFile()
        try {
            val report = File(repository, "module/build/test-results/test/TEST-Payment.xml")
            report.parentFile.mkdirs()
            report.writeText("""<testsuite><testcase classname="PaymentTest" name="rejectsPayment"/></testsuite>""")
            val before = JUnitXmlReportCollector.snapshot(repository)

            assertTrue(JUnitXmlReportCollector.collectFresh(
                repository, before, candidateSha, candidateSha, stateDigest, stateDigest
            ).isEmpty())

            report.writeText("""<testsuite><testcase classname="PaymentTest" name="rejectsPayment"/><testcase classname="PaymentTest" name="acceptsPayment"/></testsuite>""")
            val reports = JUnitXmlReportCollector.collectFresh(
                repository, before, candidateSha, candidateSha, stateDigest, stateDigest
            )

            assertEquals(1, reports.size)
            assertEquals(2, reports.single().tests)
            assertTrue(reports.single().reportSha256.matches(Regex("^[a-f0-9]{64}$")))
        } finally {
            repository.deleteRecursively()
        }
    }

    @Test
    fun `candidate drift prevents fresh report evidence from being collected`() {
        val repository = Files.createTempDirectory("vericore-junit-collector-stale-").toFile()
        try {
            val report = File(repository, "target/surefire-reports/TEST-Payment.xml")
            report.parentFile.mkdirs()
            report.writeText("""<testsuite><testcase classname="PaymentTest" name="rejectsPayment"/></testsuite>""")
            val before = JUnitXmlReportCollector.snapshot(repository)
            report.writeText("""<testsuite><testcase classname="PaymentTest" name="rejectsPayment"/><testcase classname="PaymentTest" name="acceptsPayment"/></testsuite>""")

            assertTrue(JUnitXmlReportCollector.collectFresh(
                repository, before, candidateSha, candidateSha, stateDigest, "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"
            ).isEmpty())
        } finally {
            repository.deleteRecursively()
        }
    }
}
