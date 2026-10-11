package com.vericore.cli

/**
 * A small, deterministic renderer for product-level CLI summaries.
 *
 * Keep machine-readable output separate: callers should use this renderer only
 * for their human-readable path, without changing JSON schemas or exit behavior.
 */
internal object HumanCommandOutput {
    data class Section(val title: String, val lines: List<String>)

    fun render(
        title: String,
        status: String? = null,
        sections: List<Section> = emptyList(),
        nextStep: String? = null
    ): String = buildString {
        appendLine(title)
        status?.takeIf { it.isNotBlank() }?.let { appendLine("Status: $it") }

        sections.forEach { section ->
            appendLine()
            appendLine(section.title)
            section.lines.filter { it.isNotBlank() }.forEach { appendLine("  $it") }
        }

        nextStep?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine("Next step")
            appendLine("  $it")
        }
    }.trimEnd()

    fun section(title: String, vararg lines: String): Section =
        Section(title = title, lines = lines.toList())
}
