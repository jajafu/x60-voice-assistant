package com.jajafu.x60assistant.llm

import java.net.URI

object ProviderUrl {
    fun normalize(
        baseUrl: String,
        endpoint: String,
        allowHttp: Boolean = false,
        allowedHttpHosts: Set<String> = setOf("localhost", "127.0.0.1", "::1", "10.0.2.2"),
    ): String {
        val base = runCatching { URI(baseUrl.trim()) }
            .getOrElse { throw LlmException(LlmErrorCode.CONFIG_INVALID, message = "Base URL 格式無效") }
        val scheme = base.scheme?.lowercase()
        if (scheme !in setOf("https", "http") || base.host.isNullOrBlank() || base.userInfo != null) {
            throw LlmException(LlmErrorCode.CONFIG_INVALID, message = "Base URL 必須是有效的 HTTPS URL")
        }
        if (scheme == "http" && (!allowHttp || base.host.lowercase() !in allowedHttpHosts)) {
            throw LlmException(LlmErrorCode.CONFIG_INVALID, message = "此版本不允許該 HTTP Host")
        }
        if (base.query != null || base.fragment != null) {
            throw LlmException(LlmErrorCode.CONFIG_INVALID, message = "Base URL 不可包含 query 或 fragment")
        }

        val baseSegments = base.path.orEmpty().split('/').filter(String::isNotBlank).toMutableList()
        var endpointSegments = endpoint.trim().split('/').filter(String::isNotBlank)
        if (endpointSegments.isEmpty()) endpointSegments = listOf("chat", "completions")
        if (baseSegments.endsWith(endpointSegments)) endpointSegments = emptyList()
        if (baseSegments.lastOrNull() == "v1" && endpointSegments.firstOrNull() == "v1") {
            endpointSegments = endpointSegments.drop(1)
        }
        val path = (baseSegments + endpointSegments).joinToString("/", prefix = "/")
        return URI(scheme, null, base.host, base.port, path, null, null).toString()
    }

    private fun <T> List<T>.endsWith(suffix: List<T>): Boolean =
        suffix.size <= size && takeLast(suffix.size) == suffix
}
