plugins {
    kotlin("jvm") version "1.8.20"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

repositories {
    mavenCentral()
}

dependencies {
    // microservices
    implementation(project(":DaylinMicroservices-Core"))
    implementation("com.orbitz.consul:consul-client:1.5.3")

    // json
    implementation("org.json:json:20230227")

    // logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.6")
}
tasks {
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveFileName.set("DaylinMicroservices-CLIInterface.jar")
//        mergeServiceFiles()
        manifest {
            attributes(mapOf("Main-Class" to "daylightnebula.daylinmicroservices.cli.CLIInterfaceKt"))
        }
    }
}