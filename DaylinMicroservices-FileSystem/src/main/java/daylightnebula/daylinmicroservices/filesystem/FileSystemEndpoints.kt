package daylightnebula.daylinmicroservices.filesystem

import daylightnebula.daylinmicroservices.requests.broadcastRequestByTag
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

val endpoints = hashMapOf<String, (json: JSONObject) -> JSONObject>(
    // get a list of files at a given path
    "list" to { json ->
        // ask the file system for all the files at the path
        val files = FileSystemFiles.getFileListAtPath(json.optString("path", "???"))

        // return the files, return an error object if no files found
        if (files == null) JSONObject("error", "path not found")
        else
            JSONObject().put(
                "files",
                JSONArray().putAll(files)
            )
    },

    // get the hash code of a file at the given path
    "hash" to { json ->
        // ask the file system for the hash for the file with the given path
        val hash = FileSystemFiles.getHash(json.optString("path", "???"))

        // return the hash, return an error object if no hash
        if (hash == null) JSONObject().put("error", "path was not found or was a directory")
        else JSONObject().put("hash", hash)
    },

    // get the change log since a given time
    "change_log" to { json ->
        // ask the file system for the change log
        val log = FileSystemFiles.getChangeLog(json.optLong("sinceTime", 0L))

        // convert and then return a json object with the log info and the file bytes converted to base 64
        val outJson = JSONObject()
        log.forEach { (path, file) -> outJson.put(path, Base64.getEncoder().encodeToString(file.readBytes())) }
        outJson
    },

    // return the file
    "get" to { json ->
        // ask the file system for the file
        val file = FileSystemFiles.getFile(json.optString("path", "???"))

        // if the file is not null and exists, return a json object with a base 64 string containing the files info
        if (file != null && file.exists())
            JSONObject().put("bytes", Base64.getEncoder().encodeToString(file.readBytes()))
        else JSONObject().put("error", "could not find given file")
    },

    // add the given file to the file system, then broadcast the change to all file system services if necessary
    "push" to { json ->
        var error = ""

        // get file info
        val path = json.optString("path")
        val base64string = json.optString("bytes")
        val bytes = if (base64string != null) {
            try { Base64.getDecoder().decode(base64string) }
            catch (_: Exception) { null }
        } else null

        // push the file
        if (path != null && base64string != null && bytes != null)
            FileSystemFiles.pushFile(path, bytes)
        // if the push could not be started, save the error
        else if (path == null) error = "path must be supplied"
        else if (base64string == null) error = "bytes must be supplied"
        else error = "could not convert bytes string to byte array"

        // if the input json is not marked broadcast, broadcast the packet
        if (json.optBoolean("broadcast"))
            service.broadcastRequestByTag("file_system", "push", json)

        // if error is not blank, return the error, otherwise, report operation successful
        if (error.isNotBlank())
            JSONObject().put("error", error).put("success", false)
        else
            JSONObject().put("success", true)
    },

    // delete the given file, report the result, and then broadcast the change to all file system services if necessary
    "delete" to { json ->
        // attempt to get the path and delete the file with that path
        val path = json.optString("path")
        val success = if (path != null) FileSystemFiles.deleteFile(path) else false

        // if input json was not marked broadcast, broadcast
        if (json.optBoolean("broadcast", false))
            service.broadcastRequestByTag("file_system", "delete", json)

        // return if the operation was successful
        if (success) JSONObject().put("success", true)
        else JSONObject().put("success", false).put("error", "could not delete the file at the path specified")
    }
)