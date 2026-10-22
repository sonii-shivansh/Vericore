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
        val actualRepository = Files.createTempDirectory("vericore-junit-collector-").toFile()
        // Windows file systems are case-insensitive, but File.relativeTo can still compare the
        // caller's spelling against the canonical path returned by the directory walk.
        val repository = if (File.separatorChar == '\\') {
            File(actualRepository.absolutePath.uppercase())
        } else {
            File(actualRepository, ".")
        }
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
            assertEquals("module/build/test-results/test/TEST-Payment.xml", reports.single().reportPath)
            assertTrue(reports.single().reportSha256.matches(Regex("^[a-f0-9]{64}$")))
        } finally {
            actualRepository.deleteRecursively()
        }
    }

    @Test
    fun `fresh Maven reports are collected with normalized repository-relative paths`() {
        val repository = Files.createTempDirectory("vericore-junit-maven-collector-").toFile()
        try {
            val report = File(repository, "service/target/surefire-reports/TEST-Payment.xml")
            report.parentFile.mkdirs()
            report.writeText("""<testsuite><testcase classname="PaymentTest" name="rejectsPayment"/></testsuite>""")
            val before = JUnitXmlReportCollector.snapshot(repository)
            report.writeText("""<testsuite><testcase classname="PaymentTest" name="rejectsPayment"/><testcase classname="PaymentTest" name="acceptsPayment"/></testsuite>""")

            val reports = JUnitXmlReportCollector.collectFresh(
                repository, before, candidateSha, candidateSha, stateDigest, stateDigest
            )

            assertEquals(1, reports.size)
            assertEquals("service/target/surefire-reports/TEST-Payment.xml", reports.single().reportPath)
            assertEquals(2, reports.single().tests)
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
