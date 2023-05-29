package daylightnebula.daylinmicroservices.serializables

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.*

// object that can be validated against a schema
class DynamicObject() {
    private val elements = hashMapOf<String, Any>()

    // convert any to json with the given check key
    @OptIn(ExperimentalEncodingApi::class)
    private fun anyToJson(checkKey: SchemaElement, value: Any): Result =
        try {
            when (checkKey) {
                is SchemaElement.Array -> if (value is Array<*> && checkKey.size == value.size) Result.Ok(
                    JsonArray(
                        value.map { subElement ->
                            if (subElement == null) throw IllegalArgumentException("Elements of list cannot be null")
                            val result = anyToJson(checkKey.subElementKey, subElement)
                            if (result.isOk())
                                result.unwrap()
                            else throw IllegalArgumentException("Could not convert input to json array")
                        })
                ) else Result.Error("Invalid input array")

                is SchemaElement.Boolean -> if (value is Boolean) validate(
                    checkKey,
                    JsonPrimitive(value)
                ) else Result.Error("Invalid input boolean")

                is SchemaElement.Data -> if (value is ByteArray) validate(
                    checkKey,
                    JsonPrimitive(Base64.encode(value))
                ) else Result.Error("Invalid byte array as data")

                is SchemaElement.List -> if (value is Collection<*>) Result.Ok(JsonArray(value.map { subElement ->
                    if (subElement == null) throw IllegalArgumentException("Elements of list cannot be null")
                    val result = anyToJson(checkKey.subElementKey, subElement)
                    if (result.isOk())
                        result.unwrap()
                    else throw IllegalArgumentException("Could not convert input to json list")
                })) else Result.Error("Invalid input list")

                is SchemaElement.Number -> if (value is Number) validate(
                    checkKey,
                    JsonPrimitive(value)
                ) else Result.Error("Invalid input number")

                is SchemaElement.Object -> if (value is DynamicObject) {
                    // validate the object to a result
                    val result = value.validateToResult(checkKey.schema)

                    // if the object is ok
                    if (result.isOk()) Result.Ok(result.unwrap())
                    else result as Result.Error
                } else Result.Error("Invalid input object")

                is SchemaElement.String -> if (value is String) validate(
                    checkKey,
                    JsonPrimitive(value)
                ) else Result.Error("Invalid input string")
            }
        } catch (ex: IllegalArgumentException) { Result.Error(ex.message ?: "Unknown error") }

    // validate if a check key matches a value
    private fun validate(checkKey: SchemaElement, value: JsonElement): Result = if (checkKey.isValid(value)) Result.Ok(value) else Result.Error("An element did not pass schema validation!")

    // add any to object
    fun put(key: String, value: Any): DynamicObject {
        elements[key] = value
        return this
    }

    // get final object that validates it first
    fun validateToResult(schema: Schema): Result {
        // map new json object
        val map = hashMapOf<String, JsonElement>()
        for ((key, element) in elements) {
            // attempt to get schema element
            val schemaElement = schema[key]
            if (schemaElement == null) return Result.Error("Could not find schema element with key $key")

            // get result and validate it
            val result = anyToJson(schemaElement, element)
            if (result.isError()) return result as Result.Error

            // save result
            map[key] = result.unwrap()
        }
        val json = JsonObject(map)

        // validate the schema and return the result
        return if (schema.validate(json))
            Result.Ok(json)
        else Result.Error("Dynamic Object did not pass final validation")
    }
}