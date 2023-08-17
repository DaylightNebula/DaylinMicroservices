# Daylin Microservices
[![](https://jitpack.io/v/DaylightNebula/DaylinMicroservices.svg)](https://jitpack.io/#DaylightNebula/DaylinMicroservices) \
This is a simple backbone for kotlin based microservices, designed for fast, light-weight use with or without docker.

## Including in a Project
Add jitpack to your repositories list.
```kotlin
repositories {
  maven("https://jitpack.io/")
}
```

Then add this to your dependencies list.  
```kotlin
dependencies {
  implementation("com.github.DaylightNebula.DaylinMicroservices:DaylinMicroservices-Core:0.5.0")
  // 0.5.0 may need to be changed to the lastest version in case I forgot to update this readme file.
  // you can also use master-SNAPSHOT as the version if you are feeling brave. 
  // However, I use master as my development branch so use at your own risk.
}
```

## Creating a Microservice
Microservices can quickly be created using just a microservice config (described below) and a hashmap of endpoints (also described below).
The service instance, once started, will take care of everything needed to talk to other services on its "network".

Here is an example of a service that once started will simply return a json object with hello set to true if its hello endpoint is called:
```kotlin
val service = Microservice(
    MicroserviceConfig(name = "test"),
    endpoints = hashMapOf(
        "hello" to ( Schema() to { json ->
            JSONObject().put("hello", true)
        })
    )
)
```

## Microservice Config
A microservice config simply describes how a microservice set its self up to be seen by other microservices.  All values listed below can be given to the microservice config OR you can give a file or json object to the microservice and it will automatically load the values it needs from that file or json object.
All arguments below can also be set using environment variables, which is useful for those who use a lot of docker containers.

Arguments (in this order in Kotlin):
- id: UUID
  - A unique ID for this service.  This allows multiple services of the same type to easily be referenced independently.  This can be set if you wish, however it is recommended you do not change this.
- name: String
  - The name of the microservice.  This service may be identified with this name.
- tags: List of strings
  - A list of tags given to the service.  Services may broadcast json packets to all services with a given tag.
  - For example: when a change to the file system is requested, ALL services with the tag "file_system" will receive that change request.
- port: Int
  - Representing the port for the service to run on.
  - If not set or set to 0, the service will automatically find an open port on the host machine.
- registerUrl: String
  - A URL to the register that keeps track of all running services so the services can easily talk to each other.
  - Example: "http://host.docker.internal:2999/"
- doRegister: Boolean
  - Determines whether the service will automatically register itself so that other services can see the new service.
- registerUpdateInterval: String
  - Determines how often the register will check if this service is still alive.
  - Example: 1m
- logger: SLF4J Logger
  - IN KOTLIN ONLY
  - Sets the logger that the microservice will use for logging.
  - By default, one is created automatically called "Microservice <name>".

Here is an example json config:
```json
{
  "name": "tester",
  "tags": [ "tester" ],
  "port": 54345,
  "id": "d79337df-6bd2-4da1-837b-e64c8cd70318"
}
```

## Endpoints
The endpoints hashmap is a hashmap with a string key, that represents the callbacks name, and a pair containing a schema and a callback that takes in and returns a json object.  That callback is called whenever a get request is made with the given name.  However, if the get request is made without a json object with it, the endpoint WILL NOT be called.
The schema will automatically be checked against the input to validate if the schema and input match.  If they do not match, the callback will not be run.  The schema can be left blank if you do not with to use it.

Here is an example endpoint that returns a json object with "hi" set to true if the endpoint "bob" is called
```kotlin
"bob" to ( Schema() to { json ->
    JSONObject().put("hi", true)
})
```

## Register
This project has a custom register that keeps track of all services that are part of the network.  A docker image containing the register can be found on https://hub.docker.com with the tag "daylightnebula/daylinmicroservices-register:version".
A custom register is used to allow the services to be as "restful" as possible.  While it is possible to use the register outside of docker, it is not recommended as it has been designed for use in docker containers.

Using Redis is optional but highly recommended, as it gives the register a place to back up the current service registry.
A redis connection will only be initialized if the "redisAddress" environment variable is given.

Here is a recommended docker-compose.yml file for using the register.
```yml
version: '3.9'
services:
  redis:
    image: redis:latest
    ports:
      - 6379:6379
    command: redis-server
  register:
    image: daylightnebula/daylinmicroservices-register:0.5.1
    ports:
      - 2999:2999
    environment:
      - redisAddress=host.docker.internal
```