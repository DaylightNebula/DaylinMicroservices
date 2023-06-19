package daylightnebula.daylinmicroservices.serializables

import org.json.JSONArray
import org.json.JSONObject

// extra info to go with
sealed class SchemaElement(val isValid: (element: Any) -> kotlin.Boolean) {
    // basic types
    class Boolean(): SchemaElement({ element -> element is kotlin.Boolean })
    class Number(): SchemaElement({ element -> element is kotlin.Number })
    class String(): SchemaElement({ element -> element is kotlin.String })
    class Data(): SchemaElement({ element -> element is ByteArray })

    // complex objects
    class List(val subElementKey: SchemaElement): SchemaElement({ element -> element is Collection<*> || element is JSONArray })
    class Array(val subElementKey: SchemaElement, val size: Int): SchemaElement({ element -> (element is kotlin.Array<*> && element.size == size) || (element is JSONArray && element.length() == size) })
    class Object(val schema: Schema): SchemaElement({ element -> element is JSONObject && element.validate(schema).isOk() })

    // default object
    class Default(val schema: SchemaElement, val default: Any): SchemaElement({ element -> schema.isValid(element) })

    // optionals
    class Optional(val schema: SchemaElement): SchemaElement({ element -> schema.isValid(element) })
}

// class description of schema with a list of names and their corresponding schema elements
class Schema(val elements: HashMap<String, SchemaElement>) {
//    // validate json object with schema
//    fun validate(json: JSONObject): Result<JSONObject> {
//        // check if all schema elements are present in json and valid
//        var output = true
//        elements.forEach { (key, element) ->
//            // check if element is not optional
//            if (element !is SchemaElement.Optional) {
//                // make sure json has the given key
//                if (!json.has(key)) {
//                    output = false; return@forEach
//                }
//
//                // validate the json at the key
//                if (!element.isValid(json[key]!!)) output = false
//            }
//            // otherwise, validate optionally
//            else {
//                // if json contains key, validate it
//                if (json.has(key) && !element.isValid(json[key]!!))
//                    output = false
//            }
//        }
//        return output
//    }

    // get function
    operator fun get(key: String) = elements[key]
}

// function to make schemas easier to assemble
fun Schema(vararg elements: Pair<String, SchemaElement>): Schema =
    Schema(hashMapOf(*elements))