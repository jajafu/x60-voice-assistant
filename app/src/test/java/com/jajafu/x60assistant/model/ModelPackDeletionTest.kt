package com.jajafu.x60assistant.model

import com.jajafu.x60assistant.asr.AsrModelPacks
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelPackDeletionTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `deleting one pack removes its model and download leftovers only`() {
        val filesDir = temporaryFolder.root
        val target = AsrModelPacks.moonshine
        val other = AsrModelPacks.standard
        val targetDirectory = File(filesDir, "model-packs/asr/${target.id}")
        val stagingDirectory = File(filesDir, "model-packs/asr/${target.id}.staging")
        val backupDirectory = File(filesDir, "model-packs/asr/${target.id}.backup")
        val partialArchive = File(filesDir, "model-packs/downloads/${target.id}.part")
        val otherDirectory = File(filesDir, "model-packs/asr/${other.id}")
        val otherPartial = File(filesDir, "model-packs/downloads/${other.id}.part")
        listOf(targetDirectory, stagingDirectory, backupDirectory, otherDirectory).forEach {
            assertTrue(it.mkdirs())
            File(it, "model.bin").writeText("data")
        }
        partialArchive.apply { parentFile!!.mkdirs(); writeText("partial") }
        otherPartial.writeText("other partial")

        deleteAsrPackFiles(filesDir, target)

        listOf(targetDirectory, stagingDirectory, backupDirectory, partialArchive).forEach {
            assertFalse(it.exists())
        }
        assertTrue(File(otherDirectory, "model.bin").isFile)
        assertTrue(otherPartial.isFile)
    }
}
