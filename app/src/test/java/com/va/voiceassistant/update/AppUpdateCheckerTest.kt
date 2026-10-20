package com.va.voiceassistant.update

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

class AppUpdateCheckerTest {
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
    fun `parses latest release apk asset`() {
        val payload = """
            {
              "tag_name": "v1.0.6",
              "body": "更新說明",
              "assets": [
                {"name": "app-arm64.apk", "browser_download_url": "https://example.com/app.apk", "size": 12345},
                {"name": "notes.txt", "browser_download_url": "https://example.com/notes.txt", "size": 10}
              ]
            }
        """.trimIndent()
        val info = AppUpdateChecker.parseLatestRelease(payload)!!
        assertEquals("v1.0.6", info.versionTag)
        assertEquals("https://example.com/app.apk", info.apkUrl)
        assertEquals("app-arm64.apk", info.fileName)
        assertEquals(12345L, info.sizeBytes)
    }

    @Test
    fun `returns null when no apk asset`() {
        val payload = """{"tag_name": "v1.0.6", "body": "", "assets": []}"""
        assertNull(AppUpdateChecker.parseLatestRelease(payload))
    }

    @Test
    fun `version comparison ignores leading v`() {
        assertTrue(AppUpdateChecker.isNewerVersion("v1.0.6", "1.0.5"))
        assertFalse(AppUpdateChecker.isNewerVersion("v1.0.5", "1.0.5"))
        assertFalse(AppUpdateChecker.isNewerVersion("1.0.4", "1.0.5"))
        assertTrue(AppUpdateChecker.isNewerVersion("1.0.10", "1.0.9"))
        assertFalse(AppUpdateChecker.isNewerVersion("", "1.0.5"))
    }

    @Test
    fun `prefers release apk when multiple apks exist`() {
        val payload = """
            {
              "tag_name": "v1.0.7",
              "body": "",
              "assets": [
                {"name": "app-debug.apk", "browser_download_url": "https://example.com/d.apk", "size": 2},
                {"name": "app-release.apk", "browser_download_url": "https://example.com/r.apk", "size": 1}
              ]
            }
        """.trimIndent()
        val info = AppUpdateChecker.parseLatestRelease(payload)!!
        assertEquals("app-release.apk", info.fileName)
        assertEquals("https://example.com/r.apk", info.apkUrl)
    }

    @Test
    fun `sanitizes update file names`() {
        assertEquals("app-release.apk", AppUpdateDownloader.sanitizeFileName("app-release.apk"))
        assertEquals("a.apk", AppUpdateDownloader.sanitizeFileName("../../a.apk"))
        assertEquals("update.apk", AppUpdateDownloader.sanitizeFileName("..."))
    }

    @Test
    fun `prerelease never beats its own stable core`() {
        assertFalse(AppUpdateChecker.isNewerVersion("1.0.6-rc10", "1.0.6"))
        assertTrue(AppUpdateChecker.isNewerVersion("1.0.6", "1.0.6-rc2"))
        assertTrue(AppUpdateChecker.isNewerVersion("1.0.7-rc1", "1.0.6"))
    }

    @Test
    fun `check returns info only for newer releases`() = runTest {
        val api = server.url("/releases/latest").toString()
        server.enqueue(
            MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""),
        )
        assertNull(
            AppUpdateChecker.checkForUpdate("1.0.6", OkHttpClient(), api, forceRefresh = true),
        )
        server.enqueue(
            MockResponse().setBody(
                """{"tag_name":"v1.0.6","body":"","assets":[
                    {"name":"app.apk","browser_download_url":"https://example.com/a.apk","size":1}]}""",
            ),
        )
        assertNull(
            AppUpdateChecker.checkForUpdate("1.0.6", OkHttpClient(), api, forceRefresh = true),
        )
        server.enqueue(
            MockResponse().setBody(
                """{"tag_name":"v1.0.7","body":"new","assets":[
                    {"name":"app.apk","browser_download_url":"https://example.com/a.apk","size":1}]}""",
            ),
        )
        val info = AppUpdateChecker.checkForUpdate("1.0.6", OkHttpClient(), api, forceRefresh = true)!!
        assertEquals("v1.0.7", info.versionTag)
    }

    @Test
    fun `automatic checks reuse the recent release response`() = runTest {
        val api = server.url("/releases/latest").toString()
        server.enqueue(
            MockResponse().setBody(
                """{"tag_name":"v1.0.7","body":"new","assets":[
                    {"name":"app.apk","browser_download_url":"https://example.com/a.apk","size":1}]}""",
            ),
        )

        val first = AppUpdateChecker.checkForUpdate("1.0.6", OkHttpClient(), api, forceRefresh = true)
        val second = AppUpdateChecker.checkForUpdate("1.0.6", OkHttpClient(), api)

        assertEquals("v1.0.7", first?.versionTag)
        assertEquals("v1.0.7", second?.versionTag)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `apk download resumes an existing partial file`() = runTest {
        val bytes = "complete apk bytes".toByteArray()
        val directory = Files.createTempDirectory("voice-assistant-update").toFile()
        val target = directory.resolve("update.apk")
        val partial = AppUpdateDownloader.partialApkFile(target)
        partial.writeBytes(bytes.copyOfRange(0, 8))
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 8-${bytes.lastIndex}/${bytes.size}")
                .setBody(Buffer().write(bytes, 8, bytes.size - 8)),
        )

        val result = AppUpdateDownloader.downloadApkToFile(
            target = target,
            url = server.url("/update.apk").toString(),
            expectedSizeBytes = bytes.size.toLong(),
            client = OkHttpClient.Builder().followRedirects(false).build(),
            maxRetries = 0,
        )

        assertEquals(target, result)
        assertEquals(bytes.toList(), target.readBytes().toList())
        assertEquals("bytes=8-", server.takeRequest().getHeader("Range"))
        assertFalse(partial.exists())
        target.delete()
        directory.delete()
    }
}
