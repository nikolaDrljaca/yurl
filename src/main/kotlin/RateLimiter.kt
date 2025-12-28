package com.drbrosdev

import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.seconds

object Limiters {
    val FIND_HOP = RateLimitName("findHop")
    val CREATE_HOP = RateLimitName("createHop")
}

fun Application.configureRateLimiter() {
    install(RateLimit) {
        // limiter for lookups, more lenient, around 10 per sec
        register(Limiters.FIND_HOP) {
            rateLimiter(limit = 100, refillPeriod = 10.seconds)
        }

        // limiter for creations, discourage spamming
        register(Limiters.CREATE_HOP) {
            rateLimiter(limit = 10, refillPeriod = 30.seconds)
        }
    }
}
