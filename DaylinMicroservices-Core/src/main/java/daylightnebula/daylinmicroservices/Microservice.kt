package daylightnebula.daylinmicroservices

import com.orbitz.consul.Consul
import com.orbitz.consul.model.agent.ImmutableRegCheck
import com.orbitz.consul.model.agent.ImmutableRegistration
import com.orbitz.consul.model.health.Service
import com.sun.security.ntlm.Server
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KLogger
import mu.KotlinLogging
import org.json.JSONArray
import org.json.JSONObject
import java.net.*
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.HashMap

class Microservice(
    private val config: MicroserviceConfig,
    private val endpoints: HashMap<String, (json: JSONObject) -> JSONObject>,

    // callbacks for when a service starts and closes
    private val onServiceOpen: (serv: Service) -> Unit = {},
    private val onServiceClose: (serv: Service) -> Unit = {}
): Thread() {

    // server stuff
    val uuid = UUID.randomUUID()
    private lateinit var server: NettyApplicationEngine
    private lateinit var consul: Consul

    // service cache
    private var serviceCache = mutableMapOf<UUID, Service>()
    private val serviceCacheThread = loopingThread(1000) {
        if (!this::consul.isInitialized) return@loopingThread
        val curServices = consul.agentClient().services.mapKeys { UUID.fromString(it.key) }

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
            .http(config.consulRefUrl)
            .interval("10s")
            .timeout("1s")
            .deregisterCriticalServiceAfter("1s")
            .build()

        // setup consul
        consul = Consul.builder().withUrl(config.consulUrl).build()
        consul.agentClient().register(
            ImmutableRegistration.builder()
                .id(uuid.toString())
                .tags(config.tags)
                .name(config.name)
                .address("localhost")
                .port(config.port)
                .check(check)
                .build()
        )

        // start server
        server.start(wait = false)
        println("Created with port ${config.port}")
    }

    // get service functions
    fun getService(uuid: UUID): Service? { return serviceCache[uuid] }
    fun getServiceWithName(name: String): Map.Entry<UUID, Service>? { return serviceCache.asSequence().firstOrNull { it.value.service == name } }
    fun getServiceWithTag(tag: String): Map.Entry<UUID, Service>? { return serviceCache.asSequence().firstOrNull { it.value.tags.contains(tag) } }
    fun getServices(): Map<UUID, Service> { return serviceCache }
    fun getServicesWithName(name: String): Map<UUID, Service> { return serviceCache.filter { it.value.service == name } }
    fun getServicesWithTag(tag: String): Map<UUID, Service> { return serviceCache.filter { it.value.tags.contains(tag) } }

    // make request to services
    fun request(service: Service, endpoint: String, json: JSONObject): CompletableFuture<JSONObject> {
        json.put("broadcasted", false)
        val address = "http://${service.address}:${service.port}/$endpoint"
        return Requester.rawRequest(config.logger, address, json)
    }
    fun requestByUUID(uuid: UUID, endpoint: String, json: JSONObject): CompletableFuture<JSONObject>? {
        val service = getService(uuid) ?: return null
        return request(service, endpoint, json)
    }
    fun requestByName(name: String, endpoint: String, json: JSONObject): CompletableFuture<JSONObject>? {
        val serviceEntry = getServiceWithName(name) ?: return null
        return request(serviceEntry.value, endpoint, json)
    }
    fun requestByTag(tag: String, endpoint: String, json: JSONObject): CompletableFuture<JSONObject>? {
        val serviceEntry = getServiceWithTag(tag) ?: return null
        return request(serviceEntry.value, endpoint, json)
    }

    // broadcast to services
    fun broadcastRequestByName(name: String, endpoint: String, json: JSONObject): List<CompletableFuture<JSONObject>> {
        json.put("broadcasted", true)
        return getServicesWithName(name).map { entry -> request(entry.value, endpoint, json) }
    }
    fun broadcastRequestByTag(tag: String, endpoint: String, json: JSONObject): List<CompletableFuture<JSONObject>> {
        json.put("broadcasted", true)
        return getServicesWithTag(tag).map { entry -> request(entry.value, endpoint, json) }
    }

    // function that creates service join packet
    private lateinit var cachedJoinPacket: JSONObject
    private fun getJoinPacket(): JSONObject {
        if (!this::cachedJoinPacket.isInitialized)
            cachedJoinPacket = (endpoints["info"]?.let { it(JSONObject()) } ?: JSONObject())
                .put("status", "join")
                .put("port", config.port)
        return cachedJoinPacket
    }

    // function that creates service close packet
    private lateinit var cachedClosePacket: JSONObject
    private fun getClosePacket(): JSONObject {
        if (!this::cachedClosePacket.isInitialized)
            cachedClosePacket = (endpoints["info"]?.let { it(JSONObject()) } ?: JSONObject()).put("status", "close")
        return cachedClosePacket
    }

    // function that just sets up default "/" endpoint and "/info" endpoints
    private fun setupDefaults() {
        // setup ping callback, adding to any preexisting version
        val pingCallback = endpoints[""] ?: { JSONObject() }
        endpoints[""] = { pingCallback(it).put("status", "ok") }

        // do the same with info
        val infoCallback = endpoints["info"] ?: { JSONObject() }
        endpoints["info"] = {
            infoCallback(it)
                .put("name", config.name)
                .put("tags", config.tags)
                .put("endpoints", JSONArray().putAll(endpoints.keys))
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

                    // try to get the result, if an error is thrown, return null
                    val result = try {
                        callback(json)
                    } catch (ex: Exception) { ex.printStackTrace(); null }
                    val resultString = result?.toString(4) ?: ""

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
        consul.agentClient().deregister(name)
        server.stop(1000, 1000)
        config.logger.info("Shutdown $name, hidden = $hidden")
        super.join()
    }
}