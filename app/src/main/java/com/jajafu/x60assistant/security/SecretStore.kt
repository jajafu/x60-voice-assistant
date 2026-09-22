package com.jajafu.x60assistant.security

interface SecretStore {
    fun saveApiKey(value: CharArray)
    fun readApiKey(): CharArray?
    fun deleteApiKey()
    fun maskedApiKey(): String
}
