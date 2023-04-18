package daylightnebula.daylinmicroservices

import mu.KotlinLogging
import org.json.JSONObject
import org.slf4j.Logger
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.function.Predicate


// just a basic config that can load from json or file
data class MicroserviceConfig(
    val id: String,                     // the id of the microservice
    val name: String,                   // the name of the microservice
    val tags: List<String>,             // the tags of the microservice, like what its type is
    var port: Int = 0,                  // the port that this service will run on
    var consulUrl: String = "",         // the url that consul is on
    var consulRefUrl: String = "",      // this is what consul will use to reference this microservice
    val logger: Logger =                // the logger that this service will write its output too
        KotlinLogging.logger("Microservice $name")
) {
    init {
        // make sure port is set
        setupPort()

        // make sure consul addresses are setup
        setupConsulUrl()
        setupConsulRefUrl()
    }

    // load from a json object
    constructor(json: JSONObject): this(
        json.optString("id", ""),
        json.optString("name", ""),
        json.optJSONArray("tags")?.map { it as String } ?: listOf(),
        json.optInt("port", 0),
        json.optString("consulUrl", ""),
        json.optString("consulRefUrl", "")
    )

    // load from a json object in a file
    constructor(file: File): this(
        if (file.exists()) JSONObject(file.readText()) else throw IllegalArgumentException("Config file given must exist!  Path: ${file.absolutePath}")
    )

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

    // function that sets up the consul url to defaults if necessary
    private fun setupConsulUrl() {
        // make sure consul url is blank
        if (consulUrl.isNotBlank()) return

        // set consul url
        consulUrl = if (isRunningInsideDocker()) "http://host.docker.internal:8500" else "http://localhost:8500"
    }

    // function that sets the consul ref url to defaults if necessary
    private fun setupConsulRefUrl() {
        // make sure consul ref url is blank
        if (consulRefUrl.isNotBlank()) return

        // get my ip
        val whatismyip = URL("http://checkip.amazonaws.com")
        val `in` = BufferedReader(InputStreamReader(whatismyip.openStream()))
        val ip = `in`.readLine()

        // set consul ref url
        consulRefUrl = "http://$ip:${port}/"
    }

    // function that checks if this process is running in a docker container
    fun isRunningInsideDocker(): Boolean {
        try {
            Files.lines(Paths.get("/proc/1/cgroup")).use { stream ->
                return stream.anyMatch(Predicate { line: String ->
                    line.contains(
                        "/docker"
                    )
                })
            }
        } catch (e: IOException) {
            return false
        }
    }
}