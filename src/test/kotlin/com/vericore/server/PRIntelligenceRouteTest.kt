package com.vericore.server

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PRIntelligenceRouteTest {
    @Test
    fun `REST report output is written under the directory exposed by reports route`() {
        val report = reportOutputFile("report-id")
        kotlin.test.assertEquals(File("output", "report-id.html").path, report.path)
    }

    @Test
    fun `working tree mode allows both revisions to be absent`() {
        validateRevisionPair(null, null)
    }

    @Test
    fun `revision mode requires both revisions`() {
        assertFailsWith<IllegalArgumentException> { validateRevisionPair("HEAD~1", null) }
        assertFailsWith<IllegalArgumentException> { validateRevisionPair(null, "HEAD") }
    }

    @Test
    fun `blank or oversized revisions are rejected`() {
        assertFailsWith<IllegalArgumentException> { validateRevisionPair("", "HEAD") }
        assertFailsWith<IllegalArgumentException> { validateRevisionPair("A".repeat(257), "HEAD") }
    }
}
