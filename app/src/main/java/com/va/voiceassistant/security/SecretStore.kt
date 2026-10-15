package com.va.voiceassistant.security

interface SecretStore {
    fun saveApiKey(value: CharArray)
    fun readApiKey(): CharArray?
    fun deleteApiKey()
    fun maskedApiKey(): String
}
