package daylightnebula.daylinmicroservices.filesysteminterface

import daylightnebula.daylinmicroservices.Microservice
import daylightnebula.daylinmicroservices.requests.requestByTag
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.HashMap

val cacheFolder = File("cache").makeSureExists(true)

fun Microservice.requestFile(path: String): CompletableFuture<File?> {
    val file = File(cacheFolder, path)

    // if the file exists, check if the hash code matches before making the request
    return if (file.exists()) {
        val future = CompletableFuture<File?>()

        // request the hash from the file system, if error, complete the future as null, otherwise, complete the future with requireRequestFile
        getHashForFile(path).whenComplete { hash, _ ->
            if (hash == HashUtils.getCheckSumFromFile(MessageDigest.getInstance("MD5"), file))
                future.complete(file)
            else
                requireRequestFile(path).whenComplete { file, _ -> future.complete(file) }
        }

        future
    } else requireRequestFile(path)
}
fun Microservice.requireRequestFile(path: String): CompletableFuture<File?> {
    // create future for when the file is returned
    val future = CompletableFuture<File?>()

    // make a request to the file system for the file at the path
    requestByTag("file_system", "get", JSONObject().put("path", path))?.whenComplete { json, _ ->
        // if the returned json, create a file at the given path, write the bytes passed from the file system to that file, and then return the file
        if (json.has("bytes"))
            future.complete(File(cacheFolder, path).apply {
                writeBytes(Base64.getDecoder().decode(json.getString("bytes")))
            })
        // otherwise, output the error message and complete the future as null
        else {
            this.config.logger.warn("File request returned error ${json.optString("error", "???")}")
            future.complete(null)
        }
    } ?: future.complete(null)

    // return the future
    return future
}

fun Microservice.deleteFile(path: String): CompletableFuture<Boolean> {
    val future = CompletableFuture<Boolean>()

    // complete the future with the success state of the delete request
    requestByTag("file_system", "delete", JSONObject().put("path", path))?.whenComplete { json, _ ->
        future.complete(json.optBoolean("success", false))
    } ?: future.complete(false)

    return future
}

fun Microservice.pushFileBytes(path: String, bytes: ByteArray): CompletableFuture<Boolean> {
    val future = CompletableFuture<Boolean>()

    // send a push request to the file system, and then complete with the success state
    requestByTag(
        "file_system",
        "push",
        JSONObject().put("path", path).put("bytes", Base64.getEncoder().encodeToString(bytes))
    )?.whenComplete { json, _ ->
        // report an error if there is one
        if (json.has("error")) this.config.logger.warn("File system push request for path $path returned with error ${json.getString("error")}")

        // complete the future with the success state
        future.complete(json.optBoolean("success", false))
    } ?: future.complete(false)

    return future
}
fun Microservice.pushFile(path: String, file: File): CompletableFuture<Boolean> = pushFileBytes(path, file.readBytes())

fun Microservice.getHashForFile(path: String): CompletableFuture<String?> {
    val future = CompletableFuture<String?>()

    // complete the future with the result from the file system
    requestByTag("file_system", "hash", JSONObject().put("path", path))?.whenComplete { json, _ ->
        future.complete(json.optString("hash"))
    } ?: future.complete(null)

    return future
}

fun Microservice.doesFileExist(path: String): CompletableFuture<Boolean> {
    val future = CompletableFuture<Boolean>()

    // if the hash is not null, then complete the future as success, otherwise, fail
    getHashForFile(path).whenComplete { str, _ ->
        future.complete(str != null)
    }

    return future
}

fun Microservice.listFilesAtPath(path: String): CompletableFuture<List<String>?> {
    val future = CompletableFuture<List<String>?>()

    requestByTag("file_system", "list", JSONObject().put("path", path))?.whenComplete { json, _ ->
        // if error from request, report error
        if (json.has("error")) this.config.logger.warn("List files request at path $path failed with error: ${json.getString("error")}")

        // complete the future with the files if they were given
        future.complete(json.optJSONArray("files")?.map { it as String })
    } ?: future.complete(null)

    return future
}

fun Microservice.getChangeLog(time: Long): CompletableFuture<HashMap<String, String>> {
    val future = CompletableFuture<HashMap<String, String>>()

    // make a change log request to the server
    requestByTag("file_system", "change_log", JSONObject().put("sinceTime", time))?.whenComplete { json, _ ->
        // complete the future with the result converted to a hashmap
        val output = hashMapOf<String, String>()
        json.keys().forEach { key ->
            output.put(key, json.getString(key))
        }
        future.complete(output)
    } ?: future.complete(hashMapOf())

    return future
}

fun File.makeSureExists(isDirectory: Boolean, defaultText: String = ""): File {
    // if type does not match but this file exists, delete the old one
    if (this.exists() && isDirectory != isDirectory()) this.delete()

    // if this file does not exist or the type does not match, make the file or directory exist
    if (!this.exists() || isDirectory() != isDirectory) {
        if (isDirectory) mkdirs()
        else {
            if (parentFile != null) parentFile.mkdirs()
            writeText(defaultText)
        }
    }

    // return this so that this can be used for something else
    return this
}