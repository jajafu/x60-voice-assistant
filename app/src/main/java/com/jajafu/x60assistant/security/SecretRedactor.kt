package com.jajafu.x60assistant.security

import java.net.URI

object SecretRedactor {
    private val authorization = Regex("(?i)(authorization\\s*[:=]\\s*)([^\\s,;]+)")
    private val bearer = Regex("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+")
    private val keyLike = Regex("(?i)\\b(sk-|key-|api[_-]?key[=:]?)?[A-Za-z0-9_-]{20,}\\b")

    fun redact(value: String): String = value
        .replace(authorization, "$1[REDACTED]")
        .replace(bearer, "Bearer [REDACTED]")
        .replace(keyLike, "[REDACTED]")

    fun maskUrl(value: String): String = runCatching {
        val uri = URI(value)
        URI(uri.scheme, null, uri.host, uri.port, uri.path, null, null).toString()
    }.getOrElse { "[invalid-url]" }
}
