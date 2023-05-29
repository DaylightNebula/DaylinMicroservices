package daylightnebula.daylinmicroservices.serializables

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/********************************
 * Serializable result that can be Ok or an Error.
 * Similar to the Rust version of this.
 ********************************/
@Serializable
sealed class Result() {
    class Ok(val json: JsonElement): Result()
    class Error(val error: String): Result()

    // is functions
    fun isOk(): Boolean = this is Ok
    fun isError(): Boolean =  this is Error

    // getter functions
    fun unwrap(): JsonElement = (this as Ok).json
    fun error(): String = (this as Error).error

    // default functions
    fun unwrapOr(default: JsonElement) = if (this is Ok) this.json else default
    fun unwrapOrError(error: String) = if (this is Ok) this.json else throw ResultException(error)
}

class ResultException(message: String): Exception(message)