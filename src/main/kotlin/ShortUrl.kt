package com.drbrosdev

import glide.api.GlideClient
import io.ktor.http.*
import org.apache.commons.lang3.RandomStringUtils
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlinx.coroutines.future.await
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

// DATA

object ShortUrlTable : Table() {
    val id = uuid("id")
        .autoGenerate()

    val key = text("key")
        .uniqueIndex("key_index")

    val longUrl = text("long_url")

    val createdAt = text("created_at")
        .default(LocalDate.now().format(DateTimeFormatter.ISO_DATE))

    override val primaryKey = PrimaryKey(id)
}

// Domain

data class ShortUrl(
    val id: UUID,
    val key: String,
    val url: String,
    val createdAt: LocalDate
)

sealed interface ShortUrlResult {
    data class Success(val data: ShortUrl) : ShortUrlResult

    data object InvalidUrl : ShortUrlResult

    data object NoUrl : ShortUrlResult
}

// Use Cases

fun interface CreateShortUrl {
    suspend fun execute(url: String?): ShortUrlResult
}

class CreateShortUrlImpl(
    private val cacheAccessor: suspend () -> GlideClient?
) : CreateShortUrl {
    override suspend fun execute(url: String?): ShortUrlResult {
        // validate incoming url
        if (url == null) {
            return ShortUrlResult.NoUrl
        }
        val parsed = parseUrl(url) ?: return ShortUrlResult.InvalidUrl

        if (parsed.protocol !in setOf(URLProtocol.HTTP, URLProtocol.HTTPS)) {
            return ShortUrlResult.InvalidUrl
        }

        // store in database
        var output = suspendTransaction {
            // create new key and insert
            val row = retry {
                ShortUrlTable.insert {
                    it[ShortUrlTable.key] = createKey()
                    it[ShortUrlTable.longUrl] = url
                }
            }
            requireNotNull(row) { "Unable to generate unique hop key!" }
            ShortUrl(
                id = row[ShortUrlTable.id],
                key = row[ShortUrlTable.key],
                url = row[ShortUrlTable.longUrl],
                createdAt = LocalDate.parse(row[ShortUrlTable.createdAt])
            ).also {
                LOG.info("Generated ${it.key} for ${it.url}.")
            }
        }

        // store in cache
        cacheAccessor()?.set(output.key, output.url)?.await()

        // return
        return ShortUrlResult.Success(output)
    }

    private fun <T> retry(
        attempts: Int = 5,
        block: () -> T,
    ): T? {
        var result = runCatching { block() }
        var hasFailed = result.isFailure
        if (result.isSuccess) {
            return result.getOrNull()
        }

        var count = 0
        while (count < attempts && hasFailed) {
            count++
            result = runCatching { block() }
                .onFailure { println(it.localizedMessage) }
            hasFailed = result.isFailure
        }
        return result.getOrNull()
    }

    private fun createKey(): String {
        return RandomStringUtils.secure()
            .nextAlphabetic(7)
    }
}

fun interface FindShortUrlByKey {
    suspend fun execute(key: String): String?
}

class FindShortUrlByKeyImpl(
    private val cacheAccessor: suspend () -> GlideClient?
) : FindShortUrlByKey {
    override suspend fun execute(key: String): String? {
        val cache = cacheAccessor()
        return when {
            cache != null -> cache.get(key)?.await()

            else -> suspendTransaction {
                ShortUrlTable.selectAll()
                    .where { ShortUrlTable.key eq key }
                    .map { it.asHop() }
                    .singleOrNull()
                    ?.url
            }
        }
    }

    private fun ResultRow.asHop() = ShortUrl(
        id = this[ShortUrlTable.id],
        key = this[ShortUrlTable.key],
        url = this[ShortUrlTable.longUrl],
        createdAt = LocalDate.parse(this[ShortUrlTable.createdAt])
    )
}
