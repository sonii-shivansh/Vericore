package com.vericore.server

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Assumptions.assumeTrue

class ReportRouteTest {
    @Test
    fun `only existing uuid-named html reports inside output are resolvable`() {
        val output = Files.createTempDirectory("vericore-reports-").toFile()
        try {
            val reportId = UUID.randomUUID().toString()
            val report = File(output, "$reportId.html").apply { writeText("<html>report</html>") }

            assertEquals(report.canonicalFile, resolveGeneratedReport(reportId, output))
            assertNull(resolveGeneratedReport(UUID.randomUUID().toString(), output))
            assertNull(resolveGeneratedReport("../secret", output))
            assertNull(resolveGeneratedReport("../../output/secret", output))
            assertNull(resolveGeneratedReport(null, output))
            assertNull(resolveGeneratedReport("not-a-uuid", output))
        } finally {
            output.deleteRecursively()
        }
    }

    @Test
    fun `does not resolve report symlinks that escape the output directory`() {
        val output = Files.createTempDirectory("vericore-report-link-").toFile()
        val outside = File(output.parentFile, "outside-${UUID.randomUUID()}.html").apply { writeText("private") }
        val reportId = UUID.randomUUID().toString()
        val link = File(output, "$reportId.html")
        try {
            val created = runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()) }.isSuccess
            assumeTrue(created, "Symbolic links are not available in this environment")
            assertNull(resolveGeneratedReport(reportId, output))
        } finally {
            output.deleteRecursively()
            outside.delete()
        }
    }

    @Test
    fun `generated report destination uses the configured output directory`() {
        val processOutput = Files.createTempDirectory("vericore-server-output-").toFile()
        val repository = Files.createTempDirectory("vericore-analyzed-repo-").toFile()
        try {
            val reportId = UUID.randomUUID().toString()
            val servedFile = generatedReportFile(reportId, processOutput)
            val repositoryFile = File(repository, "output/$reportId.html").canonicalFile

            assertEquals(File(processOutput.canonicalFile, "$reportId.html"), servedFile)
            assertNotEquals(repositoryFile, servedFile)
        } finally {
            processOutput.deleteRecursively()
            repository.deleteRecursively()
        }
    }
}