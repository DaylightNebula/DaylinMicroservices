package daylightnebula.daylinmicroservices.register

import daylightnebula.daylinmicroservices.core.Microservice
import daylightnebula.daylinmicroservices.core.MicroserviceConfig
import daylightnebula.daylinmicroservices.core.ServiceInfo
import daylightnebula.daylinmicroservices.redis.RedisConnection
import daylightnebula.daylinmicroservices.serializables.Schema
import daylightnebula.daylinmicroservices.serializables.Result
import mu.KotlinLogging
import org.json.JSONArray
import org.json.JSONObject
import java.awt.SystemColor.info
import java.lang.Thread.sleep
import java.util.*

// TODO endpoint: get
// TODO endpoint: add
// TODO endpoint: remove
// TODO what to do if a service crashes
// TODO backup to redis

val id = UUID.randomUUID()
val services = mutableListOf<ServiceInfo>()
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
            services.iterator().forEach { json.put(it.toJson()) }

            // return final object
            Result.Ok(JSONObject().put("services", json))
        }),

        // adds a service to the register
        "add" to (Schema() to {
            val info = ServiceInfo(it)

            // make sure no service with the given ID is in the register already
            if (services.any { it.id == info.id }) Result.Error("Service already registered!")
            else {
                // add the service to the register
                services.add(info)
                Result.Ok(JSONObject())
            }
        }),

        // removes a service from the register
        "remove" to (Schema() to {
            // remove the service
            val info = ServiceInfo(it)
            val removed = services.remove(info)

            // return result based on if removed
            if (removed) Result.Ok(JSONObject())
            else Result.Error("Service not found!")
        })
    )
)

// on start, start services and redis
fun main() {
    service.start()
    while(true) { sleep(100) }
}