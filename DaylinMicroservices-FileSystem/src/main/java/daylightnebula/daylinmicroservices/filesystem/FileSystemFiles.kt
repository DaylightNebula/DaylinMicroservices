package daylightnebula.daylinmicroservices.filesystem

import daylightnebula.daylinmicroservices.filesysteminterface.HashUtils
import daylightnebula.daylinmicroservices.filesysteminterface.makeSureExists
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.IllegalArgumentException
import java.security.MessageDigest

object FileSystemFiles {

    private val cacheDirectory = File("files").makeSureExists(true)
    private val cacheLogFile = File("filelog.json").makeSureExists(false, defaultText = "{}")
    private val cacheTree = loadTree(cacheDirectory)

    // a function that triggers the creation of this object
    fun triggerStart() { println("Trigger") }

    // function that prints the entire file tree
    private fun printTree(path: String, element: LogTreeElement, tabs: Int = 0) {
        for (i in 0 until tabs) print("  ")
        print("- ")
        if (element is LogEntryDirectory)
            println("Directory: path $path children count ${element.children.size}")
        else if (element is LogEntry)
            println("File: path $path hash ${element.hash}")

        for (child in element.children)
            printTree(child.key, child.value, tabs + 1)
    }

    // update log functions
    fun pushFile(path: String, bytes: ByteArray) {
        // path in log tree MUST exist
        // get elements in path
        val pathElements = path.split("/")
        var root: LogTreeElement = cacheTree
        var didChange = false
        
        // loop through all elements in the list except the first and the last (first is root and the last is the file)
        var path = ""
        for (i in 1 until pathElements.size - 1) {
            val element = pathElements[i]
            path += "/$element"
            
            // get the next root from the roots children list
            var nextRoot = root.children[path]
            
            // if no next root was found, make one
            if (nextRoot == null) {
                val file = File(cacheDirectory, path)
                nextRoot = LogEntryDirectory(file, hashMapOf())
                didChange = true
            }
            
            // update root with next root
            root = nextRoot
        }

        // get path and hash for the file
        path += pathElements.last()
        val hash = HashUtils.getChecksumFromBytes(MessageDigest.getInstance("MD5"), bytes)

        // check if file already exists in root
        val preExistingFile = root.children[path] as? LogEntry

        // check if the file does not already exist or its hash has changed
        if (preExistingFile == null || preExistingFile.hash != hash) {
            // get the target file and write the file to it
            val file = File(cacheDirectory, path)
            file.writeBytes(bytes)

            // save the change
            root.children[path] = LogEntry(file, hash, System.currentTimeMillis())
            didChange = true
        }
        
        // if there was a change, save it
        if (didChange)
            saveLog()
    }
    fun getFile(path: String): File? {
        // setup basic info
        val pathElements = path.split("/")
        var root: LogTreeElement = cacheTree

        // get root node
        var path = ""
        for (i in 1 until pathElements.size - 1) {
            val element = pathElements[i]
            path += "/$element"

            // get the next root from the roots children list, if it does not exist, return null
            var nextRoot = root.children[path] ?: return null

            // update root with next root
            root = nextRoot
        }

        // get the final file, return null if it does not exist
        path += pathElements.last()
        val file = root.children[path]?.file ?: return null

        // if the file does not exist, remove the file from the log and save the change, then return nothing
        if (!file.exists()) {
            root.children.remove(path)
            saveLog()
            return null
        }

        // return the file
        return file
    }
    fun deleteFile(path: String): Boolean {
        // setup basic info
        var root: LogTreeElement = cacheTree
        val pathElements = path.split("/")

        // get root, return false if it cannot be found
        var path = ""
        for (i in 1 until pathElements.size - 1) {
            val element = pathElements[i]
            path += "/$element"

            // get the next root from the roots children list, if it does not exist, return false
            var nextRoot = root.children[path] ?: return false

            // update root with next root
            root = nextRoot
        }

        // try to get the file
        path += pathElements.last()
        val entry = root.children[path] ?: return false

        // if the file does not exist, return false
        if (!entry.file.exists()) {
            root.children.remove(path)
            saveLog()
            return false
        }

        // remove the log and save the changes
        root.children.remove(path)
        saveLog()

        // return true if the delete operation succeeds
        try { entry.file.deleteRecursively(); return true } catch (ex: Exception) { return false }
    }

    // get functions
    fun getHash(path: String): String? {
        // setup basic info
        var root: LogTreeElement = cacheTree
        val pathElements = path.split("/")

        // get root, return false if it cannot be found
        var path = ""
        for (i in 1 until pathElements.size - 1) {
            val element = pathElements[i]
            path += "/$element"

            // get the next root from the roots children list, if it does not exist, return false
            var nextRoot = root.children[path] ?: return null

            // update root with next root
            root = nextRoot
        }

        // try to get the file and then return the hash
        path += pathElements.last()
        return (root.children[path] as? LogEntry)?.hash ?: return null
    }
    fun getFileListAtPath(path: String): List<String>? {
        // setup basic info
        var root: LogTreeElement = cacheTree
        val pathElements = path.split("/")

        // get root, return false if it cannot be found
        var path = ""
        for (i in 1 until pathElements.size - 1) {
            val element = pathElements[i]
            path += "/$element"

            // get the next root from the roots children list, if it does not exist, return false
            var nextRoot = root.children[path] ?: return null

            // update root with next root
            root = nextRoot
        }

        // try to get the file
        path += pathElements.last()
        val entry = root.children[path] ?: return null

        // return the name and extensions of the entries children
        return entry.children.map { it.value.file.name }
    }

    // get change log functions
    fun getChangeLog(sinceTime: Long): Map<String, String> {
        val map = hashMapOf<String, String>()
        populateChangeLogMap(map, cacheTree, sinceTime)
        return map
    }
    private fun populateChangeLogMap(map: HashMap<String, String>, root: LogEntryDirectory, sinceTime: Long) {
        root.children.forEach { (path, element) ->
            if (element is LogEntry && element.lastUpdate >= sinceTime) {
                map[path] = element.hash
            } else if (element is LogEntryDirectory)
                populateChangeLogMap(map, element, sinceTime)
        }
    }

    // load and save log functions
    fun updateLog(json: JSONObject) {}

    private fun saveLog() = cacheLogFile.writeText(
        cacheTree
            .convertToJson()
            .put("lastUpdateTime", System.currentTimeMillis())
            .toString(0)
    )

    // log tree json stuff
    private fun loadTree(file: File): LogEntryDirectory {
        return LogEntryDirectory(cacheDirectory, hashMapOf(*loadTreeRecursively(cacheDirectory, "")))
    }
    private fun loadTreeRecursively(root: File, inCachePath: String): Array<Pair<String, LogTreeElement>> {
        return root.listFiles()?.map { file ->
            val path = "$inCachePath/${file.name}"
            Pair(path, 
                if (file.isDirectory)
                    (LogEntryDirectory(file, hashMapOf(*loadTreeRecursively(file,  path))))
                else LogEntry(file, HashUtils.getCheckSumFromFile(MessageDigest.getInstance("MD5"), file), file.lastModified())
            )
        }?.toTypedArray() ?: throw IllegalArgumentException("Cannot list files from given root")
    }

    // log tree stuff
    abstract class LogTreeElement(val file: File, val children: HashMap<String, LogTreeElement>) { abstract fun convertToJson(): JSONObject }
    class LogEntryDirectory(_file: File, _children: HashMap<String, LogTreeElement>) : LogTreeElement(_file, _children) {
        override fun convertToJson(): JSONObject {
            return JSONObject()
                .put("children", JSONArray().putAll(children.map { it.value.convertToJson().put("path", it.key) }))
        }
    }
    class LogEntry(_file: File, val hash: String, val lastUpdate: Long): LogTreeElement(_file, hashMapOf()) {
        override fun convertToJson(): JSONObject {
            return JSONObject()
                .put("filename", file.name)
                .put("hash", hash)
        }
    }
}