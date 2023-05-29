package daylightnebula.daylinmicroservices.serializables

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.*

// object that corresponds to a given schema
class DynamicObject(private val schema: Schema) {
    private val output = hashMapOf<String, JsonElement>()

    // make extracting schemas from schema easier
    constructor(schema: Schema, key: String): this(((schema.elements[key] ?: throw IllegalArgumentException("Key $key was not a valid key in the given schema")) as SchemaElement.Object).schema)

    // convert any to json with the given check key
    @OptIn(ExperimentalEncodingApi::class)
    fun anyToJson(checkKey: SchemaElement, value: Any): JsonElement =
        when (checkKey) {
            is SchemaElement.Array -> if (value is Array<*> && checkKey.size == value.size) JsonArray(value.map { subElement ->
                if (subElement == null) throw IllegalArgumentException("Elements of list cannot be null")
                anyToJson(checkKey.subElementKey, subElement)
            }) else null
            is SchemaElement.Boolean -> if (value is Boolean) validate(checkKey, JsonPrimitive(value)) else null
            is SchemaElement.Data -> if (value is ByteArray) validate(checkKey, JsonPrimitive(Base64.encode(value))) else null
            is SchemaElement.List -> if (value is Collection<*>) JsonArray(value.map { subElement ->
                if (subElement == null) throw IllegalArgumentException("Elements of list cannot be null")
                anyToJson(checkKey.subElementKey, subElement)
            }) else null
            is SchemaElement.Number -> if (value is Number) validate(checkKey, JsonPrimitive(value)) else null
            is SchemaElement.Object -> if (value is DynamicObject && checkKey.schema.validate(value.getOutput())) value.getOutput() else null
            is SchemaElement.String -> if (value is String) validate(checkKey, JsonPrimitive(value)) else null
        } ?: throw IllegalArgumentException("Value of type ${value::class.simpleName} did not pass schema validate!")

    // validate if a check key matches a value
    private fun validate(checkKey: SchemaElement, value: JsonElement): JsonElement? = if (checkKey.isValid(value)) value else null

    // get a check key for the given value
    private fun getCheckKey(key: String): SchemaElement =
        schema.elements[key] ?: throw IllegalArgumentException("No key $key in schema!")

    // add any to object
    fun put(key: String, value: Any): DynamicObject {
        val checkKey = getCheckKey(key)
        val element = anyToJson(checkKey, value)

        if (!checkKey.isValid(element)) throw IllegalArgumentException("Given value with key $key did not validate! ${element::class.simpleName}")
        output[key] = element
        return this
    }

    // add json element to object
    fun put(key: String, value: JsonElement): DynamicObject {
        // get schemas corresponding "check" key
        val checkKey = getCheckKey(key)

        // validate value with the check key
        if (!checkKey.isValid(value)) throw IllegalArgumentException("Given value with key $key did not validate!")

        // add to the output
        output[key] = value
        return this
    }

    // get final object that validates it first
    fun getOutput(): JsonObject {
        val final = JsonObject(output)
        if (schema.validate(final))
            return final
        else
            throw IllegalArgumentException("Given schema and object did not match!")
    }
}