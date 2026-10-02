package com.jajafu.x60assistant.volume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeTargetPolicyTest {
    private val min = VolumeTargetPolicy.MIN_VOLUME_INDEX

    @Test
    fun `steps one level in the requested direction`() {
        assertEquals(6, VolumeTargetPolicy.step(currentIndex = 5, maxIndex = 15, raise = true))
        assertEquals(4, VolumeTargetPolicy.step(currentIndex = 5, maxIndex = 15, raise = false))
    }

    @Test
    fun `does not move past the ceiling`() {
        assertNull(VolumeTargetPolicy.step(currentIndex = 15, maxIndex = 15, raise = true))
    }

    @Test
    fun `stops at the floor instead of going to zero`() {
        assertNull(VolumeTargetPolicy.step(currentIndex = min, maxIndex = 15, raise = false))
        assertEquals(min, VolumeTargetPolicy.step(currentIndex = min + 1, maxIndex = 15, raise = false))
    }

    @Test
    fun `does not raise the volume when already below the floor`() {
        // 使用者自己用音量鍵壓到 0 之後說「小聲一點」，不能反過來把音量抬高。
        assertNull(VolumeTargetPolicy.step(currentIndex = 0, maxIndex = 15, raise = false))
    }

    @Test
    fun `honours a custom floor`() {
        assertNull(VolumeTargetPolicy.step(currentIndex = 4, maxIndex = 15, minIndex = 4, raise = false))
        assertEquals(4, VolumeTargetPolicy.step(currentIndex = 5, maxIndex = 15, minIndex = 4, raise = false))
    }

    @Test
    fun `converts a percentage into a level`() {
        assertEquals(8, VolumeTargetPolicy.percent(50, maxIndex = 15)?.index)
        assertEquals(15, VolumeTargetPolicy.percent(100, maxIndex = 15)?.index)
        assertEquals(3, VolumeTargetPolicy.percent(20, maxIndex = 15)?.index)
        // 0% 會被夾到下限，並標記為低於下限讓執行器只說明不執行。
        val zero = VolumeTargetPolicy.percent(0, maxIndex = 15)
        assertEquals(min, zero?.index)
        assertTrue(zero!!.belowFloor)
    }

    @Test
    fun `rounds to the nearest level`() {
        // 0.5 × 15 = 7.5，四捨五入到 8。
        assertEquals(8, VolumeTargetPolicy.percent(50, maxIndex = 15)?.index)
        // 0.3 × 15 = 4.5，四捨五入到 5。
        assertEquals(5, VolumeTargetPolicy.percent(30, maxIndex = 15)?.index)
    }

    @Test
    fun `reports a request below the floor instead of executing it`() {
        val target = VolumeTargetPolicy.percent(1, maxIndex = 15)
        assertTrue(target!!.belowFloor)
        assertEquals(min, target.index)
    }

    @Test
    fun `does not flag values inside the allowed range as below the floor`() {
        assertFalse(VolumeTargetPolicy.percent(50, maxIndex = 15)!!.belowFloor)
        assertFalse(VolumeTargetPolicy.percent(100, maxIndex = 15)!!.belowFloor)
    }

    @Test
    fun `rejects impossible inputs`() {
        assertNull(VolumeTargetPolicy.percent(-1, maxIndex = 15))
        assertNull(VolumeTargetPolicy.percent(101, maxIndex = 15))
        // 裝置上限低於下限時沒有可用的調整範圍。
        assertNull(VolumeTargetPolicy.percent(50, maxIndex = 1, minIndex = 2))
    }

    @Test
    fun `walks one level at a time toward the target`() {
        assertEquals(listOf(4, 5, 6), VolumeTargetPolicy.stepsTo(targetIndex = 6, currentIndex = 3))
        assertEquals(listOf(4, 3, 2), VolumeTargetPolicy.stepsTo(targetIndex = 2, currentIndex = 5))
    }

    @Test
    fun `produces no steps when already at the target`() {
        assertEquals(emptyList<Int>(), VolumeTargetPolicy.stepsTo(targetIndex = 5, currentIndex = 5))
    }

    @Test
    fun `walks exactly the number of levels needed`() {
        assertEquals(
            11,
            VolumeTargetPolicy.stepsTo(targetIndex = 13, currentIndex = 2).size,
        )
        assertEquals(
            13,
            VolumeTargetPolicy.stepsTo(targetIndex = 2, currentIndex = 15).size,
        )
    }

    @Test
    fun `plans directions from the current level including the first step`() {
        assertEquals(
            listOf(VolumeStep(4, VolumeStepDirection.LOWER)),
            VolumeTargetPolicy.adjustmentStepsTo(targetIndex = 4, currentIndex = 5),
        )
        assertEquals(
            listOf(
                VolumeStep(4, VolumeStepDirection.LOWER),
                VolumeStep(3, VolumeStepDirection.LOWER),
                VolumeStep(2, VolumeStepDirection.LOWER),
            ),
            VolumeTargetPolicy.adjustmentStepsTo(targetIndex = 2, currentIndex = 5),
        )
        assertEquals(
            listOf(
                VolumeStep(6, VolumeStepDirection.RAISE),
                VolumeStep(7, VolumeStepDirection.RAISE),
            ),
            VolumeTargetPolicy.adjustmentStepsTo(targetIndex = 7, currentIndex = 5),
        )
    }
}
