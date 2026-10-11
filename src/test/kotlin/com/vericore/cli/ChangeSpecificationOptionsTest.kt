package com.vericore.cli

import com.vericore.core.planner.CriterionEvidenceBinding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChangeSpecificationOptionsTest {
    @Test
    fun `repeatable CLI evidence flags group multiple IDs by criterion`() {
        val bindings = parseCriterionEvidenceBindings(
            listOf(
                "acceptance-criterion-001=file.1",
                "acceptance-criterion-001=hotspot.1",
                "required-behavior-001=repo.metrics"
            )
        )

        assertEquals(
            listOf(
                CriterionEvidenceBinding("acceptance-criterion-001", listOf("file.1", "hotspot.1")),
                CriterionEvidenceBinding("required-behavior-001", listOf("repo.metrics"))
            ),
            bindings
        )
    }

    @Test
    fun `malformed CLI evidence flags are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            parseCriterionEvidenceBindings(listOf("acceptance-criterion-001"))
        }
        assertFailsWith<IllegalArgumentException> {
            parseCriterionEvidenceBindings(listOf("=file.1"))
        }
        assertFailsWith<IllegalArgumentException> {
            parseCriterionEvidenceBindings(listOf("acceptance-criterion-001="))
        }
    }
}
