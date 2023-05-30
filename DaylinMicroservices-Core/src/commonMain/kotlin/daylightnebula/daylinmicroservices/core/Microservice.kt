package daylightnebula.daylinmicroservices.core

import com.fasterxml.jackson.databind.jsonschema.SchemaAware
import com.orbitz.consul.Consul
import com.orbitz.consul.model.agent.ImmutableRegCheck
import com.orbitz.consul.model.agent.ImmutableRegistration
import com.orbitz.consul.model.health.Service
import daylightnebula.daylinmicroservices.core.utils.loopingThread
import daylightnebula.daylinmicroservices.serializables.DynamicObject
import daylightnebula.daylinmicroservices.serializables.Result
import daylightnebula.daylinmicroservices.serializables.Schema
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.net.InetAddress
import kotlin.collections.HashMap

class Microservice(
    internal val config: MicroserviceConfig,
    private val endpoints: HashMap<String, Pair<Schema, (json: DynamicObject) -> Result<DynamicObject>>>,

    // callbacks for when a service starts and closes
    private val onServiceOpen: (serv: Service) -> Unit = {},
    private val onServiceClose: (serv: Service) -> Unit = {}
): Thread() {

    // server stuff
    private lateinit var server: NettyApplicationEngine
    private lateinit var consul: Consul

    // service cache
    private var serviceCache = mutableMapOf<String, Service>()
    private val serviceCacheThread = loopingThread(1000) {
        if (!this::consul.isInitialized) return@loopingThread
        val curServices = consul.agentClient().services

        // check for any new services (anything in new service list that isn't in the cache)
        val newServices = curServices.filter { !serviceCache.contains(it.key) }
        newServices.forEach {
            serviceCache[it.key] = it.value
            onServiceOpen(it.value)
        }

        // check if there are any services that closed
        val oldServices = serviceCache.filter { !curServices.containsKey(it.key) }
        oldServices.forEach {
            serviceCache.remove(it.key)
            onServiceClose(it.value)
        }
    }

    // just start the server on this thread
    override fun run() {
        // create microservice server and endpoints
        setupDefaults()
        server = embeddedServer(Netty, port = config.port, module = module)

        // setup health check
        val check = ImmutableRegCheck.builder()
            .http("http://${InetAddress.getLocalHost().hostAddress}:${config.port}/")
            .interval("1s")
//            .timeout("1s")
            .deregisterCriticalServiceAfter("100ms")
//            .deregisterCriticalServiceAfter("1s")
            .build()

        // setup consul
        consul = Consul.builder().withUrl(config.consulUrl).build()
        consul.agentClient().register(
            ImmutableRegistration.builder()
                .id(config.id)
                .tags(config.tags)
                .name(config.name)
                .address("localhost")
                .port(config.port)
                .addChecks(check)
                .build()
        )

        // start server
        server.start(wait = false)
        println("Created with port ${config.port}")
    }

    // get service functions
    fun getService(uuid: String): Service? { return serviceCache[uuid] }
    fun getServiceWithName(name: String): Map.Entry<String, Service>? { return serviceCache.asSequence().firstOrNull { it.value.service == name } }
    fun getServiceWithTag(tag: String): Map.Entry<String, Service>? { return serviceCache.asSequence().firstOrNull { it.value.tags.contains(tag) } }
    fun getServices(): Map<String, Service> { return serviceCache }
    fun getServicesWithName(name: String): Map<String, Service> { return serviceCache.filter { it.value.service == name } }
    fun getServicesWithTag(tag: String): Map<String, Service> { return serviceCache.filter { it.value.tags.contains(tag) } }

    // function that just sets up default "/" endpoint and "/info" endpoints
    private fun setupDefaults() {
        // setup ping callback, adding to any preexisting version
        val pingCallback: Pair<Schema, (DynamicObject) -> Result<DynamicObject>> =
            endpoints[""] ?: (Schema() to { Result.Ok(DynamicObject().put("ok", "ok")) })
        endpoints[""] = Schema() to {
            val result = pingCallback.second(it)
            if (result.isOk())
                Result.Ok(result.unwrap().put("status", "ok"))
            else Result.Error(result.error())
        }

        // do the same with info
        val infoCallback: Pair<Schema, (DynamicObject) -> Result<DynamicObject>> =
            endpoints["info"] ?: (Schema() to { Result.Ok(DynamicObject()) })
        endpoints["info"] = Schema() to {
            val result = infoCallback.second(it)
            if (result.isOk())
                Result.Ok(
                    result.unwrap()
                        .put("name", config.name)
                        .put("tags", config.tags)
                        .put("endpoints", endpoints.keys)
                )
            else Result.Error(result.error())
        }
    }

    // dynamically create routing using the given endpoints
    private val module: Application.() -> Unit = {
        config.logger.info("Creating endpoints...")
        routing {
            endpoints.forEach { (name, pair) ->
                val (schema, callback) = pair
                get("/$name") {
                    // get json, error if null
                    val jsonString = this.call.request.queryParameters["json"]
                    var result =
                        try {
                            val json = jsonString?.let { Json.parseToJsonElement(it).jsonObject }
                            if (json != null)
                                DynamicObject.deserialize(schema, json)
                            else Result.Error("Could not get json object to deserialize dynamic object")
                        } catch (ex: Exception) {
                            Result.Error("Could not deserialize dynamic object!")
                        }

                    // try to get the result, if an error is thrown, return null
                    if (result.isOk()) {
                        result = try {
                            callback(result.unwrap())
                        } catch (ex: Exception) {
                            Result.Error("Endpoint failed with error: ${ex.message ?: " Unknown Error"}")
                        }
                    }

                    // send back result
                    this.call.respondText(Json.encodeToString(result))
                }
                config.logger.info("Created endpoint /$name")
            }
        }
        config.logger.info("Setup finished")
    }

    // function that stops everything
    fun dispose(hidden: Boolean = false) {
        serviceCacheThread.dispose()
        consul.agentClient().deregister(config.id)
        server.stop(1000, 1000)
        config.logger.info("Shutdown ${config.id}, hidden = $hidden")
        super.join()
    }
}