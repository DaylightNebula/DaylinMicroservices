package daylightnebula.daylinmicroservices.register

import daylightnebula.daylinmicroservices.core.*
import daylightnebula.daylinmicroservices.core.requests.request
import daylightnebula.daylinmicroservices.redis.redisTable
import daylightnebula.daylinmicroservices.serializables.Result
import daylightnebula.daylinmicroservices.serializables.Schema
import mu.KotlinLogging
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.concurrent.thread

val logger = KotlinLogging.logger("Service Register")

// setup config for the microservice
val config = MicroserviceConfig(
    name = "SVC-REGISTER",
    doRegister = false
)

// create microservice
val service = Microservice(
    config,
    endpoints = hashMapOf(
        // return all currently registered services
        endpoint("get", Schema()) { json ->
            // respond with a list of all active services
            Result.Ok(JSONObject().put("services", JSONArray().putAll(getAllServices().map { it.toJson() })))
        },

        // add a service
        endpoint("add", Schema()) { json ->
            // add the new service
            addService(Service(json))

            // respond with all active services
            Result.Ok(JSONObject().put("services", JSONArray().putAll(getAllServices().map { it.toJson() })))
        },

        // remove a service
        endpoint("remove", Schema()) {
            val service = Service(it)
            val entry = services.queryAll { it.service == service }.firstOrNull()
            if (entry != null) {
                removeService(entry)
                Result.Ok(JSONObject())
            } else Result.Error("No service found with id ${service.id} to remove!")
        }
    )
)

fun getAllServices(): List<Service> = service.getServices()

// create looping thread for updating services and waiting listeners
val checkAliveThread = loopingThread(1000) {
    // make sure tables initialized
    if (!tablesInitialized) return@loopingThread

    // get current time
    val currentTime = System.currentTimeMillis()

    // ping all services if they need a check
    services.getAll().forEach { entry ->
        // if it is not this services time for a check, skip
        if (currentTime - entry.lastCheckTime < entry.updateInterval) return@forEach

        // send request to the service
        service.request(entry.service, "", JSONObject()).whenComplete { result, _ ->
            // if an error was returned remove it from the services
            if (result.isError()) {
                logger.info("Service ${entry.uuid} stopped responding!")
                removeService(entry)
            }
        }
    }
}

fun addService(newService: Service) {
    // create service entry
    val entry = ServiceEntry(UUID.randomUUID(), newService, newService.updateInterval, System.currentTimeMillis())

    // add entry
    service.services.add(newService)
    services.insertOrUpdate(entry)

    logger.info("Added service $newService")

    // broadcast event
    sendEventToListeners(entry, ServiceEvent.ADDED)
}

fun removeService(old: ServiceEntry) {
    service.services.remove(old.service)
    services.remove(old.service.id)
    logger.info("Removing service ${old.service}")
    sendEventToListeners(old, ServiceEvent.REMOVED)
}

fun sendEventToListeners(svc: ServiceEntry, event: ServiceEvent) {
    service.services.forEach {
        service.request(it, "svc_event", JSONObject().put("event", event.toString()).put("service", svc.service.toJson()))
    }
}

fun main() {
    // start service
    service.start()

    // on shutdown, make sure check alive thread stops
    Runtime.getRuntime().addShutdownHook(thread (start = false) {
        checkAliveThread.join(100)
    })
}