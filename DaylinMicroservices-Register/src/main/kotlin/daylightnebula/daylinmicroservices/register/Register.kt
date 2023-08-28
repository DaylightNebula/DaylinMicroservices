package daylightnebula.daylinmicroservices.register

import daylightnebula.daylinmicroservices.core.Microservice
import daylightnebula.daylinmicroservices.core.MicroserviceConfig
import daylightnebula.daylinmicroservices.core.ServiceInfo
import daylightnebula.daylinmicroservices.redis.RedisConnection
import daylightnebula.daylinmicroservices.serializables.Schema
import daylightnebula.daylinmicroservices.serializables.Result
import mu.KotlinLogging
import org.json.JSONObject
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
            Result.Error("Not implemented!")
        }),

        // adds a service to the register
        "add" to (Schema() to {
            val info = ServiceInfo(it)
            logger.info("Received add request: $info")
            Result.Error("Not implemented!")
        }),

        // removes a service from the register
        "remove" to (Schema() to {
            val info = ServiceInfo(it)
            logger.info("Received remove request: $info")
            Result.Error("Not implemented!")
        })
    )
)

// on start, start services and redis
fun main() {
    service.start()
    while(true) { sleep(100) }
}