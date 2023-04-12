# Daylin Microservices
This is a simple backbone for kotlin based microservices.  
This uses consul for discovery, so you will need that to be setup to use this.  
You can use the run-consul shell or batch files to quickly create and set up consul for testing.

## Creating a Microservice
Microservices can quickly be created using just a microservice config (described below) and a hashmap of endpoints (also described below).  All they will do is connect consul and then respond to those endpoints until their dispose function is called or the application is shutdown.  Microservices do not keep their program alive by themselves and will shutdown if the main thread stops.

Here is an example of a service that once started will simple return a json object with hello set to true if its hello endpoint is called:
```kotlin
val service = Microservice(
    MicroserviceConfig("test", listOf()),
    endpoints = hashMapOf(
        "hello" to { json ->
            JSONObject().put("hello", true)
        }
    )
)
```

## Microservice Config
A microservice config simply describes how a microservice set its self up to be seen by other microservices.  All values listed below can be given to the microservice config OR you can give a file or json object to the microservice and it will automatically load the values it needs from that file or json object.

Arguments (in this order in Kotlin):
- name: String
  - The name of the microservice.  This service may be identified with this name.
- tags: List of strings
  - A list of tags given to the service.  Services may broadcast json packets to all services with a given tag.
  - For example: when a change to the file system is requested, ALL services with the tag "file_system" will receive that change request.
- uuid: UUID
  - A unique identifier for this service.
  - By default, this is a random UUID.
- port: Int
  - Representing the port for the service to run on.
  - If not set or set to 0, the service will automatically find an open port on the host machine.
- consulUrl: String
  - A url to the consul instance you are using.
  - By default, this will reference "https://localhost:8500"
  - This MUST be a HTTPS url
- consulRefUrl: String
  - The url that consul will use to reference this service.
  - By default, this will reference "http://host.docker.internal:<port>/" on windows or mac, and "http://localhost:<port>/" on linux
  - This MUST be a HTTP url, not HTTPS
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
  "uuid": "d79337df-6bd2-4da1-837b-e64c8cd70318",
  "consulUrl": "https://localhost:8500/",
  "consulRefUrl": "http://localhost:54345/"
}
```

## Endpoints
The endpoints hashmap is a hashmap with a string key, that represents the callbacks name, and a callback that takes in and returns a json object.  That callback is called whenever a get request is made with the given name.  However if the get request is made without a json object with it, the endpoint WILL NOT be called.

Here is an example endpoint that returns a json object with "hi" set to true if the endpoint "bob" is called
```kotlin
"bob" to { json ->
    JSONObject().put("hi", true)
}
```
