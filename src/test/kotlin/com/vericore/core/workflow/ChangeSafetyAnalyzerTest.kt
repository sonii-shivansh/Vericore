package com.vericore.core.workflow

import com.vericore.core.intelligence.ChangeType
import com.vericore.core.intelligence.ChangedFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChangeSafetyAnalyzerTest {
    private fun changeSet(vararg paths: String) = paths.map { ChangedFile(it, ChangeType.MODIFIED) }

    @Test
    fun `passes when working tree stays inside plan`() {
        val result = ChangeSafetyAnalyzer.verify(changeSet("src/App.kt"), listOf("src/App.kt"))
        assertEquals(SafetyStatus.PASS, result.status)
        assertTrue(result.unexpectedPaths.isEmpty())
    }

    @Test
    fun `fails when change escapes planned scope`() {
        val result = ChangeSafetyAnalyzer.verify(changeSet("src/App.kt", "src/Other.kt"), listOf("src/App.kt"))
        assertEquals(SafetyStatus.FAIL, result.status)
        assertTrue("src/Other.kt" in result.unexpectedPaths)
    }

    @Test
    fun `fails closed when source changes exist without planned paths`() {
        val result = ChangeSafetyAnalyzer.verify(changeSet("README.md"), emptyList())
        assertEquals(SafetyStatus.FAIL, result.status)
        assertTrue("README.md" in result.unexpectedPaths)
        assertTrue(result.reasons.any { it.contains("No explicit source paths") })
    }

    @Test
    fun `requires review for deleted files`() {
        val result = ChangeSafetyAnalyzer.verify(listOf(ChangedFile("src/App.kt", ChangeType.DELETED)), listOf("src/App.kt"))
        assertEquals(SafetyStatus.REVIEW_REQUIRED, result.status)
    }
}
