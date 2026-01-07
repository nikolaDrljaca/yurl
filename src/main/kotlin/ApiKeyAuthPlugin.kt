package com.drbrosdev

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

/*
NOTE: Not used anymore.

Left here as an example of how to make a custom plugin and
how to protect a pure JSON web service.
 */
class ApiKeyAuthPluginConfiguration {
    private val _protectedPaths = mutableSetOf<String>()
    val protectedPaths: Set<String> = _protectedPaths

    var apiKey: String? = null
        private set

    fun withKey(value: String) {
        apiKey = value
    }

    fun protect(path: String) {
        _protectedPaths.add(path)
    }
}

val ApiKeyAuthPlugin = createApplicationPlugin(
    name = "ApiKeyAuthPlugin",
    createConfiguration = ::ApiKeyAuthPluginConfiguration
) {
    val apiKey = pluginConfig.apiKey
    application.log.info("Initializing ApiKeyAuth plugin with shortCode:$apiKey.")

    onCall { call ->
        val incoming = call.request.headers[HopHeaders.API_KEY]
        // NOTE: Path here does not contain query parameters!
        val url = call.request.path()

        if (url in pluginConfig.protectedPaths) {
            when {
                apiKey != incoming -> call.respond(HttpStatusCode.Unauthorized)
            }
        }
    }
}

fun Application.configureSecurity() {
    val apiKey = requireNotNull(environment.config.propertyOrNull("security.api_key"))
        .getString()

    install(ApiKeyAuthPlugin) {
        withKey(apiKey)
        protect("/l")
    }
}

