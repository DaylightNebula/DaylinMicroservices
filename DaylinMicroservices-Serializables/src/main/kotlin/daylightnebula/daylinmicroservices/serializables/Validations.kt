package daylightnebula.daylinmicroservices.serializables

import org.json.JSONObject

fun JSONObject.validate(schema: Schema): Result<JSONObject> {
    return validateToResult(this, schema)
}

// get final object that validates it first
fun validateToResult(input: JSONObject, schema: Schema): Result<JSONObject> {
    // create map that can be used by json object
    val map = hashMapOf<String, Any>()
    for (key in schema.elements.keys) {

        // attempt to get schema element
        val schemaElement = schema[key]
            ?: return Result.Error("Could not find schema element with key $key")

        // get element
        var element = input.opt(key)

        // if no element found
        if (element == null) {
            if (schemaElement is SchemaElement.Default)
                element = schemaElement.default
            else if (schemaElement !is SchemaElement.Optional)
                return Result.Error("Key $key was not found in input json, and schema element was not optional or default!")
        }
        // otherwise, validate that element
        else {
            println("Schema: ${schemaElement.javaClass}, Element: ${element.javaClass}")
            if (!schemaElement.isValid(element))
                return Result.Error("Schema validation failed at key $key")
        }

        // save result if not null
        if (element != null) map[key] = element
    }

    // make sure all default values are present
    schema.elements
        .filter { it.value is SchemaElement.Default && !map.containsKey(it.key) }
        .forEach { key, value -> map[key] = (value as SchemaElement.Default).default }

    // validate the schema and return the result
    return Result.Ok(JSONObject(map))
}

// validate if a check key matches a value
private fun validate(checkKey: SchemaElement, value: Any): Result<Any> = if (checkKey.isValid(value)) Result.Ok(value) else Result.Error("An element did not pass schema validation!")
