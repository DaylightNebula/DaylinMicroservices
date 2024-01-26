package daylightnebula.daylinmicroservices.register

import daylightnebula.daylinmicroservices.core.Microservice
import daylightnebula.daylinmicroservices.core.MicroserviceConfig
import daylightnebula.daylinmicroservices.core.ServiceInfo
import daylightnebula.daylinmicroservices.core.requests.request
import daylightnebula.daylinmicroservices.redis.RedisConnection
import daylightnebula.daylinmicroservices.serializables.Schema
import daylightnebula.daylinmicroservices.serializables.Result
import mu.KotlinLogging
import org.json.JSONArray
import org.json.JSONObject
import java.awt.SystemColor.info
import java.lang.Thread.sleep
import java.util.*

// TODO pings
// TODO backup to redis

const val updateInterval = 1000 // milliseconds
const val maxStartTime = 60000  // milliseconds

// services
data class RegisteredService(val info: ServiceInfo, var lastPing: Long = System.currentTimeMillis(), var hasStarted: Boolean = false)
val services = mutableListOf<RegisteredService>()
var serviceListDirty = false

// other important stuff
val id = UUID.randomUUID()
val logger = KotlinLogging.logger("Register $id")

// create microservices with all endpoints
val config = MicroserviceConfig(id, "register", tags = listOf("register"))
val service = Microservice(
    config,
    doRegister = false,
    endpoints = hashMapOf(
        // returns all currently register services
        "get" to (Schema() to {
            // assemble json array of services
            val json = JSONArray()
            services.iterator().forEach { json.put(it.info.toJson()) }

            // return final object
            Result.Ok(JSONObject().put("services", json))
        }),

        // adds a service to the register
        "add" to (Schema() to { addService(ServiceInfo(it)) }),

        // removes a service from the register
        "remove" to (Schema() to { removeService(ServiceInfo(it)) })
    )
)

fun addService(info: ServiceInfo): Result<JSONObject> {
    logger.info("Adding service with id: ${info.id}")

    // make sure no service with the given ID is in the register already
    return if ((services).any { it.info.id == info.id }) Result.Error("Service already registered!")
    else {
        // add the service to the register
        services.add(RegisteredService(info))
        serviceListDirty = true
        Result.Ok(JSONObject())
    }
}

fun removeService(info: ServiceInfo): Result<JSONObject> {
    logger.info("Removing service with id: ${info.id}")

    // remove the service
    val removed = services.removeIf { other -> other.info == info }

    // return result based on if removed
    return if (removed) {
        serviceListDirty = true
        Result.Ok(JSONObject())
    }
    else Result.Error("Service not found!")
}

fun main() {
    // start service
    service.start()

    // loop to ping starting and started services
    while(true) {
        // grab start time of this iteration
        val startTime = System.currentTimeMillis()

        // for all services, ping to check if alive
        services.iterator().forEach { svc ->
            // send ping request with cache info
            service.request(
                svc.info,
                "register_ping",
                JSONObject().put("isCacheDirty", serviceListDirty)
            ).whenComplete { result, _ ->
                // check if fail based on if result was an error and whether the service has started
                val failed = if (result.isError()) {
                    if (svc.hasStarted) true
                    else startTime - svc.lastPing > maxStartTime
                } else false

                // if failed, remove the service
                if (failed) removeService(svc.info)
                else {
                    if (!svc.hasStarted) svc.hasStarted = true
                    svc.lastPing = startTime
                }
            }
        }

        // mark service cache up to date
        serviceListDirty = false

        // slow down based on time elapsed from start time until now
        val now = System.currentTimeMillis()
        val elapsed = (now - startTime).coerceAtLeast(0)
        if (elapsed >= updateInterval)
            logger.warn("Last update tick took too long ($elapsed MS)!  Could be too many services or register check intervals being to short.")
        else sleep(updateInterval - elapsed)
    }
}