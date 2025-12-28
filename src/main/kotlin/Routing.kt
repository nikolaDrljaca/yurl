package com.drbrosdev

import glide.api.GlideClient
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.serialization.Serializable
import java.time.format.DateTimeFormatter
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

fun Application.configureRouting() = routing {
    configureMaintenanceRoutes()

    val shortUrlDeps: ShortUrlRouteDeps by dependencies

    configureShortUrlRoutes(shortUrlDeps)
    configureViewRoutes(shortUrlDeps)
}

fun Route.configureMaintenanceRoutes() {
    get("/health") {
        val result = runCatching {
            suspendTransaction {
                exec("SELECT 1;")
            }
        }
        result.onFailure {
            if (it is CancellationException) throw it
            call.respond(HttpStatusCode.ServiceUnavailable)
        }
        result.onSuccess {
            call.respond(HttpStatusCode.OK)
        }
    }
}

// ===

@Serializable
data class CreateShortUrlPayload(
    val url: String
)

@Serializable
data class ShortUrlDto(
    val id: String,
    val key: String,
    val url: String,
    val fullUrl: String,
    val createdAt: String
) {
    companion object {
        fun from(value: ShortUrl, basePath: String) = ShortUrlDto(
            id = value.id.toString(),
            key = value.key,
            url = value.url,
            createdAt = value.createdAt.format(DateTimeFormatter.ISO_DATE),
            fullUrl = "$basePath/l/${value.key}"
        )
    }
}

fun createFullUrl(
    basePath: String,
    slug: String
): String = "$basePath/l/${slug}"

data class ShortUrlRouteDeps(
    val findHop: FindShortUrlByKey,
    val createShortUrl: CreateShortUrl,
    val config: ShortUrlServiceConfiguration,
    val log: Logger
)

fun Route.configureShortUrlRoutes(
    dependencies: ShortUrlRouteDeps
) = with(dependencies) {

    rateLimit(Limiters.CREATE_HOP) {
        post("/l") {
            // parse payload and create hop
            val payload = call.receive<CreateShortUrlPayload>()
            log.info("createHop called with $payload.")
            // prepare response
            when (val result = createShortUrl.execute(payload.url)) {
                is ShortUrlResult.InvalidUrl -> call.respond(HttpStatusCode.BadRequest, "")

                is ShortUrlResult.NoUrl -> call.respond(HttpStatusCode.BadRequest, "")

                is ShortUrlResult.Success -> {
                    val response = ShortUrlDto.from(result.data, config.basePath)
                    call.respond(HttpStatusCode.Created, response)
                }
            }
        }
    }

    post("/create-url") {
        val params = call.receiveParameters()
        log.info("create-url called with $params")
        val url = params["url"]
        // handle response
        when (val result = createShortUrl.execute(url)) {
            is ShortUrlResult.InvalidUrl -> call.respondRedirect("/400")
            is ShortUrlResult.NoUrl -> call.respondRedirect("/400")
            is ShortUrlResult.Success -> call.respondRedirect("/${result.data.key}")
        }
    }

    rateLimit(Limiters.FIND_HOP) {
        get("/l/{hop_key}") {
            val key = requireNotNull(call.pathParameters["hop_key"])
            log.info("findHop called with $key.")

            val hop = findHop.execute(key)

            when {
                hop != null -> call.respondRedirect(url = hop, permanent = false)

                else -> call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}

fun Route.configureViewRoutes(dependencies: ShortUrlRouteDeps) = with(dependencies) {

    // static assets
    staticResources("/", "public")

    get("/{slug}") {
        val key = requireNotNull(call.pathParameters["slug"])
        // make sure the key actually resolves to something
        val hop = findHop.execute(key)

        when {
            hop == null -> call.respondRedirect("/400")

            else -> call.respondHtml {
                shortUrlCreatedPage(
                    basePath = config.basePath,
                    createdUrl = createFullUrl(
                        config.basePath,
                        key
                    )
                )
            }
        }
    }

    get("/400") {
        call.respondHtml {
            yurlErrorPage(
                code = HttpStatusCode.BadRequest,
                basePath = config.basePath
            )
        }
    }

    get("/") {
        call.respondHtml {
            createUrlPage(config.basePath)
        }
    }
}
