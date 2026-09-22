package com.jajafu.x60assistant.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceDiagnosticsFormatterTest {
    @Test
    fun `formats bytes as gibibytes with two decimal places`() {
        assertEquals("4.00 GiB", DeviceDiagnosticsFormatter.formatMemory(4L * 1024 * 1024 * 1024))
    }

    @Test
    fun `formats zero bytes`() {
        assertEquals("0.00 GiB", DeviceDiagnosticsFormatter.formatMemory(0))
    }
}

