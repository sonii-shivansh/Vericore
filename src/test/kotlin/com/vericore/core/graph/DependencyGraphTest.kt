package com.vericore.core.graph

import com.vericore.core.parser.JavaRealParser
import com.vericore.core.parser.ParsedFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files

class DependencyGraphTest :
        FunSpec({
            test("Graph should link simple imports") {
                val fileA = File("src/com/A.kt")
                val fileB = File("src/com/B.kt")

                val parsedA = ParsedFile(fileA, "com", listOf("com.B"))
                val parsedB = ParsedFile(fileB, "com", emptyList())

                val graph = RobustDependencyGraph()
                graph.build(listOf(parsedA, parsedB))

                graph.graph.containsEdge(fileA.absolutePath, fileB.absolutePath) shouldBe true
            }

            test("Graph should handle wildcard imports") {
                val fileA = File("src/com/A.kt")
                val fileB = File("src/utils/B.kt")
                val fileC = File("src/utils/C.kt")

                val parsedA = ParsedFile(fileA, "com", listOf("utils.*"))
                val parsedB = ParsedFile(fileB, "utils", emptyList())
                val parsedC = ParsedFile(fileC, "utils", emptyList())

                val graph = RobustDependencyGraph()
                graph.build(listOf(parsedA, parsedB, parsedC))

                graph.graph.containsEdge(fileA.absolutePath, fileB.absolutePath) shouldBe true
                graph.graph.containsEdge(fileA.absolutePath, fileC.absolutePath) shouldBe true
            }

            test("Graph should link same-package Java type references and detect cycles") {
                val root = Files.createTempDirectory("vericore-same-package-").toFile()
                try {
                    val packageDir = File(root, "com/example").apply { mkdirs() }
                    val fileA = File(packageDir, "A.java").apply {
                        writeText(
                            """
                            package com.example;
                            class A {
                                B b;
                            }
                            """.trimIndent()
                        )
                    }
                    val fileB = File(packageDir, "B.java").apply {
                        writeText(
                            """
                            package com.example;
                            class B {
                                A a;
                            }
                            """.trimIndent()
                        )
                    }

                    val parser = JavaRealParser()
                    val parsedA = parser.parse(fileA)
                    val parsedB = parser.parse(fileB)

                    val graph = RobustDependencyGraph()
                    graph.build(listOf(parsedA, parsedB))

                    graph.graph.containsEdge(fileA.absolutePath, fileB.absolutePath) shouldBe true
                    graph.graph.containsEdge(fileB.absolutePath, fileA.absolutePath) shouldBe true
                    graph.hasCycles shouldBe true
                } finally {
                    root.deleteRecursively()
                }
            }
        })
