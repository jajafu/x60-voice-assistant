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
import java.util.Comparator

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
    fun `cache hit keeps reporting up to date when the installed version is the latest`() = runTest {
        val api = server.url("/releases/latest").toString()
        server.enqueue(
            MockResponse().setBody(
                """{"tag_name":"v1.0.7","body":"new","assets":[
                    {"name":"app.apk","browser_download_url":"https://example.com/a.apk","size":1}]}""",
            ),
        )

        // 第一次檢查：已安裝 1.0.7、遠端 latest 是 v1.0.7 → 不是更新，但 release 進了快取。
        assertNull(AppUpdateChecker.checkForUpdate("1.0.7", OkHttpClient(), api, forceRefresh = true))
        // 快取有效期內的後續前景檢查不得把同一個版本當成「新版本」再報一次。
        assertNull(AppUpdateChecker.checkForUpdate("1.0.7", OkHttpClient(), api))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `cached result is re-filtered when the local version advances`() = runTest {
        val api = server.url("/releases/latest").toString()
        val body =
            """{"tag_name":"v1.0.7","body":"new","assets":[
                {"name":"app.apk","browser_download_url":"https://example.com/a.apk","size":1}]}"""
        server.enqueue(MockResponse().setBody(body))
        server.enqueue(MockResponse().setBody(body))

        // 更新前：1.0.6 對 v1.0.7 是真的有新版。
        val before = AppUpdateChecker.checkForUpdate("1.0.6", OkHttpClient(), api, forceRefresh = true)
        assertEquals("v1.0.7", before?.versionTag)
        // 安裝會重啟程序、快取帶著舊的本地版本號：對新本地版本快取無效，必須重新抓取，
        // 且重新抓到同一個 tag 時要回「已是最新」而不是把相同版本當成更新。
        assertNull(AppUpdateChecker.checkForUpdate("1.0.7", OkHttpClient(), api))
        assertEquals(2, server.requestCount)
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

    @Test
    fun `clearing stale staged files removes installed-version files and keeps newer ones`() {
        val directory = Files.createTempDirectory("voice-assistant-updates").toFile()
        val installedApk = directory.resolve("x60-voice-assistant-v1.0.13.apk")
        val installedPart = directory.resolve("x60-voice-assistant-v1.0.13.apk.part")
        val newerApk = directory.resolve("x60-voice-assistant-v1.0.14.apk")
        val newerPart = directory.resolve("x60-voice-assistant-v1.0.14.apk.part")
        val unrelated = directory.resolve("unrelated.txt")
        listOf(installedApk, installedPart, newerApk, newerPart, unrelated).forEach { it.writeBytes(byteArrayOf(1)) }

        AppUpdateDownloader.removeStaleStagedFiles(directory, "1.0.13")

        // 已安裝版本的 APK 與續傳檔是殘留，清除；較新版本（含下載中的續傳檔）保留。
        assertFalse(installedApk.exists())
        assertFalse(installedPart.exists())
        assertTrue(newerApk.exists())
        assertTrue(newerPart.exists())
        assertTrue(unrelated.exists())
        Files.walk(directory.toPath()).sorted(Comparator.reverseOrder()).forEach { it.toFile().delete() }
    }

    @Test
    fun `removing other staged apks keeps the kept target and its resume file`() {
        val directory = Files.createTempDirectory("voice-assistant-updates").toFile()
        val keep = directory.resolve("x60-voice-assistant-v1.0.14.apk")
        val keepPart = directory.resolve("x60-voice-assistant-v1.0.14.apk.part")
        val oldApk = directory.resolve("x60-voice-assistant-v1.0.13.apk")
        val oldPart = directory.resolve("x60-voice-assistant-v1.0.13.apk.part")
        listOf(keep, keepPart, oldApk, oldPart).forEach { it.writeBytes(byteArrayOf(1)) }

        AppUpdateDownloader.removeOtherStagedApks(keep)

        // 當前版本的續傳檔不得被當成「其他版本」刪掉，否則中斷下載永遠無法續傳。
        assertTrue(keep.exists())
        assertTrue(keepPart.exists())
        assertFalse(oldApk.exists())
        assertFalse(oldPart.exists())
        Files.walk(directory.toPath()).sorted(Comparator.reverseOrder()).forEach { it.toFile().delete() }
    }
}
