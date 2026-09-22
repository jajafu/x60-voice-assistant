package com.jajafu.x60assistant.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ServiceStateRepository {
    private val mutableRunning = MutableStateFlow(false)
    val running = mutableRunning.asStateFlow()

    fun setRunning(running: Boolean) {
        mutableRunning.value = running
    }
}

