package com.vericore.core.intelligence

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent

class GitChangeSetBuilderTest {
    @Test
    fun `revision diff captures modified and added files`() {
        val root = Files.createTempDirectory("codecontext-pr-").toFile()
        Git.init().setDirectory(root).call().use { git ->
            val a = root.resolve("A.kt")
            a.writeText("class A\n")
            git.add().addFilepattern("A.kt").call()
            git.commit().setMessage("initial").setAuthor(PersonIdent("test", "test@example.com")).setCommitter(PersonIdent("test", "test@example.com")).call()
            val base = git.repository.resolve("HEAD").name

            a.writeText("class A\nfun changed() {}\n")
            root.resolve("B.kt").writeText("class B\n")
            git.add().addFilepattern("A.kt").addFilepattern("B.kt").call()
            git.commit().setMessage("change").setAuthor(PersonIdent("test", "test@example.com")).setCommitter(PersonIdent("test", "test@example.com")).call()
            val head = git.repository.resolve("HEAD").name

            val result = GitChangeSetBuilder.fromRevisions(root.path, base, head)

            assertEquals(listOf("A.kt", "B.kt"), result.files.map { it.path })
            assertEquals(ChangeType.MODIFIED, result.files[0].changeType)
            assertEquals(ChangeType.ADDED, result.files[1].changeType)
            assertTrue(result.addedLines > 0)
        }
        root.deleteRecursively()
    }

    @Test
    fun `invalid revision is rejected`() {
        val root = Files.createTempDirectory("codecontext-pr-invalid-").toFile()
        Git.init().setDirectory(root).call().use {
            assertFailsWith<IllegalArgumentException> {
                GitChangeSetBuilder.fromRevisions(root.path, "missing-base", "missing-head")
            }
        }
        root.deleteRecursively()
    }

    @Test
    fun `empty revision diff is valid`() {
        val root = Files.createTempDirectory("codecontext-pr-empty-").toFile()
        Git.init().setDirectory(root).call().use { git ->
            val a = root.resolve("A.kt")
            a.writeText("class A\n")
            git.add().addFilepattern("A.kt").call()
            val commit = git.commit().setMessage("initial").setAllowEmpty(false)
                .setAuthor(PersonIdent("test", "test@example.com"))
                .setCommitter(PersonIdent("test", "test@example.com"))
                .call().name
            val result = GitChangeSetBuilder.fromRevisions(root.path, commit, commit)
            assertTrue(result.files.isEmpty())
        }
        root.deleteRecursively()
    }

    @Test
    fun `working tree ignores Vericore and legacy CodeContext generated output`() {
        val root = Files.createTempDirectory("codecontext-working-tree-").toFile()
        Git.init().setDirectory(root).call().use { git ->
            root.resolve("README.md").writeText("hello\n")
            git.add().addFilepattern("README.md").call()
            git.commit().setMessage("initial")
                .setAuthor(PersonIdent("test", "test@example.com"))
                .setCommitter(PersonIdent("test", "test@example.com"))
                .call()

            root.resolve("src.kt").writeText("class Source\n")
            root.resolve("output/verify.json").apply {
                parentFile.mkdirs()
                writeText("generated")
            }
            root.resolve("output/engineering-plan.json").writeText("generated")
            root.resolve(".vericore/cache/generated.json").apply {
                parentFile.mkdirs()
                writeText("generated")
            }
            root.resolve(".codecontext/cache/generated.json").apply {
                parentFile.mkdirs()
                writeText("legacy generated")
            }

            val result = GitChangeSetBuilder.fromWorkingTree(root.path)

            assertEquals(listOf("src.kt"), result.files.map { it.path })
        }
        root.deleteRecursively()
    }

    @Test
    fun `working tree returns empty change set when only generated output exists`() {
        val root = Files.createTempDirectory("codecontext-working-tree-clean-").toFile()
        Git.init().setDirectory(root).call().use { git ->
            root.resolve("README.md").writeText("hello\n")
            git.add().addFilepattern("README.md").call()
            git.commit().setMessage("initial")
                .setAuthor(PersonIdent("test", "test@example.com"))
                .setCommitter(PersonIdent("test", "test@example.com"))
                .call()

            root.resolve("output/analysis-snapshot.json").apply {
                parentFile.mkdirs()
                writeText("generated")
            }
            root.resolve(".vericore/cache/generated.json").apply {
                parentFile.mkdirs()
                writeText("generated")
            }

            val result = GitChangeSetBuilder.fromWorkingTree(root.path)

            assertTrue(result.files.isEmpty())
        }
        root.deleteRecursively()
    }
}
