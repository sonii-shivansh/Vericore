package com.vericore.cli

import kotlin.test.Test
import kotlin.test.assertEquals

class AIAssistantCommandTest {
    @Test
    fun rendersAnswerSuggestedFilesAndConfidenceThroughSharedOutput() {
        assertEquals(
            """💡 Answer

Response
  The service delegates to the repository layer.

Check these files
  src/main/Service.kt
  src/test/ServiceTest.kt

Confidence
  92%""",
            renderAskSummary(
                "The service delegates to the repository layer.",
                listOf("src/main/Service.kt", "src/test/ServiceTest.kt"),
                0.92
            )
        )
    }

    @Test
    fun omitsSuggestedFilesSectionWhenThereAreNoSuggestions() {
        assertEquals(
            """💡 Answer

Response
  No grounded file suggestions.

Confidence
  50%""",
            renderAskSummary("No grounded file suggestions.", emptyList(), 0.5)
        )
    }
}
