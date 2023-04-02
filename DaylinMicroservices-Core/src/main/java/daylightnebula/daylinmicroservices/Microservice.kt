package daylightnebula.daylinmicroservices

import com.orbitz.consul.Consul
import com.orbitz.consul.model.agent.ImmutableRegCheck
import com.orbitz.consul.model.agent.ImmutableRegistration
import com.orbitz.consul.model.health.Service
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

class Microservice(
    // name and id for the service, these are used to identify the
    private val name: String,
    private val tags: List<String>,
    val uuid: UUID = UUID.randomUUID(),

    // port of this service, if zero, a port will be found automatically
    private var port: Int = 0,

    // an endpoint cannot return null, null is only returned as a result if there is an error
    // this is used instead of ktor routing so that errors can be handled
    private val endpoints: HashMap<String, (json: JSONObject) -> JSONObject> = hashMapOf(),

    // multicast socket info, used for broadcasting service create and shutdown
    private val multicastAddress: InetAddress = InetAddress.getByName("224.0.0.200"),
    private val multicastPort: Int = 3000,
    private val multicastSocket: MulticastSocket = MulticastSocket(multicastPort),

    // logger
    private val logger: KLogger = KotlinLogging.logger("Node ${name.ifBlank { uuid.toString() }}"),

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
        val oldServices = serviceCache.filter { !curServices.contains(it.key) }
        oldServices.forEach {
            serviceCache.remove(it.key)
            onServiceClose(it.value)
        }
    }

    // get service functions
    fun getService(name: String): Service? { return serviceCache[name] }
    fun getServices(): Collection<Service> { return serviceCache.values }
    fun getServiceWithTag(tag: String): Service? { return serviceCache.values.firstOrNull { it.tags.contains(tag) } }

    // just start the server on this thread
    override fun run() {
        // finish setting up sockets
        multicastSocket.joinGroup(InetSocketAddress(multicastAddress, multicastPort), NetworkInterface.getByName("bge0"))

        // create microservice server and endpoints
        setupPort()
        setupDefaults()
        server = embeddedServer(Netty, port = port, module = module)

        // setup health check
        val check = ImmutableRegCheck.builder()
            .http("http://host.docker.internal:$port/")
            .interval("10s")
            .timeout("1s")
            .deregisterCriticalServiceAfter("1s")
            .build()

        // setup consul
        consul = Consul.builder().withUrl("http://localhost:8500").build()
        consul.agentClient().register(
            ImmutableRegistration.builder()
                .id(name)
                .tags(tags)
                .name(name)
                .address("localhost")
                .port(port)
                .check(check)
                .build()
        )

        // start server
        server.start(wait = false)
        println("Created with port $port")

        // broadcast join packet
        broadcastPacket(getJoinPacket().toString(0).toByteArray())
    }

    // make request to services
    fun request(name: String, endpoint: String, json: JSONObject): CompletableFuture<JSONObject>? {
        val service = getService(name) ?: return null
        val address = "http://${service.address}:${service.port}/$endpoint"
        return Requester.rawRequest(logger, address, json)
    }

    // function that sends a byte array to a given socket
    private fun broadcastPacket(data: ByteArray) {
        val socket = DatagramSocket()
        socket.broadcast = true
        val sizeBuffer = ByteBuffer.allocate(4).putInt(data.size).array()
        socket.send(DatagramPacket(sizeBuffer, 4, multicastAddress, multicastPort))
        socket.send(DatagramPacket(data, data.size, multicastAddress, multicastPort))
        socket.close()
    }

    // function that creates service join packet
    private lateinit var cachedJoinPacket: JSONObject
    private fun getJoinPacket(): JSONObject {
        if (!this::cachedJoinPacket.isInitialized)
            cachedJoinPacket = (endpoints["info"]?.let { it(JSONObject()) } ?: JSONObject())
                .put("status", "join")
                .put("port", port)
        return cachedJoinPacket
    }

    // function that creates service close packet
    private lateinit var cachedClosePacket: JSONObject
    private fun getClosePacket(): JSONObject {
        if (!this::cachedClosePacket.isInitialized)
            cachedClosePacket = (endpoints["info"]?.let { it(JSONObject()) } ?: JSONObject()).put("status", "close")
        return cachedClosePacket
    }

    // function that finds an open port if necessary
    private fun setupPort() {
        // if port has already been set, skip
        if (port != 0) return

        // grab a blank port by creating a server socket, getting its port and then close the server
        val sSocket = ServerSocket(0)
        port = sSocket.localPort
        sSocket.close()
        logger.info("Found open port $port")
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
                .put("name", name)
                .put("uuid", uuid)
                .put("endpoints", JSONArray().putAll(endpoints.keys))
        }
    }

    // dynamically create routing using the given endpoints
    private val module: Application.() -> Unit = {
        logger.info("Creating endpoints...")
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
                logger.info("Created endpoint /$name")
            }
        }
        logger.info("Setup finished")
    }

    // function that stops everything
    fun dispose(hidden: Boolean = false) {
        if (!hidden) broadcastPacket(getClosePacket().toString(0).toByteArray())
        serviceCacheThread.dispose()
        consul.agentClient().deregister(name)
        server.stop(1000, 1000)
        logger.info { "Shutdown $name, hidden = $hidden" }
        super.join()
    }
}