package daylightnebula.daylinmicroservices.core

import com.orbitz.consul.Consul
import com.orbitz.consul.model.agent.ImmutableRegCheck
import com.orbitz.consul.model.agent.ImmutableRegistration
import com.orbitz.consul.model.agent.Registration.RegCheck
import com.orbitz.consul.model.health.Service
import daylightnebula.daylinmicroservices.core.requests.Requester
import daylightnebula.daylinmicroservices.core.requests.request
import daylightnebula.daylinmicroservices.serializables.Result
import daylightnebula.daylinmicroservices.serializables.Schema
import daylightnebula.daylinmicroservices.serializables.validate
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.util.*
import kotlin.collections.HashMap

class Microservice(
    internal val config: MicroserviceConfig,
    private val endpoints: HashMap<String, Pair<Schema, (json: JSONObject) -> Result<JSONObject>>>,
    private val metadata: Map<String, String> = mapOf(),
    private val debugRequests: Boolean = false,
    internal val mapRequestAddress: (serv: Service, endpoint: String) -> String = { service, endpoint ->
        var targetAddress = System.getenv("requestAddr") ?: service.address
        if (debugRequests) println("Making request too $targetAddress, docker? ${config.isRunningInsideDocker()}, port: ${service.port}, endpoint $endpoint")
        if (targetAddress == "localhost" && config.isRunningInsideDocker()) targetAddress = "host.docker.internal"
        "http://${targetAddress}:${service.port}/$endpoint"
    },

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
        val doRegCheckEnv = System.getenv("doRegCheck")
        val doRegCheck = if (doRegCheckEnv != null) doRegCheckEnv == "true" else config.doRegCheck
        val check = if (doRegCheck) {
            ImmutableRegCheck.builder()
                .http(System.getenv("consulRefUrl") ?: config.consulRefUrl)
                .interval("1s")
                .deregisterCriticalServiceAfter("100ms")
                .build()
        } else { null }

        // if in docker container, auto grab own address
        val myAddress = if (config.isRunningInsideDocker()) {
            val hosts = File("/etc/hosts")
            val hostLine = hosts.readLines().last().split("\\s".toRegex())
            hostLine.first()
        } else { null }

        // setup consul
        consul = Consul.builder().withUrl(System.getenv("consulUrl") ?: config.consulUrl).build()
        val builder = ImmutableRegistration.builder()
            .id(config.id)
            .tags(config.tags)
            .name(config.name)
            .address(System.getenv("consulAddr") ?: myAddress ?: config.consulAddr)
            .meta(metadata)
            .port(config.port)
        if (check != null) builder.addChecks(check)
        consul.agentClient().register(builder.build())

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
        val pingCallback = endpoints[""] ?: (Schema() to { _ -> Result.Ok(JSONObject()) })
        endpoints[""] = pingCallback.first to {
            val result = pingCallback.second(it)
            if (result.isOk())
                Result.Ok(result.unwrap().put("status", "ok"))
            else result
        }

        // do the same with info
        val infoCallback = endpoints["info"] ?: (Schema() to { _ -> Result.Ok(JSONObject()) })
        endpoints["info"] = infoCallback.first to {
            val result = infoCallback.second(it)
            if (result.isOk())
                Result.Ok(
                    result.unwrap()
                        .put("name", config.name)
                        .put("tags", config.tags)
                        .put("endpoints", JSONArray().putAll(endpoints.keys))
                )
            else result
        }
    }

    // dynamically create routing using the given endpoints
    private val module: Application.() -> Unit = {
        config.logger.info("Creating endpoints...")
        routing {
            endpoints.forEach { (name, callback) ->
                get("/$name") {
                    // get json, cancel if null
                    val jsonString = this.call.request.queryParameters["json"]
                    val json =
                        try { JSONObject(jsonString) }
                        catch (ex: Exception) {
                            if (name == "") this.call.respondText("{}") else this.call.respondText("")
                            return@get
                        }

                    // validate json against schema
                    val validationResult = json.validate(callback.first)
                    if (validationResult.isError()) {
                        this.call.respondText { validationResult.serialize().toString(0) }
                        return@get
                    }

                    // try to get the result, if an error is thrown, return null
                    val result = try {
                        callback.second(json)
                    } catch (ex: Exception) { ex.printStackTrace(); null }
                    val resultString = result?.serialize()?.toString(0) ?: ""

                    // send back result
                    this.call.respondText(resultString)
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
    }
}