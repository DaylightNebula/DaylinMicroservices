package daylightnebula.daylinmicroservices.filesystem

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.IllegalArgumentException
import java.security.MessageDigest

object FileSystemFiles {

    private val cacheDirectory = File("files").makeSureExists(true)
    private val cacheLogFile = File("filelog.json").makeSureExists(false, defaultText = "{}")
    private val cacheTree = loadTree(cacheDirectory)

    init {
        printTree(cacheTree)
    }

    fun trigger() { println("Trigger") }

    private fun printTree(element: LogTreeElement, tabs: Int = 0) {
        for (i in 0 until tabs) print("  ")
        print("- ")
        if (element is LogEntryDirectory)
            println("Directory: path ${element.inCachePath} children count ${element.children.size}")
        else if (element is LogEntry)
            println("File: path ${element.inCachePath} hash ${element.hash}")

        for (child in element.children)
            printTree(child, tabs + 1)
    }

    // update log functions
    fun pushFile(path: String, bytes: ByteArray) {
        // make path exist

    }

    // load and save log functions
    fun updateLog(json: JSONObject) {}
    fun saveLog() = cacheLogFile.writeText(cacheTree.convertToJson().toString(0))

    // log tree json stuff
    private fun loadTree(file: File): LogTreeElement {
        return LogEntryDirectory("", cacheDirectory, loadTreeRecursively(cacheDirectory, ""))
    }
    private fun loadTreeRecursively(root: File, inCachePath: String): List<LogTreeElement> {
        return root.listFiles()?.map { file ->
            val path = "$inCachePath/${file.name}"
            if (file.isDirectory)
                LogEntryDirectory(path, file, loadTreeRecursively(file,  path))
            else LogEntry(path, file, HashUtils.getCheckSumFromFile(MessageDigest.getInstance("MD5"), file), file.lastModified())
        }
            ?: throw IllegalArgumentException("Cannot list files from given root")
    }

    // log tree stuff
    abstract class LogTreeElement(val inCachePath: String, val file: File, val children: List<LogTreeElement>) { abstract fun convertToJson(): JSONObject }
    class LogEntryDirectory(_inCachePath: String, _file: File, _children: List<LogTreeElement>) : LogTreeElement(_inCachePath, _file, _children) {
        override fun convertToJson(): JSONObject {
            return JSONObject()
                .put("path", inCachePath)
                .put("children", JSONArray().putAll(children.map { it.convertToJson() }))
        }
    }
    class LogEntry(_inCachePath: String, _file: File, val hash: String, val lastUpdate: Long): LogTreeElement(_inCachePath, _file, listOf()) {
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