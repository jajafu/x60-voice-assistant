package com.jajafu.x60assistant.llm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException

class RetryPolicyTest {
    @Test
    fun `connect and server failures have bounded retries`() {
        assertTrue(RetryPolicy.decide(0, failure = ConnectException()).retry)
        assertTrue(RetryPolicy.decide(1, httpStatus = 503).retry)
        assertFalse(RetryPolicy.decide(2, httpStatus = 503).retry)
    }

    @Test
    fun `read timeout is retried only before output`() {
        assertTrue(RetryPolicy.decide(0, failure = SocketTimeoutException(), responseStarted = false).retry)
        assertFalse(RetryPolicy.decide(0, failure = SocketTimeoutException(), responseStarted = true).retry)
        assertFalse(RetryPolicy.decide(0, httpStatus = 401).retry)
    }
}
