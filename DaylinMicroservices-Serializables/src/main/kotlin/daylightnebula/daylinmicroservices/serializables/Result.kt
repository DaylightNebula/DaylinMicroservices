package daylightnebula.daylinmicroservices.serializables

import org.json.JSONObject

/********************************
 * Serializable result that can be Ok or an Error.
 * Similar to the Rust version of this.
 ********************************/
sealed class Result<T>() {
    class Ok<T>(val value: T): Result<T>()
    class Error<T>(val error: String): Result<T>()

    // is functions
    fun isOk(): Boolean = this is Ok
    fun isError(): Boolean =  this is Error

    // getter functions
    fun unwrap(): T = (this as Ok).value
    fun error(): String = (this as Error).error

    // default functions
    fun unwrapOr(default: T) = if (this is Ok) this.value else default
    fun unwrapOrError(error: String) = if (this is Ok) this.value else throw ResultException(error)

    // serialization
    fun serialize(): JSONObject {
        if (isOk())
            return JSONObject().put("ok", unwrap())
        else
            return JSONObject().put("error", error())
    }
}

// deserialization
fun JSONObject.toResult(): Result<JSONObject> {
    if (has("ok")) return Result.Ok(getJSONObject("ok"))
    else if (has("error")) return Result.Error(getString("error"))
    else {
        println("WARN Converting objects to directly to ok results is deprecated!")
        return Result.Ok(this)
    }
}

class ResultException(message: String): Exception(message)