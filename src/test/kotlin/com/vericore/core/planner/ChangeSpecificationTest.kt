package com.vericore.core.planner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChangeSpecificationTest {
    @Test
    fun `normalization trims entries and removes duplicates without reordering`() {
        val normalized = ChangeSpecification(
            requiredBehaviors = listOf("  preserve retries ", "preserve retries", "return a result"),
            acceptanceCriteria = listOf("  status is 200 ")
        ).normalized()

        assertEquals(listOf("preserve retries", "return a result"), normalized.requiredBehaviors)
        assertEquals(listOf("status is 200"), normalized.acceptanceCriteria)
    }

    @Test
    fun `blank requirements are rejected rather than silently discarded`() {
        assertFailsWith<IllegalArgumentException> {
            ChangeSpecification(requiredBehaviors = listOf("   ")).normalized()
        }
    }

    @Test
    fun `entry and aggregate bounds are enforced`() {
        assertFailsWith<IllegalArgumentException> {
            ChangeSpecification(forbiddenBehaviors = listOf("x".repeat(2_001))).normalized()
        }
        assertFailsWith<IllegalArgumentException> {
            ChangeSpecification(requiredBehaviors = List(101) { "requirement-$it" }).normalized()
        }
    }

    @Test
    fun `evidence bindings are normalized and must reference declared criterion IDs`() {
        val normalized = ChangeSpecification(
            acceptanceCriteria = listOf("Invalid input returns 400"),
            evidenceBindings = listOf(
                CriterionEvidenceBinding("acceptance-criterion-001", listOf(" hotspot.1 ", "file.1", "file.1"))
            )
        ).normalized()

        assertEquals(
            listOf(CriterionEvidenceBinding("acceptance-criterion-001", listOf("file.1", "hotspot.1"))),
            normalized.evidenceBindings
        )
        assertFailsWith<IllegalArgumentException> {
            ChangeSpecification(
                acceptanceCriteria = listOf("Invalid input returns 400"),
                evidenceBindings = listOf(CriterionEvidenceBinding("acceptance-criterion-002", listOf("file.1")))
            ).normalized()
        }
    }

    @Test
    fun `default manual evidence rule preserves prior canonical form`() {
        val specification = ChangeSpecification(
            acceptanceCriteria = listOf("Invalid input returns 400"),
            evidenceBindings = listOf(
                CriterionEvidenceBinding("acceptance-criterion-001", listOf("file.1"))
            )
        ).normalized()

        assertFalse(specification.canonicalForm().contains("MANUAL_REVIEW"))
    }

    @Test
    fun `command-success rule is restricted to required evidence criteria`() {
        assertFailsWith<IllegalArgumentException> {
            ChangeSpecification(
                acceptanceCriteria = listOf("Invalid input returns 400"),
                evidenceBindings = listOf(
                    CriterionEvidenceBinding(
                        "acceptance-criterion-001",
                        listOf(VerificationEvidenceIds.command(1)),
                        CriterionEvidenceRule.ALL_BOUND_COMMANDS_SUCCEEDED
                    )
                )
            ).normalized()
        }
    }

    @Test
    fun `canonical form is stable and distinguishes delimiter-like user text`() {
        val first = ChangeSpecification(requiredBehaviors = listOf("a:b", "c"))
        val same = ChangeSpecification(requiredBehaviors = listOf("a:b", "c"))
        val different = ChangeSpecification(requiredBehaviors = listOf("a", "b:c"))

        assertEquals(first.canonicalForm(), same.canonicalForm())
        assertFalse(first.canonicalForm() == different.canonicalForm())
        assertTrue(first.canonicalForm().isNotBlank())
    }
}
