package daylightnebula.daylinmicroservices.serializables

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/********************************
 * Serializable result that can be Ok or an Error.
 * Similar to the Rust version of this.
 ********************************/
@Serializable
sealed class Result<T>() {
    class Ok<T>(val json: T): Result<T>()
    class Error<T>(val error: String): Result<T>()

    // is functions
    fun isOk(): Boolean = this is Ok
    fun isError(): Boolean =  this is Error

    // getter functions
    fun unwrap(): T = (this as Ok).json
    fun error(): String = (this as Error).error

    // default functions
    fun unwrapOr(default: T) = if (this is Ok) this.json else default
    fun unwrapOrError(error: String) = if (this is Ok) this.json else throw ResultException(error)
}

class ResultException(message: String): Exception(message)