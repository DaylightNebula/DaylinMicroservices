package daylightnebula.daylinmicroservices.endpoints

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

val base64Regex = Regex.fromLiteral("^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)?\$")

// extra info to go with
@Serializable
sealed class SchemaElement(val isValid: (element: JsonElement) -> kotlin.Boolean) {
    class Boolean(): SchemaElement({ element -> element is JsonPrimitive && element.jsonPrimitive.booleanOrNull != null })
    class Number(): SchemaElement({ element -> element is JsonPrimitive && element.jsonPrimitive.doubleOrNull != null })
    class String(): SchemaElement({ element -> element is JsonPrimitive && element.jsonPrimitive.contentOrNull != null })
    class Data(): SchemaElement({ element -> element is JsonPrimitive && element.jsonPrimitive.contentOrNull != null })

    // complex objects
    class List(val subElementKey: SchemaElement): SchemaElement({ element -> element is JsonArray })
    class Array(val subElementKey: SchemaElement, val size: Int): SchemaElement({ element -> element is JsonArray && element.jsonArray.size == size })
    class Object(val schema: Schema): SchemaElement({ element -> element is JsonObject && schema.validate(element) })
}

// class description of schema with a list of names and their corresponding schema elements
@Serializable
class Schema(val elements: HashMap<String, SchemaElement>) {
    // validate json object with schema
    fun validate(json: JsonObject): Boolean {
        // check if all schema elements are present in json and valid
        var output = true
        elements.forEach { (key, element) ->
            // make sure json has the given key
            if (!json.containsKey(key)) { output = false; return@forEach }

            // validate the json at the key
            if (!element.isValid(json[key]!!)) output = false
        }
        return output
    }
}

// function to make schemas easier to assemble
fun Schema(vararg elements: Pair<String, SchemaElement>): Schema =
    Schema(hashMapOf(*elements))

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

// TESTS
val testSchema = Schema(
    "name" to SchemaElement.String(),
    "lastTimes" to SchemaElement.List(SchemaElement.Number()),
    "position" to SchemaElement.Array(SchemaElement.Number(), 3),
    "info" to SchemaElement.Object(Schema(
        "age" to SchemaElement.Number(),
        "image" to SchemaElement.Data(),
        "enabled" to SchemaElement.Boolean(),
    ))
)

val testObject = DynamicObject(testSchema)
    .put("name", "Bobby")
    .put("lastTimes", listOf<Number>())
    .put("position", arrayOf(32, 32, 32))
    .put("info", DynamicObject(testSchema, "info")
        .put("age", 32)
        .put("image", "asfdabwer".toByteArray())
        .put("enabled", false)
    )

fun main() {
    if (testSchema.validate(testObject.getOutput()))
        println("Valid")
    else
        println("Invalid")
}