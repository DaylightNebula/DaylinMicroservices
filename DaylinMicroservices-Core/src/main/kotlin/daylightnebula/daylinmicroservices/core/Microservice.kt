package daylightnebula.daylinmicroservices.core

import daylightnebula.daylinmicroservices.core.requests.Requester
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
import java.util.concurrent.TimeUnit
import kotlin.collections.HashMap
import kotlin.time.Duration

class Microservice(
    internal val config: MicroserviceConfig,
    private val endpoints: HashMap<String, Pair<Schema, (json: JSONObject) -> Result<JSONObject>>>,
    private val metadata: Map<String, String> = mapOf(),
    internal val mapRequestAddress: (serv: Service, endpoint: String) -> String = { service, endpoint ->
        var defaultAddress = service.address
        if (defaultAddress.contains("localhost") && isRunningInsideDocker())
            defaultAddress = defaultAddress.replace("localhost", "host.docker.internal")
        "${System.getenv("requestAddr") ?: defaultAddress}/$endpoint"
    },

    // callbacks for when a service starts and closes
    private val onServiceOpen: (serv: Service) -> Unit = {},
    private val onServiceClose: (serv: Service) -> Unit = {}
): Thread() {
    // service entry for self
    val address = if (isRunningInsideDocker()) {
        val hosts = File("/etc/hosts")
        val hostLine = hosts.readLines().last().split("\\s".toRegex())
        "http://${hostLine.first()}:${config.port}"
    } else { "http://localhost:${config.port}" }
    val service = Service(
        config.id,
        config.name,
        config.tags,
        address,
        metadata,
        Duration.parse(config.registerUpdateInterval).inWholeMilliseconds
    )

    // server stuff
    private lateinit var server: NettyApplicationEngine
    val services = mutableListOf<Service>()

    // just start the server on this thread
    override fun run() {
        // create microservice server and endpoints
        setupDefaults()
        server = embeddedServer(Netty, port = config.port, module = module)

        // start server
        server.start(wait = false)
        println("Created with port ${config.port}")

        // if this needs to be added to the service registry, do so
        if (config.doRegister) {
            Requester.rawRequest(config.logger, "${config.registerUrl}/add", service.toJson())
                .whenComplete { result, _ ->
                    when (result) {
                        is Result.Error -> config.logger.error("Service add failed with error: ${result.error()}")
                        is Result.Ok -> {
                            services.clear()
                            services.addAll(result.unwrap().getJSONArray("services").map { Service(it as JSONObject) })
                        }
                    }
                }
        }
    }

    // get service functions
    fun getService(uuid: UUID) = services.firstOrNull { it.id == uuid }
    fun getServiceWithName(name: String) = services.firstOrNull { it.name == name }
    fun getServiceWithTag(tag: String) = services.firstOrNull { it.tags.contains(tag) }
//    fun getServices(): List<Service> = services
    fun getServicesWithName(name: String) = services.filter { it.name == name }
    fun getServicesWithTag(tag: String) = services.filter { it.tags.contains(tag) }

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

        // handle service events
        val svcEventsCallback = endpoints["svc_event"] ?: (Schema() to { _ -> Result.Ok(JSONObject()) })
        endpoints["svc_event"] = svcEventsCallback.first to { json ->
            // unpack input
            val event = ServiceEvent.valueOf(json.getString("event"))
            val service = Service(json.getJSONObject("service"))

            // handle event
            when (event) {
                // if added, add to list and add callback
                ServiceEvent.ADDED -> {
                    services.add(service)
                    onServiceOpen(service)
                }

                // if removed, remove from list and call callback
                ServiceEvent.REMOVED -> {
                    val removed = services.remove(service)
                    if (removed) onServiceClose(service)
                }
            }

            // send back predefined callback result
            svcEventsCallback.second(json)
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
                    } catch (ex: Exception) { ex.printStackTrace(); Result.Error<JSONObject>("Endpoint failed with error: ${ex.message ?: "no error message"}") }
                    val resultString = result.serialize().toString(0)

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
        // if was registered, remove from register
        if (config.doRegister) {
            val result = Requester.rawRequest(config.logger, "${config.registerUrl}/remove", service.toJson())
                .get(1000, TimeUnit.MILLISECONDS)
            if (result.isOk()) println("Removal success: ${result.unwrap()}")
            else println("Removal fail: ${result.error()}")
        }

        // do general shutdown
        server.stop(1000, 1000)
        config.logger.info("Shutdown ${config.id}, hidden = $hidden")
    }
}