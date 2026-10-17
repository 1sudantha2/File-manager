package com.nexa.filemanager.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileRulesTest {
    @Test fun rejectsUnsafeNames() {
        listOf("", " ", ".", "..", "../vault", "a/b", "a\\b", "name\u0000", "file\nname", " leading", "trailing ", "x".repeat(241)).forEach {
            assertFalse("Unsafe name accepted: $it", FileRules.validName(it))
        }
    }
    @Test fun acceptsNormalAndUnicodeNames() {
        listOf("New folder", ".hidden", "photo.jpg", "සිංහල", "日本語", "backup-2026.zip").forEach {
            assertTrue(it, FileRules.validName(it))
        }
    }
    @Test fun preventsZipSlipAndPrefixConfusion() {
        val root = Files.createTempDirectory("archive-test").toFile()
        try {
            listOf("../escape", "../../escape", "/absolute", "C:/absolute", "folder/../../escape", "..\\escape", "../${root.name}-other/file").forEach { entry ->
                try { FileRules.archiveTarget(root, entry); fail("Accepted unsafe archive entry: $entry") }
                catch (_: IllegalArgumentException) { }
            }
            assertEquals(File(root, "folder/file.txt").canonicalFile, FileRules.archiveTarget(root, "folder/file.txt"))
        } finally { root.deleteRecursively() }
    }
    @Test fun categorizationIgnoresExtensionCase() {
        assertEquals("Images", FileRules.category(File("PHOTO.JPG")))
        assertEquals("Videos", FileRules.category(File("video.mkv")))
        assertEquals("Audio", FileRules.category(File("track.FLAC")))
        assertEquals("APKs", FileRules.category(File("app.apk")))
        assertEquals("Archives", FileRules.category(File("backup.7z")))
        assertEquals("Documents", FileRules.category(File("readme.md")))
    }
    @Test fun formatsBinarySizes() {
        assertEquals("0 B", FileRules.size(0))
        assertEquals("1023 B", FileRules.size(1023))
        assertEquals("1.0 KB", FileRules.size(1024))
        assertEquals("25.0 MB", FileRules.size(25L * 1024 * 1024))
        assertEquals("1.0 GB", FileRules.size(1024L * 1024 * 1024))
    }
    @Test fun junkScannerFindsResidualFilesButNotNormalDocuments() {
        val root = Files.createTempDirectory("junk-test").toFile()
        try {
            listOf("cache.tmp", "debug.log", "backup.bak", "previous.old", ".thumbnails", "zero.txt").forEach { name ->
                val f = File(root, name).apply { writeText(if (name == "zero.txt") "" else "data") }
                assertTrue(name, FileRules.junk(f))
            }
            val normal = File(root, "important.md").apply { writeText("keep me") }
            assertFalse(FileRules.junk(normal))
        } finally { root.deleteRecursively() }
    }
}
