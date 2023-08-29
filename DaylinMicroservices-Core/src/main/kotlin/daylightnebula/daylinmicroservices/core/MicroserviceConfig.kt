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
    val id: UUID,                     // the id of the microservice
    val name: String,                   // the name of the microservice
    val tags: List<String>,             // the tags of the microservice, like what its type is
    var port: Int = System.getenv("port")?.toIntOrNull() ?: 0,
    val maxServiceCacheAge: Long = System.getenv("maxServiceCacheAge")?.toLongOrNull() ?: 60000L,
    val doRegCheck: Boolean = true,     // allow consul to periodically perform a check to see if the service is active
    val logger: Logger =                // the logger that this service will write its output too
        KotlinLogging.logger("Microservice $name")
) {
    // make sure port is set
    init { setupPort() }

    // load from a json object
    constructor(json: JSONObject): this(
        UUID.fromString(json.optString("id", "")),
        json.optString("name", ""),
        json.optJSONArray("tags")?.map { it as String } ?: listOf(),
        json.optInt("port", 0),
        json.optLong("maxServiceCacheAge", 60000)
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