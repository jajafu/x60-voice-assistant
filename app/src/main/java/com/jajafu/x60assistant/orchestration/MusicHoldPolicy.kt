package com.jajafu.x60assistant.orchestration

import com.jajafu.x60assistant.music.MusicCommand

internal object MusicHoldPolicy {
    fun shouldPauseAfterAction(
        command: MusicCommand,
        musicAlreadyHeld: Boolean,
    ): Boolean = when (command) {
        MusicCommand.Pause, MusicCommand.Open -> false
        MusicCommand.Play, MusicCommand.PlayFavorites, MusicCommand.Next, MusicCommand.Previous,
        is MusicCommand.SearchAndPlay, is MusicCommand.PlayArtist -> true
        else -> musicAlreadyHeld
    }
}
