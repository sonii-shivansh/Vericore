package com.vericore.core.graph

import com.vericore.core.parser.JavaRealParser
import com.vericore.core.parser.ParsedFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files

class DependencyGraphTest :
        FunSpec({
            test("hotspot ranking uses path as a deterministic tie breaker") {
                val graph = RobustDependencyGraph()
                graph.pageRankScores["/repo/zeta.kt"] = 0.5
                graph.pageRankScores["/repo/alpha.kt"] = 0.5
                graph.pageRankScores["/repo/beta.kt"] = 0.9

                graph.getTopHotspots(3).map { it.first } shouldBe listOf(
                    "/repo/beta.kt",
                    "/repo/alpha.kt",
                    "/repo/zeta.kt"
                )
            }

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

            test("Graph should resolve same-package method parameters, returns, and generic types") {
                val root = Files.createTempDirectory("vericore-same-package-types-").toFile()
                try {
                    val packageDir = File(root, "com/example").apply { mkdirs() }
                    val fileA = File(packageDir, "A.java").apply {
                        writeText(
                            """
                            package com.example;
                            import java.util.List;
                            class A {
                                B convert(B input) { return input; }
                                List<B> values;
                            }
                            """.trimIndent()
                        )
                    }
                    val fileB = File(packageDir, "B.java").apply {
                        writeText("package com.example; class B {}")
                    }

                    val parser = JavaRealParser()
                    val parsedA = parser.parse(fileA)
                    val parsedB = parser.parse(fileB)
                    val graph = RobustDependencyGraph()
                    graph.build(listOf(parsedA, parsedB))

                    graph.graph.containsEdge(fileA.absolutePath, fileB.absolutePath) shouldBe true
                } finally {
                    root.deleteRecursively()
                }
            }

            test("Graph should resolve same-package inheritance and interfaces") {
                val root = Files.createTempDirectory("vericore-same-package-inheritance-").toFile()
                try {
                    val packageDir = File(root, "com/example").apply { mkdirs() }
                    val base = File(packageDir, "Base.java").apply {
                        writeText("package com.example; class Base {}")
                    }
                    val contract = File(packageDir, "Contract.java").apply {
                        writeText("package com.example; interface Contract {}")
                    }
                    val child = File(packageDir, "Child.java").apply {
                        writeText("package com.example; class Child extends Base implements Contract {}")
                    }

                    val parser = JavaRealParser()
                    val parsed = listOf(base, contract, child).map(parser::parse)
                    val graph = RobustDependencyGraph()
                    graph.build(parsed)

                    graph.graph.containsEdge(child.absolutePath, base.absolutePath) shouldBe true
                    graph.graph.containsEdge(child.absolutePath, contract.absolutePath) shouldBe true
                } finally {
                    root.deleteRecursively()
                }
            }

            test("Graph should not invent same-package edges for unrelated types") {
                val root = Files.createTempDirectory("vericore-no-false-edge-").toFile()
                try {
                    val packageDir = File(root, "com/example").apply { mkdirs() }
                    val fileA = File(packageDir, "A.java").apply {
                        writeText(
                            """
                            package com.example;
                            class A {
                                String name;
                            }
                            """.trimIndent()
                        )
                    }
                    val fileB = File(packageDir, "B.java").apply {
                        writeText("package com.example; class B {}")
                    }

                    val parser = JavaRealParser()
                    val parsedA = parser.parse(fileA)
                    val parsedB = parser.parse(fileB)
                    val graph = RobustDependencyGraph()
                    graph.build(listOf(parsedA, parsedB))

                    graph.graph.containsEdge(fileA.absolutePath, fileB.absolutePath) shouldBe false
                } finally {
                    root.deleteRecursively()
                }
            }
        })
