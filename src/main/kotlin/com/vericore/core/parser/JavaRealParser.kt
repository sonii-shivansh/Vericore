package com.vericore.core.parser

import com.github.javaparser.JavaParser
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.type.ClassOrInterfaceType
import java.io.File

class JavaRealParser : LanguageParser {
    override fun parse(file: File): ParsedFile {
        val languageLevel = JavaLanguageLevelDetector.detect(file)
        return try {
            val configuration = ParserConfiguration()
                .setLanguageLevel(languageLevel)
            val parser = JavaParser(configuration)
            val result = parser.parse(file)
            val cu: CompilationUnit = result.result.orElseThrow {
                IllegalArgumentException("JavaParser returned no compilation unit")
            }

            val packageName = cu.packageDeclaration.map { it.nameAsString }.orElse("")
            val imports = cu.imports.map { it.nameAsString }
            val referencedTypes = cu.findAll(ClassOrInterfaceType::class.java)
                .map { it.nameWithScope }
                .distinct()

            var description = ""
            cu.primaryType.ifPresent { type ->
                type.javadoc.ifPresent { javadoc -> description = javadoc.description.toText() }
            }

            val parserProblems = result.problems
            val warning = if (parserProblems.isNotEmpty()) {
                "JavaParser reported ${parserProblems.size} problem(s) at language level $languageLevel: " +
                    parserProblems.joinToString("; ") { it.message }
            } else {
                null
            }

            if (warning != null) {
                System.err.println("⚠️  ${file.name}: $warning")
            }

            ParsedFile(
                file = file,
                packageName = packageName,
                imports = imports,
                description = description,
                parseWarning = warning,
                referencedTypes = referencedTypes
            )
        } catch (e: Exception) {
            val warning = "Failed to parse Java file ${file.name} at language level $languageLevel: ${e.message}"
            System.err.println("⚠️  $warning")
            ParsedFile(file, "", emptyList(), parseWarning = warning)
        }
    }
}
