package com.vericore.server

import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

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
    fun `report urls use the api output directory rather than the analyzed repository`() {
        val processOutput = File("output").canonicalFile
        val repoOutput = File(Files.createTempDirectory("vericore-analyzed-repo-").toFile(), "output").canonicalFile
        val reportId = UUID.randomUUID().toString()
        val servedFile = File(processOutput, "$reportId.html")
        val repositoryFile = File(repoOutput, "$reportId.html")

        assertEquals(File(processOutput, "$reportId.html"), servedFile)
        kotlin.test.assertNotEquals(repositoryFile, servedFile)
    }
}