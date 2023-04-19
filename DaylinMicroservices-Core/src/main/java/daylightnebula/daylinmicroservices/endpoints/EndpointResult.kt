package daylightnebula.daylinmicroservices.endpoints

import org.json.JSONArray
import org.json.JSONObject

class EndpointResult private constructor(private var result: JSONObject?, private var error: String?) {
    constructor(_result: JSONObject): this(_result, null) {
        if (result!!.has("error")) {
            error = result!!.getString("error")
            result = null
        }
    }
    constructor(error: String): this(null, error)

    fun getResult(): JSONObject {
        return result ?: JSONObject().put("error", error)
    }

    fun isError() = error != null
    fun isOk() = result != null

    fun getError(): String? {
        return error
    }
}