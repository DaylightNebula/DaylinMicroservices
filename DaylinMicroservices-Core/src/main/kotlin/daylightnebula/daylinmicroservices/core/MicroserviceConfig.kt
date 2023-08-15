package daylightnebula.daylinmicroservices.core

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
    val id: UUID = if (System.getenv("id") != null)
        UUID.fromString(System.getenv("id")) else UUID.randomUUID(),                      // the id of the microservice
    val name: String = System.getenv("name") ?: "unnamed",                                // the name of the microservice
    val tags: List<String> = System.getenv("tags")?.split(",") ?: listOf(),    // the tags of the microservice, like what its type is
    var port: Int = System.getenv("port")?.toInt() ?: 0,                                  // the port that this service will run on
    var registerUrl: String = System.getenv("registerUrl") ?: defaultRegisterUrl(),       // url to the current service register
    var doRegister: Boolean = System.getenv("doRegister")?.toBoolean() ?: true,           // determines if the register actions should be performed
    var registerUpdateInterval: String = System.getenv("registerUpdateInterval") ?: "1m", // how often the service register should check if this service is alive
    val logger: Logger =                                                                        // the logger that this service will write its output too
        KotlinLogging.logger("Microservice $name")
) {
    init {
        // make sure port is set
        setupPort()
    }

    // load from a json object
    constructor(json: JSONObject): this(
        UUID.fromString(json.optString("id", System.getenv("id") ?: "")) ?: UUID.randomUUID(),
        json.optString("name", System.getenv("name") ?: "unnamed"),
        json.optJSONArray("tags")?.map { it as String } ?: System.getenv("tags")?.split(",") ?: listOf(),
        json.optInt("port", System.getenv("port")?.toInt() ?: 0),
        json.optString("registerUrl", System.getenv("registerUrl") ?: defaultRegisterUrl()),
        json.optBoolean("doRegister", System.getenv("doRegister")?.toBoolean() ?: true),
        json.optString("registerUpdateInterval", System.getenv("registerUpdateInterval") ?: "1m")
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
}

fun defaultRegisterUrl() = "http://host.docker.internal:2999/"

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