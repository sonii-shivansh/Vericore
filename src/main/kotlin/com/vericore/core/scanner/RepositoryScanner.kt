package com.vericore.core.scanner

import com.vericore.core.config.VericoreConfig
import com.vericore.core.config.ConfigLoader
import java.io.File

class RepositoryScanner(
    private val configuredConfig: VericoreConfig? = null
) {
    fun scan(rootPath: String): List<File> {
        val root = File(rootPath).canonicalFile
        if (!root.exists() || !root.isDirectory) {
            throw IllegalArgumentException("Invalid repository path: $rootPath")
        }

        // When callers do not explicitly supply configuration, resolve it from the
        // repository being analyzed rather than from Vericore's process cwd.
        val config = configuredConfig ?: ConfigLoader.loadForRepository(root.path)
        val exclusionSet = config.excludePaths
            .map { it.trim().trim('/') }
            .filter { it.isNotEmpty() }
            .toSet()

        return root.walkTopDown()
            .filter { it.isFile }
            .filter { file ->
                val name = file.name
                val relativePath = file.relativeTo(root).invariantSeparatorsPath
                val segments = relativePath.split('/').filter { it.isNotEmpty() }

                val matchesSupportedExtension =
                    name.endsWith(".kt") || name.endsWith(".java")

                // Vericore-owned generated directories are never analyzed.
                val isToolGeneratedRootPath = segments.firstOrNull() in setOf(".vericore", ".codecontext", "output")

                val excludedByConfig = segments.any { segment ->
                    segment in exclusionSet ||
                        (segment.startsWith('.') && segment.trimStart('.') in exclusionSet)
                }

                matchesSupportedExtension && !isToolGeneratedRootPath && !excludedByConfig
            }
            .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
            .toList()
    }
}
