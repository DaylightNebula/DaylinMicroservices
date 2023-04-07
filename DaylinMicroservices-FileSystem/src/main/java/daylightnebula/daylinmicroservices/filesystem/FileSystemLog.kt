package daylightnebula.daylinmicroservices.filesystem

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object FileSystemLog {

    private val cacheDirectory = File("files").makeSureExists(true)
    private val cacheLogFile = File("filelog.json").makeSureExists(false, defaultText = "{}")
    private val cacheTree = loadTree(cacheDirectory)

    // log functions
    fun updateLog(json: JSONObject) {}
    fun saveLog() = cacheLogFile.writeText(cacheTree.convertToJson().toString(0))

    // log tree json stuff
    fun loadTree(file: File): LogTreeElement {
        return LogEntryDirectory("/", listOf())
    }

    // log tree stuff
    abstract class LogTreeElement(val children: List<LogTreeElement>) { abstract fun convertToJson(): JSONObject }
    class LogEntryDirectory(val path: String, _children: List<LogTreeElement>) : LogTreeElement(_children) {
        override fun convertToJson(): JSONObject {
            return JSONObject()
                .put("path", path)
                .put("children", JSONArray().putAll(children.map { it.convertToJson() }))
        }
    }
    class LogEntry(val file: File, val hash: String, val lastUpdate: Long): LogTreeElement(listOf()) {
        override fun convertToJson(): JSONObject {
            return JSONObject()
                .put("path", file.path)
                .put("filename", file.name)
                .put("hash", hash)
                .put("lastUpdate", lastUpdate)
        }
    }
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