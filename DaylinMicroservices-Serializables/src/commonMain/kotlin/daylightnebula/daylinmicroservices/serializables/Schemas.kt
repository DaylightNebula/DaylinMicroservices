package daylightnebula.daylinmicroservices.serializables

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

// extra info to go with
@Serializable
sealed class SchemaElement(val isValid: (element: JsonElement) -> kotlin.Boolean) {
    // basic types
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

    // get function
    operator fun get(key: String) = elements[key]
}

// function to make schemas easier to assemble
fun Schema(vararg elements: Pair<String, SchemaElement>): Schema =
    Schema(hashMapOf(*elements))