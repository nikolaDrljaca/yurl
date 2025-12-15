package com.drbrosdev

import glide.api.GlideClient
import glide.api.models.configuration.GlideClientConfiguration
import glide.api.models.configuration.NodeAddress
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection

val LOG = KtorSimpleLogger("HopService")

// DATABASE

fun Application.configureDatabase() {
    val path = environment.config.propertyOrNull("database.url")?.getString()
    requireNotNull(path) { "Database URL not specified!" }
    val url = "$path?journal_mode=WAL&busy_timeout=5000&foreign_keys=true"

    val database = Database.connect(
        url = url,
        driver = "org.sqlite.JDBC"
    )
    // set sqlite compatible isolation level
    TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
    // configure schemas
    transaction(database) {
        SchemaUtils.create(ShortUrlTable)
    }
}


suspend fun <T> transaction(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }

// DI

data class ValkeyConfiguration(
    val host: String,
    val port: Int
)

fun ApplicationEnvironment.valkeyConfiguration(): ValkeyConfiguration {
    val hostProp = requireNotNull(config.propertyOrNull("valkey.host")) {
        "Valkey Host not set! Check environment variables."
    }
    val portProp = requireNotNull(config.propertyOrNull("valkey.port")) {
        "Valkey Port not set! Check environment variables."
    }
    return ValkeyConfiguration(
        host = hostProp.getString(),
        port = portProp.getString().toInt()
    )
}

private suspend fun createGlideClient(valkeyConfig: ValkeyConfiguration): Result<GlideClient> {
    val config = GlideClientConfiguration.builder()
        .address(
            NodeAddress.builder()
                .host(valkeyConfig.host)
                .port(valkeyConfig.port)
                .build()
        )
        .requestTimeout(500)
        .build()
    return try {
        Result.success(GlideClient.createClient(config).await())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

//===

data class ShortUrlServiceConfiguration(
    val basePath: String
)

fun ApplicationEnvironment.configuration(): ShortUrlServiceConfiguration {
    val basePathProp = requireNotNull(config.propertyOrNull("app.base_path")) {
        "Application base path must be set! Check environment variables."
    }
    return ShortUrlServiceConfiguration(
        basePath = basePathProp.getString()
    )
}

fun Application.configureFrameworks() {
    val valkeyConfig = environment.valkeyConfiguration()
    val appConfig = environment.configuration()

    dependencies {
        provide<GlideClient?> {
            createGlideClient(valkeyConfig)
                .onSuccess { LOG.info("Started Glide client at ${valkeyConfig.host}:${valkeyConfig.port}") }
                .onFailure { LOG.warn("Could not start Glide Client!") }
                .getOrNull()
        } cleanup { it?.close() }

        provide<CreateShortUrl> {
            CreateShortUrlImpl(cacheAccessor = { resolve() })
        }
        provide<FindShortUrlByKey> {
            FindShortUrlByKeyImpl(cacheAccessor = { resolve() })
        }
        provide<ShortUrlServiceConfiguration> { appConfig }
    }
}
