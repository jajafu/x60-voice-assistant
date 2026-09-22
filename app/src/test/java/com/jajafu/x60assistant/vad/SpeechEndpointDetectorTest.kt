package com.jajafu.x60assistant.vad

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechEndpointDetectorTest {
    private val config = SpeechEndpointConfig()

    @Test
    fun `times out when speech never starts`() {
        val detector = SpeechEndpointDetector(config)
        detector.reset(1_000)
        assertEquals(SpeechEndpointEvent.None, detector.accept(false, 5_999))
        assertEquals(SpeechEndpointEvent.NoSpeechTimeout, detector.accept(false, 6_000))
    }

    @Test
    fun `detects normal endpoint after trailing silence`() {
        val detector = SpeechEndpointDetector(config)
        detector.reset(0)
        assertEquals(SpeechEndpointEvent.SpeechStarted, detector.accept(true, 100))
        detector.accept(true, 500)
        assertEquals(SpeechEndpointEvent.None, detector.accept(false, 1_299))
        assertEquals(SpeechEndpointEvent.Endpoint, detector.accept(false, 1_300))
    }

    @Test
    fun `rejects too short segment`() {
        val detector = SpeechEndpointDetector(config)
        detector.reset(0)
        detector.accept(true, 100)
        detector.accept(true, 300)
        assertEquals(SpeechEndpointEvent.SegmentTooShort, detector.accept(false, 1_100))
    }

    @Test
    fun `enforces maximum utterance`() {
        val detector = SpeechEndpointDetector(config)
        detector.reset(0)
        detector.accept(true, 100)
        assertEquals(SpeechEndpointEvent.MaxDuration, detector.accept(true, 20_100))
    }
}
