package com.vericore.core.planner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
    fun `command-success rule is restricted to required evidence and command IDs`() {
        val commandRule = CriterionEvidenceRule.ALL_BOUND_COMMANDS_SUCCEEDED
        val normalized = ChangeSpecification(
            requiredEvidence = listOf("Unit-test command succeeds"),
            evidenceBindings = listOf(CriterionEvidenceBinding(
                "required-evidence-001", listOf(VerificationEvidenceIds.command(1)), commandRule
            ))
        ).normalized()
        assertEquals(commandRule, normalized.evidenceBindings.single().rule)

        assertFailsWith<IllegalArgumentException> {
            ChangeSpecification(
                acceptanceCriteria = listOf("Invalid input returns 400"),
                evidenceBindings = listOf(CriterionEvidenceBinding(
                    "acceptance-criterion-001", listOf(VerificationEvidenceIds.command(1)), commandRule
                ))
            ).normalized()
        }
        assertFailsWith<IllegalArgumentException> {
            ChangeSpecification(
                requiredEvidence = listOf("Unit-test command succeeds"),
                evidenceBindings = listOf(CriterionEvidenceBinding(
                    "required-evidence-001", listOf("file.1"), commandRule
                ))
            ).normalized()
        }
    }

    @Test
    fun `JUnit testcase rule accepts explicit selectors and rejects other evidence IDs`() {
        val selector = VerificationEvidenceIds.junitTestCase("com.example.PaymentTest", "rejects invalid payment")
        val rule = CriterionEvidenceRule.ALL_BOUND_JUNIT_CASES_PASSED
        val normalized = ChangeSpecification(
            acceptanceCriteria = listOf("Invalid payment is rejected"),
            evidenceBindings = listOf(CriterionEvidenceBinding("acceptance-criterion-001", listOf(selector), rule))
        ).normalized()
        assertEquals(selector, normalized.evidenceBindings.single().evidenceIds.single())
        assertEquals(rule, normalized.evidenceBindings.single().rule)

        assertFailsWith<IllegalArgumentException> {
            ChangeSpecification(
                acceptanceCriteria = listOf("Invalid payment is rejected"),
                evidenceBindings = listOf(CriterionEvidenceBinding("acceptance-criterion-001", listOf("file.1"), rule))
            ).normalized()
        }
        assertEquals(
            "junit-test-case:com.example%23Bad#rejects%23Payment",
            VerificationEvidenceIds.junitTestCase("com.example#Bad", "rejects#Payment")
        )
        assertEquals(
            "junit-test-case:com.example%2523Bad#rejects%2523Payment",
            VerificationEvidenceIds.junitTestCase("com.example%23Bad", "rejects%23Payment")
        )
        assertTrue(VerificationEvidenceIds.isJUnitTestCase(
            VerificationEvidenceIds.junitTestCase("com.example#Bad", "rejects#Payment")
        ))
    }

    @Test
    fun `non-default evidence rules affect canonical form`() {
        val manual = ChangeSpecification(
            requiredEvidence = listOf("Unit-test command succeeds"),
            evidenceBindings = listOf(CriterionEvidenceBinding("required-evidence-001", listOf(VerificationEvidenceIds.command(1))))
        )
        val evaluated = manual.copy(evidenceBindings = listOf(CriterionEvidenceBinding(
            "required-evidence-001", listOf(VerificationEvidenceIds.command(1)), CriterionEvidenceRule.ALL_BOUND_COMMANDS_SUCCEEDED
        )))
        assertNotEquals(manual.canonicalForm(), evaluated.canonicalForm())
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
