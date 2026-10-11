package com.vericore.cli

import kotlin.test.Test
import kotlin.test.assertEquals

class HumanCommandOutputTest {
    @Test
    fun `renders stable summary sections and next step`() {
        val output = HumanCommandOutput.render(
            title = "VERICORE — PROJECT INITIALIZED",
            status = "PASS",
            sections = listOf(
                HumanCommandOutput.section("Repository", "example", "/workspace/example"),
                HumanCommandOutput.section("AI", "Optional — configure with: vericore setup")
            ),
            nextStep = "vericore scan --path /workspace/example"
        )

        assertEquals(
            """VERICORE — PROJECT INITIALIZED
Status: PASS

Repository
  example
  /workspace/example

AI
  Optional — configure with: vericore setup

Next step
  vericore scan --path /workspace/example""",
            output
        )
    }

    @Test
    fun `omits empty optional status and next step`() {
        assertEquals(
            """Vericore

Repository
  example""",
            HumanCommandOutput.render(
                title = "Vericore",
                status = "",
                sections = listOf(HumanCommandOutput.section("Repository", "example")),
                nextStep = " "
            )
        )
    }
}
