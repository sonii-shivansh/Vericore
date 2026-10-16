package com.vericore.core.workflow

import java.io.IOException
import java.nio.file.Files
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CandidateStateSnapshotterTest {
    @Test
    fun `digest is stable for an unchanged repository tree`() {
        withRepository { root ->
            Files.createDirectories(root.resolve("src/main/kotlin"))
            Files.writeString(root.resolve("src/main/kotlin/App.kt"), "fun main() = Unit\n")
            Files.writeString(root.resolve("build.gradle.kts"), "plugins { kotlin(\"jvm\") }\n")

            val first = CandidateStateSnapshotter.capture(root.toFile())
            val second = CandidateStateSnapshotter.capture(root.toFile())

            assertEquals(first.digest, second.digest)
            assertEquals(first.fileCount, second.fileCount)
            assertEquals(2, first.fileCount)
            assertTrue(first.digest.matches(Regex("^[a-f0-9]{64}$")))
        }
    }

    @Test
    fun `digest changes when source changes without a Git commit`() {
        withRepository { root ->
            val source = root.resolve("src/main/kotlin/App.kt")
            Files.createDirectories(source.parent)
            Files.writeString(source, "fun result() = 1\n")
            val before = CandidateStateSnapshotter.capture(root.toFile())

            // No Git operation occurs here: the digest must reflect working-tree content, not HEAD.
            Files.writeString(source, "fun result() = 2\n")
            val after = CandidateStateSnapshotter.capture(root.toFile())

            assertNotEquals(before.digest, after.digest)
        }
    }

    @Test
    fun `digest includes build and dependency inputs not just source files`() {
        withRepository { root ->
            val source = root.resolve("src/main/kotlin/App.kt")
            val build = root.resolve("build.gradle.kts")
            Files.createDirectories(source.parent)
            Files.writeString(source, "fun main() = Unit\n")
            Files.writeString(build, "plugins { kotlin(\"jvm\") }\n")
            val before = CandidateStateSnapshotter.capture(root.toFile())

            Files.writeString(build, "plugins { kotlin(\"jvm\") version \"2.1.0\" }\n")
            val after = CandidateStateSnapshotter.capture(root.toFile())

            assertNotEquals(before.digest, after.digest)
        }
    }

    @Test
    fun `digest changes when a tracked or untracked input is added or removed`() {
        withRepository { root ->
            val source = root.resolve("src/App.kt")
            Files.createDirectories(source.parent)
            Files.writeString(source, "class App\n")
            val initial = CandidateStateSnapshotter.capture(root.toFile())

            val config = root.resolve(".env.example")
            Files.writeString(config, "MODE=test\n")
            val withAddedFile = CandidateStateSnapshotter.capture(root.toFile())
            Files.delete(config)
            val afterRemoval = CandidateStateSnapshotter.capture(root.toFile())

            assertNotEquals(initial.digest, withAddedFile.digest)
            assertEquals(initial.digest, afterRemoval.digest)
        }
    }

    @Test
    fun `untracked root logs remain candidate inputs`() {
        withRepository { root ->
            val source = root.resolve("src/App.kt")
            Files.createDirectories(source.parent)
            Files.writeString(source, "class App\n")
            val before = CandidateStateSnapshotter.capture(root.toFile())

            // Do not special-case file extensions: a build may read an otherwise untracked file.
            Files.writeString(root.resolve("verification-executed.log"), "mvn test\n")
            val after = CandidateStateSnapshotter.capture(root.toFile())

            assertNotEquals(before.digest, after.digest)
        }
    }

    @Test
    fun `digest changes when an executable bit changes on supported filesystems`() {
        withRepository { root ->
            val script = root.resolve("run.sh")
            Files.writeString(script, "#!/bin/sh\nexit 0\n")
            val beforeExecutable = Files.isExecutable(script)
            val before = CandidateStateSnapshotter.capture(root.toFile())

            val permissionChanged = script.toFile().setExecutable(true, true)
            val afterExecutable = Files.isExecutable(script)
            if (permissionChanged && beforeExecutable != afterExecutable) {
                val after = CandidateStateSnapshotter.capture(root.toFile())
                assertNotEquals(before.digest, after.digest)
            }
        }
    }

    @Test
    fun `source tree directories named build output or target remain candidate inputs`() {
        withRepository { root ->
            val sourceRoot = root.resolve("src/main/kotlin")
            Files.createDirectories(sourceRoot)
            Files.writeString(sourceRoot.resolve("App.kt"), "class App\n")
            val before = CandidateStateSnapshotter.capture(root.toFile())

            for (directory in listOf("build", "output", "target")) {
                val path = sourceRoot.resolve("$directory/Source.kt")
                Files.createDirectories(path.parent)
                Files.writeString(path, "package $directory\nclass Source\n")
            }

            val after = CandidateStateSnapshotter.capture(root.toFile())
            assertNotEquals(before.digest, after.digest)
            assertEquals(4, after.fileCount)
        }
    }

    @Test
    fun `generated outputs and build caches do not perturb candidate digest`() {
        withRepository { root ->
            val source = root.resolve("src/App.kt")
            Files.createDirectories(source.parent)
            Files.writeString(source, "class App\n")
            val before = CandidateStateSnapshotter.capture(root.toFile())

            Files.createDirectories(root.resolve("output"))
            Files.writeString(root.resolve("output/verification-receipt.json"), "{\"status\":\"PASS\"}")
            Files.createDirectories(root.resolve("build/classes"))
            Files.writeString(root.resolve("build/classes/generated.class"), "generated")
            Files.createDirectories(root.resolve("target/classes"))
            Files.writeString(root.resolve("target/classes/generated.class"), "generated")
            Files.createDirectories(root.resolve(".vericore"))
            Files.writeString(root.resolve(".vericore/session.json"), "{}")

            val after = CandidateStateSnapshotter.capture(root.toFile())

            assertEquals(before.digest, after.digest)
            assertEquals(1, after.fileCount)
        }
    }

    @Test
    fun `digest changes when an external symlink target changes`() {
        val externalFile = Files.createTempFile("vericore-external-target", ".txt")
        try {
            Files.writeString(externalFile, "version one")
            withRepository { root ->
                try {
                    Files.createSymbolicLink(root.resolve("linked-config"), externalFile)
                } catch (error: Exception) {
                    assumeTrue(false, "Symbolic links are unavailable on this filesystem: ${error.message}")
                    return@withRepository
                }

                val before = CandidateStateSnapshotter.capture(root.toFile())
                Files.writeString(externalFile, "version two")
                val after = CandidateStateSnapshotter.capture(root.toFile())

                assertNotEquals(before.digest, after.digest)
            }
        } finally {
            Files.deleteIfExists(externalFile)
        }
    }

    @Test
    fun `directory symlinks fail closed instead of omitting linked inputs`() {
        val externalDirectory = Files.createTempDirectory("vericore-external-directory")
        try {
            Files.writeString(externalDirectory.resolve("config.json"), "{}")
            withRepository { root ->
                try {
                    Files.createSymbolicLink(root.resolve("linked-directory"), externalDirectory)
                } catch (error: Exception) {
                    assumeTrue(false, "Symbolic links are unavailable on this filesystem: ${error.message}")
                    return@withRepository
                }

                kotlin.test.assertFailsWith<IOException> {
                    CandidateStateSnapshotter.capture(root.toFile())
                }
            }
        } finally {
            externalDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `baseline manifest is deterministic and validates every entry`() {
        withRepository { root ->
            Files.createDirectories(root.resolve("src"))
            Files.writeString(root.resolve("src/App.kt"), "class App\n")
            Files.writeString(root.resolve("gradle.properties"), "org.gradle.caching=true\n")

            val first = CandidateStateSnapshotter.captureManifest(root.toFile())
            val second = CandidateStateSnapshotter.captureManifest(root.toFile())

            assertTrue(first.isValid())
            assertEquals(first, second)
            assertEquals(first.digest, first.snapshot().digest)
            assertEquals(listOf("gradle.properties", "src/App.kt"), first.entries.map { it.path })
            val tampered = first.copy(entries = first.entries.mapIndexed { index, entry ->
                if (index == 0) entry.copy(contentDigest = "0".repeat(64)) else entry
            })
            assertTrue(!tampered.isValid(), "an entry changed without updating the manifest digest must be rejected")
        }
    }

    @Test
    fun `baseline detects ignored and untracked input drift while allowing planned changes`() {
        withRepository { root ->
            Files.createDirectories(root.resolve("src"))
            Files.writeString(root.resolve("src/App.kt"), "class App\n")
            Files.writeString(root.resolve(".gitignore"), "*.secret\n")
            val gitInit = ProcessBuilder("git", "init", "-q").directory(root.toFile()).redirectErrorStream(true).start()
            gitInit.inputStream.bufferedReader().readText()
            assertEquals(0, gitInit.waitFor(), "test fixture must be a Git repository")
            val baseline = CandidateStateSnapshotter.captureManifest(root.toFile())

            // The ignored file is confirmed by Git but must still be represented as a candidate input.
            Files.writeString(root.resolve("credentials.secret"), "placeholder-not-a-real-secret")
            val checkIgnored = ProcessBuilder("git", "check-ignore", "credentials.secret")
                .directory(root.toFile()).redirectErrorStream(true).start()
            checkIgnored.inputStream.bufferedReader().readText()
            assertEquals(0, checkIgnored.waitFor(), "fixture file should actually be ignored by Git")
            Files.writeString(root.resolve("build-config.properties"), "mode=production\n")
            Files.writeString(root.resolve("src/App.kt"), "class App { fun changed() = Unit }\n")
            Files.createDirectories(root.resolve("output"))
            Files.writeString(root.resolve("output/verification-receipt.json"), "{}")

            val current = CandidateStateSnapshotter.captureManifest(root.toFile())
            val drift = CandidateStateSnapshotter.unplannedDrift(
                baseline,
                current,
                listOf("src/App.kt")
            )

            assertEquals(listOf("build-config.properties", "credentials.secret"), drift)
        }
    }

    @Test
    fun `baseline detects unplanned deletions and permits only explicitly planned paths`() {
        withRepository { root ->
            Files.createDirectories(root.resolve("src/main"))
            Files.writeString(root.resolve("src/main/App.kt"), "class App\n")
            Files.createDirectories(root.resolve("src/test"))
            Files.writeString(root.resolve("src/test/AppTest.kt"), "class AppTest\n")
            val baseline = CandidateStateSnapshotter.captureManifest(root.toFile())

            Files.delete(root.resolve("src/main/App.kt"))
            Files.writeString(root.resolve("src/test/AppTest.kt"), "class AppTest { fun newer() = Unit }\n")
            val current = CandidateStateSnapshotter.captureManifest(root.toFile())

            assertEquals(listOf("src/main/App.kt"), CandidateStateSnapshotter.unplannedDrift(
                baseline,
                current,
                listOf("src/test/AppTest.kt")
            ))
        }
    }

    private fun withRepository(block: (java.nio.file.Path) -> Unit) {
        val root = Files.createTempDirectory("vericore-candidate-state")
        try {
            block(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
