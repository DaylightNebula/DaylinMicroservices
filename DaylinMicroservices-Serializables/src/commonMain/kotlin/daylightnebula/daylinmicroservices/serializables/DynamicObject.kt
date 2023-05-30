package daylightnebula.daylinmicroservices.serializables

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.*
import kotlin.reflect.KClass

// object that can be validated against a schema
class DynamicObject(private val elements: HashMap<String, Any> = hashMapOf<String, Any>()) {
    // add any to object
    fun put(key: String, value: Any): DynamicObject {
        elements[key] = value
        return this
    }

    // get final object that validates it first
    fun validateToResult(schema: Schema): Result<JsonObject> {
        // create map that can be used by json object
        val map = hashMapOf<String, JsonElement>()
        for ((key, element) in elements) {
            // attempt to get schema element
            val schemaElement = schema[key]
                ?: return Result.Error("Could not find schema element with key $key")

            // get result and validate it
            val result = anyToJson(schemaElement, element)
            if (result.isError()) return Result.Error(result.error())

            // save result
            map[key] = result.unwrap()
        }

        // make sure all default values are present
        schema.elements
            .filter { it.value is SchemaElement.Default && !map.containsKey(it.key) }
            .forEach { key, value -> map[key] = (value as SchemaElement.Default).default }

        // create new json object
        val json = JsonObject(map)

        // validate the schema and return the result
        return if (schema.validate(json))
            Result.Ok(json)
        else Result.Error("Dynamic Object did not pass final validation")
    }

    // get functions
    operator fun get(key: String): Any? = elements[key]
    fun getInt(key: String): Int? = elements[key] as? Int
    fun getFloat(key: String): Float? = elements[key] as? Float
    fun getDouble(key: String): Double? = elements[key] as? Double
    fun getLong(key: String): Long? = elements[key] as? Long
    fun getData(key: String): ByteArray? = elements[key] as? ByteArray
    fun getString(key: String): String? = elements[key] as? String
    fun getList(key: String): List<*>? = elements[key] as? List<*>
    fun getArray(key: String): Array<*>? = elements[key] as? Array<*>

    companion object {
        fun deserialize(schema: Schema, json: JsonObject): Result<DynamicObject> {
            // output object
            val map = hashMapOf<String, Any>()

            // loop through all schema elements and add them to list
            for ((key, element) in schema.elements) {
                if (element is SchemaElement.Default) {
                    val result =
                        if (json.containsKey(key)) convertJsonToAny(key, element.schema, json[key]!!)
                        else convertJsonToAny(key, element.schema, element.default)

                    if (result.isOk()) map[key] = result.unwrap()
                    else {
                        val result2 = convertJsonToAny(key, element.schema, element.default)
                        if (result2.isOk()) map[key] = result.unwrap()
                        else return Result.Error("Default failed")
                    }
                } else if (element is SchemaElement.Optional) {
                    val result =
                        if (json.containsKey(key)) convertJsonToAny(key, element.schema, json[key]!!)
                        else Result.Error("Not used")

                    if (result.isOk()) map[key] = result.unwrap()
                } else {
                    // convert json to any
                    val result =
                        if (json.containsKey(key)) convertJsonToAny(key, element, json[key]!!)
                        else Result.Error("Could not convert json to any with key $key")

                    // if result is ok, save result to map, otherwise, return error
                    if (result.isOk()) map[key] = result.unwrap()
                    else return Result.Error(result.error())
                }
            }

            // return result
            return Result.Ok(DynamicObject(map))
        }

        @OptIn(ExperimentalEncodingApi::class)
        private fun convertJsonToAny(key: String, schema: SchemaElement, element: JsonElement): Result<Any> = when(schema) {
            is SchemaElement.Boolean ->
                if (element.jsonPrimitive.booleanOrNull != null) Result.Ok(element.jsonPrimitive.boolean)
                else Result.Error("Could not get boolean with key $key")
            is SchemaElement.Number ->
                if (element.jsonPrimitive.intOrNull != null) Result.Ok(element.jsonPrimitive.int)
                else if (element.jsonPrimitive.floatOrNull != null) Result.Ok(element.jsonPrimitive.float)
                else if (element.jsonPrimitive.doubleOrNull != null) Result.Ok(element.jsonPrimitive.double)
                else if (element.jsonPrimitive.longOrNull != null) Result.Ok(element.jsonPrimitive.long)
                else Result.Error("Could not get number with key $key")
            is SchemaElement.String ->
                if (element.jsonPrimitive.contentOrNull != null) Result.Ok(element.jsonPrimitive.content)
                else Result.Error("Could not get string with key $key")
            is SchemaElement.Data ->
                if (element.jsonPrimitive.contentOrNull != null) Result.Ok(Base64.decode(element.jsonPrimitive.content))
                else Result.Error("Could not get data with key $key")
            is SchemaElement.List ->
                Result.Ok(element.jsonArray.map { element ->
                    convertJsonToAny(key, schema.subElementKey, element)
                })
            is SchemaElement.Array ->
                Result.Ok(element.jsonArray.map { element ->
                    convertJsonToAny(key, schema.subElementKey, element)
                }.toTypedArray())
            is SchemaElement.Object -> {
                val result = deserialize(schema.schema, element.jsonObject)
                if (result.isOk()) Result.Ok(result.unwrap()) else Result.Error(result.error())
            }
            else -> Result.Error("Unexpected")
        }
    }
}



// convert any to json with the given check key
@OptIn(ExperimentalEncodingApi::class)
fun anyToJson(checkKey: SchemaElement, value: Any): Result<JsonElement> = try {
    when (checkKey) {
        is SchemaElement.Boolean -> convertFromBoolean(checkKey, value)
        is SchemaElement.Number -> convertFromNumber(checkKey, value)
        is SchemaElement.String -> convertFromString(checkKey, value)
        is SchemaElement.Data -> convertFromData(checkKey, value)
        is SchemaElement.Array -> convertFromArray(checkKey, value)
        is SchemaElement.List -> convertFromList(checkKey, value)
        is SchemaElement.Object -> convertFromObject(checkKey, value)
        is SchemaElement.Default -> convertFromDefault(checkKey.schema, value, checkKey.default)
        is SchemaElement.Optional -> convertFromOptional(checkKey.schema, value)
    }
} catch (ex: IllegalArgumentException) {
    Result.Error(ex.message ?: "Unknown error")
}

// basic conversions
private fun convertFromBoolean(schema: SchemaElement, value: Any): Result<JsonElement> =
    if (value is Boolean) validate(schema, JsonPrimitive(value))
    else Result.Error("Invalid input boolean")
private fun convertFromNumber(schema: SchemaElement, value: Any): Result<JsonElement> =
    if (value is Number) validate(schema, JsonPrimitive(value))
    else Result.Error("Invalid input number")
private fun convertFromString(schema: SchemaElement, value: Any): Result<JsonElement> =
    if (value is String) validate(schema, JsonPrimitive(value))
    else Result.Error("Invalid input string")
@OptIn(ExperimentalEncodingApi::class)
private fun convertFromData(schema: SchemaElement, value: Any): Result<JsonElement> =
    if (value is ByteArray) validate(schema, JsonPrimitive(Base64.encode(value)))
    else Result.Error("Invalid data byte array")

// complex conversions
private fun convertFromList(schema: SchemaElement.List, value: Any): Result<JsonElement> =
    if (value is Collection<*>) Result.Ok(JsonArray(value.map { subElement ->
        if (subElement == null) throw IllegalArgumentException("Elements of list cannot be null")
        val result = anyToJson(schema.subElementKey, subElement)
        if (result.isOk())
            result.unwrap()
        else throw IllegalArgumentException("Could not convert input to json list")
    })) else Result.Error("Invalid input list")
private fun convertFromArray(schema: SchemaElement.Array, value: Any): Result<JsonElement> =
    if (value is Array<*> && schema.size == value.size) Result.Ok(
        JsonArray(
            value.map { subElement ->
                if (subElement == null) throw IllegalArgumentException("Elements of list cannot be null")
                val result = anyToJson(schema.subElementKey, subElement)
                if (result.isOk())
                    result.unwrap()
                else throw IllegalArgumentException("Could not convert input to json array")
            })
    ) else Result.Error("Invalid input array")
private fun convertFromObject(schema: SchemaElement.Object, value: Any): Result<JsonElement> =
    if (value is DynamicObject) {
        val result = value.validateToResult(schema.schema)
        if (result.isOk()) Result.Ok(result.unwrap()) else Result.Error("jo")
    } else Result.Error("Invalid input dynamic object")

// default and optional conversions
private fun convertFromOptional(schema: SchemaElement, value: Any): Result<JsonElement>
    = anyToJson(schema, value)
private fun convertFromDefault(schema: SchemaElement, value: Any, default: JsonElement): Result<JsonElement>
    = Result.Ok(anyToJson(schema, value).unwrapOr(default))

// validate if a check key matches a value
private fun validate(checkKey: SchemaElement, value: JsonElement): Result<JsonElement> = if (checkKey.isValid(value)) Result.Ok(value) else Result.Error("An element did not pass schema validation!")
