package com.terminus.server

import com.terminus.shared.ApiMessage
import com.terminus.shared.DepartureResponse
import com.terminus.shared.DeviceRegistration
import com.terminus.shared.SubscriptionRequest
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import java.time.Instant

fun main(args: Array<String>) {
    if ("--import-gtfs" in args) {
        importGtfs()
    } else {
        embeddedServer(Netty, port = Env.port, host = "0.0.0.0", module = Application::module).start(wait = true)
    }
}

fun Application.module(repository: TransitRepository = TransitRepository()) {
    val appLog = environment.log
    install(CallLogging)
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; explicitNulls = false })
    }
    install(StatusPages) {
        exception<Throwable> { call, error ->
            appLog.error("Request failed", error)
            call.respond(HttpStatusCode.InternalServerError, ApiMessage("Request failed"))
        }
    }

    routing {
        get("/health") { call.respond(ApiMessage("ok")) }
        get("/bundle") {
            call.respond(repository.bundle(call.request.queryParameters["version"]))
        }
        get("/stations/{id}/departures") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val (station, departures) = repository.departures(id)
            if (station == null) {
                call.respond(HttpStatusCode.NotFound, ApiMessage("Unknown station"))
            } else {
                call.respond(DepartureResponse(station, Instant.now().toString(), departures))
            }
        }
        route("/devices") {
            post {
                repository.registerDevice(call.receive<DeviceRegistration>())
                call.respond(HttpStatusCode.Created, ApiMessage("registered"))
            }
        }
        route("/subscriptions") {
            post {
                repository.saveSubscription(call.receive<SubscriptionRequest>())
                call.respond(HttpStatusCode.Created, ApiMessage("saved"))
            }
        }
    }
}
