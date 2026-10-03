package com.jajafu.x60assistant.update

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateCheckerTest {
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
    fun `returns available when manifest versionCode is newer`() = runTest {
        val apkUrl = server.url("/x.apk").toString()
        server.enqueue(MockResponse().setBody(latestJson(server.url("/update.json").toString())))
        server.enqueue(MockResponse().setBody(manifestJson(versionCode = 35, apkUrl = apkUrl)))

        val result = checker().checkLatest(currentVersionCode = 34)

        assertTrue(result is UpdateCheckResult.Available)
        assertEquals("1.0.0-rc31", (result as UpdateCheckResult.Available).info.versionName)
    }

    @Test
    fun `returns up to date when manifest versionCode is not newer`() = runTest {
        val apkUrl = server.url("/x.apk").toString()
        server.enqueue(MockResponse().setBody(latestJson(server.url("/update.json").toString())))
        server.enqueue(MockResponse().setBody(manifestJson(versionCode = 34, apkUrl = apkUrl)))

        assertEquals(UpdateCheckResult.UpToDate, checker().checkLatest(currentVersionCode = 34))
    }

    @Test
    fun `fails when release has no update manifest`() = runTest {
        server.enqueue(MockResponse().setBody(latestJson("other.json", server.url("/other.json").toString())))

        val result = checker().checkLatest(currentVersionCode = 34)

        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals(UpdateFailure.INVALID_RESPONSE, (result as UpdateCheckResult.Failed).failure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `fails when manifest sha is malformed`() = runTest {
        server.enqueue(MockResponse().setBody(latestJson(server.url("/update.json").toString())))
        server.enqueue(
            MockResponse().setBody(
                manifestJson(versionCode = 35, apkUrl = server.url("/x.apk").toString(), sha = "xyz"),
            ),
        )

        val result = checker().checkLatest(currentVersionCode = 34)

        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals(UpdateFailure.INVALID_RESPONSE, (result as UpdateCheckResult.Failed).failure)
    }

    @Test
    fun `maps server error to network failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))

        val result = checker().checkLatest(currentVersionCode = 34)

        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals(UpdateFailure.NETWORK, (result as UpdateCheckResult.Failed).failure)
    }

    private fun checker() = UpdateChecker(
        latestUrl = server.url("/releases/latest").toString(),
        validateUrl = { it },
    )

    private fun latestJson(manifestUrl: String) =
        latestJson("update.json", manifestUrl)

    private fun latestJson(assetName: String, manifestUrl: String) =
        """{"tag_name":"v1.0.0-rc31","assets":[{"name":"$assetName","browser_download_url":"$manifestUrl"}]}"""

    private fun manifestJson(versionCode: Int, apkUrl: String, sha: String = "a".repeat(64)) =
        """{"versionCode":$versionCode,"versionName":"1.0.0-rc31","apkUrl":"$apkUrl","sha256":"$sha","notes":""}"""
}
