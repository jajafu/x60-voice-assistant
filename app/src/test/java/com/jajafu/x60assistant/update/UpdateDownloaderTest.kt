package com.jajafu.x60assistant.update

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

class UpdateDownloaderTest {
    @get:Rule
    val folders = TemporaryFolder()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `downloads and verifies apk`() = runTest {
        val payload = ByteArray(256 * 1024) { (it % 251).toByte() }
        server.enqueue(MockResponse().setBody(Buffer().write(payload)))
        val info = UpdateInfo(
            versionCode = 35,
            versionName = "1.0.0-rc31",
            apkUrl = server.url("/x.apk").toString(),
            sha256 = sha256Hex(payload),
        )

        val events = downloader().download(info, folders.root).toList()

        assertTrue(events.first() is UpdateDownload.Progress)
        val done = events.last()
        assertTrue(done is UpdateDownload.Done)
        val apk = (done as UpdateDownload.Done).apkFile
        assertTrue(apk.isFile)
        assertEquals(payload.size.toLong(), apk.length())
        assertFalse(folders.root.listFiles { file -> file.name.endsWith(".part") }!!.isNotEmpty())
    }

    @Test
    fun `rejects tampered download and deletes part file`() = runTest {
        val payload = ByteArray(8 * 1024) { it.toByte() }
        server.enqueue(MockResponse().setBody(Buffer().write(payload)))
        val info = UpdateInfo(
            versionCode = 35,
            versionName = "1.0.0-rc31",
            apkUrl = server.url("/x.apk").toString(),
            sha256 = "b".repeat(64),
        )

        val events = downloader().download(info, folders.root).toList()

        val failed = events.last()
        assertTrue(failed is UpdateDownload.Failed)
        assertEquals(UpdateFailure.VERIFY_FAILED, (failed as UpdateDownload.Failed).failure)
        assertTrue(folders.root.listFiles()!!.isEmpty())
    }

    private fun downloader() = UpdateDownloader(validateUrl = { it })

    private fun sha256Hex(payload: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
}
