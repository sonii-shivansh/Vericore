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
    fun `requires review for deleted files`() {
        val result = ChangeSafetyAnalyzer.verify(listOf(ChangedFile("src/App.kt", ChangeType.DELETED)), listOf("src/App.kt"))
        assertEquals(SafetyStatus.REVIEW_REQUIRED, result.status)
    }

    @Test
    fun `requires review when source changes exist without explicit planned paths`() {
        val result = ChangeSafetyAnalyzer.verify(changeSet("src/Unplanned.kt"), emptyList())
        assertEquals(SafetyStatus.REVIEW_REQUIRED, result.status)
        assertTrue(result.plannedPaths.isEmpty())
        assertTrue(result.unexpectedPaths.contains("src/Unplanned.kt"))
    }

    @Test
    fun `allows a clean tree without explicit planned paths`() {
        val result = ChangeSafetyAnalyzer.verify(emptyList(), emptyList())
        assertEquals(SafetyStatus.PASS, result.status)
        assertTrue(result.changedPaths.isEmpty())
    }
}
