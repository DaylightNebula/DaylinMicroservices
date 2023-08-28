package daylightnebula.daylinmicroservices.core

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
    private val debugRequests: Boolean = false,
    private val doRegister: Boolean = true,

    internal val mapRequestAddress: (serv: ServiceInfo, endpoint: String) -> String = { service, endpoint ->
        if (debugRequests) println("Docker? ${config.isRunningInsideDocker()}, address: ${service.address}, endpoint $endpoint")
        "${service.address}/$endpoint"
    },

    // callbacks for when a service starts and closes
    private val onServiceOpen: (serv: ServiceInfo) -> Unit = {},
    private val onServiceClose: (serv: ServiceInfo) -> Unit = {}
): Thread() {

    // server stuff
    private lateinit var server: NettyApplicationEngine
    private val registerUrl = System.getenv("registerUrl") ?: "http://172.90.0.3:2999"
    private lateinit var myServiceInfo: ServiceInfo

    // service cache
    private lateinit var serviceCache: MutableMap<UUID, ServiceInfo>
    private val serviceCacheThread = if (doRegister) loopingThread(1000) {
        if (!this::myServiceInfo.isInitialized) return@loopingThread

        // send get request to register
        request("$registerUrl/get", JSONObject()).whenComplete { result, _ ->
            // if result is ok, process it, otherwise, log error
            if (result.isOk()) {
                // get current services map
                val curServices = result.unwrap().getJSONArray("services").map {
                    val info = ServiceInfo(it as JSONObject)
                    info.id to info
                }.toMap()

                // if service cache is not initialized, initialize with current services
                if (!this::serviceCache.isInitialized) {
                    serviceCache = curServices.toMutableMap()
                    return@whenComplete
                }

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
            } else config.logger.error("Register get request failed with error: ${result.error()}")
        }
    } else null

    // just start the server on this thread
    override fun run() {
        // create microservice server and endpoints
        setupDefaults()
        server = embeddedServer(Netty, port = config.port, module = module)

        // if in docker container, auto grab own address
        val myAddress = if (config.isRunningInsideDocker()) {
            val hosts = File("/etc/hosts")
            val hostLine = hosts.readLines().last().split("\\s".toRegex())
            hostLine.first()
        } else {
            null
        }

        // assemble service info
        myServiceInfo = ServiceInfo(
            config.id, config.name, config.tags,
            "http://$myAddress:${config.port}"
        )

        // register if necessary
        if (doRegister) {
            // send add request
            request(
                "$registerUrl/add",
                myServiceInfo.toJson()
            ).whenComplete { result, _ ->
                // if add request succeeded, send first service get request
                if (result.isOk()) request(
                    "$registerUrl/get",
                    JSONObject()
                ).whenComplete { result, _ ->
                    // if the first get request passed, initialize service cache with the results
                    if (result.isOk()) {
                        serviceCache = result.unwrap().getJSONArray("services").map {
                            val info = ServiceInfo(it as JSONObject)
                            info.id to info
                        }.toMap().toMutableMap()
                    }
                    // otherwise, log error
                    else config.logger.error("First service cache get request failed with error: ${result.error()}")
                }
                // otherwise, log error
                else config.logger.error("Failed to register self with error: ${result.error()}")
            }
        }

        // start server
        server.start(wait = false)
        println("Created with port ${config.port}")
    }

    // get service functions
    fun getService(uuid: UUID): ServiceInfo? = safeCache { it[uuid] }
    fun getServiceWithName(name: String): ServiceInfo? = safeCache { c -> c.asSequence().firstOrNull { it.value.name == name }?.value }
    fun getServiceWithTag(tag: String): ServiceInfo? = safeCache { c -> c.asSequence().firstOrNull { it.value.tags.contains(tag) }?.value }
    fun getServices(): Map<UUID, ServiceInfo> = safeCache()
    fun getServicesWithName(name: String): Map<UUID, ServiceInfo> = safeCache { c -> c.filter { it.value.name == name } }
    fun getServicesWithTag(tag: String): Map<UUID, ServiceInfo> = safeCache { c -> c.filter { it.value.tags.contains(tag) } }

    // get helper functions
    fun <T: Any?> safeCache(callback: (cache: Map<UUID, ServiceInfo>) -> T): T =
        callback(safeCache())
    fun safeCache(): Map<UUID, ServiceInfo> = if (this::serviceCache.isInitialized) serviceCache else mapOf()

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
        // deregister if necessary
        if (doRegister) request(
            "$registerUrl/remove",
            myServiceInfo.toJson()
        )

        // stop the rest
        serviceCacheThread?.join(100)
        server.stop(1000, 1000)

        config.logger.info("Shutdown ${config.id}, hidden = $hidden")
    }
}