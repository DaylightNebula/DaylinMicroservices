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

    // some config options
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
    private var lastCacheUpdate = System.currentTimeMillis()
    private fun updateServiceCache() {
        if (!doRegister || !this::myServiceInfo.isInitialized) return

        // send get request to register
        val result = request("$registerUrl/get", JSONObject()).get()

        // if error, log and cancel
        if (result.isError()) {
            config.logger.error("Register get request failed with error: ${result.error()}")
            return
        }

        // get current services map
        val curServices = result.unwrap().getJSONArray("services").map {
            val info = ServiceInfo(it as JSONObject)
            info.id to info
        }.toMap()
        lastCacheUpdate = System.currentTimeMillis()

        // if service cache is not initialized, initialize with current services
        if (!this::serviceCache.isInitialized) {
            serviceCache = curServices.toMutableMap()
            return
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
    }

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
            "http://$myAddress:${config.port}",
            config.doRegCheck, config.regCheckInterval
        )

        // register if necessary
        if (doRegister) {
            // send add request
            request(
                "$registerUrl/add",
                myServiceInfo.toJson()
            ).whenComplete { result, _ ->
                // if add request succeeded, send first service get request
                if (result.isOk()) updateServiceCache()
                // otherwise, log error
                else config.logger.error("Failed to register self with error: ${result.error()}")
            }
        }

        // start server
        server.start(wait = false)
        println("Created with port ${config.port}")
    }

    // get service functions
    fun getService(uuid: UUID): ServiceInfo? = safeCacheProvider { it[uuid] }
    fun getServiceWithName(name: String): ServiceInfo? = safeCacheProvider { c -> c.asSequence().firstOrNull { it.value.name == name }?.value }
    fun getServiceWithTag(tag: String): ServiceInfo? = safeCacheProvider { c -> c.asSequence().firstOrNull { it.value.tags.contains(tag) }?.value }
    fun getServices(): Map<UUID, ServiceInfo> = safeCache()
    fun getServicesWithName(name: String): Map<UUID, ServiceInfo> = safeCacheProvider { c -> c.filter { it.value.name == name } }
    fun getServicesWithTag(tag: String): Map<UUID, ServiceInfo> = safeCacheProvider { c -> c.filter { it.value.tags.contains(tag) } }

    // Provides a cache that is guaranteed to exist to the callback.  That callback may be rerun after a cache update if it returns nothing the first time
    fun <T: Any?> safeCacheProvider(callback: (cache: Map<UUID, ServiceInfo>) -> T): T {
        // call callback for a result a first time
        var result = callback(safeCache())

        // if no result or a blank map, update the service cache and try again
        if (result == null || (result is Map<*, *> && result.isEmpty())) {
            updateServiceCache()
            result = callback(safeCache())
        }

        // return the result
        return result
    }

    // Returns a service cache that is guaranteed to be initialized.  This will automatically update the service cache if it out of date.
    fun safeCache(): Map<UUID, ServiceInfo> {
        // if service cache is out of date, update service cache
        if (System.currentTimeMillis() - lastCacheUpdate > config.maxServiceCacheAge) updateServiceCache()

        // return service cache if it is initialized
        return if (this::serviceCache.isInitialized) serviceCache else mapOf()
    }

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

        // do the same for register_ping
        val regCallback = endpoints["register_ping"] ?: (Schema() to { _ -> Result.Ok(JSONObject()) })
        endpoints["register_ping"] = regCallback.first to {
            // update service cache if cache is dirty
            if (it.getBoolean("isCacheDirty")) updateServiceCache()

            // pass back callback result
            regCallback.second(it)
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
        server.stop(1000, 1000)

        config.logger.info("Shutdown ${config.id}, hidden = $hidden")
    }
}