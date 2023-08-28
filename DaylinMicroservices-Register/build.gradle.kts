import org.gradle.api.JavaVersion
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*

plugins {
    id("java")
    kotlin("jvm") version "1.8.21"
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    // json
    implementation("org.json:json:20230227")

    // ktor server
//    implementation("io.ktor:ktor-server-core:2.2.4")
//    implementation("io.ktor:ktor-server-netty:2.2.4")
//
//    // ktor client
//    implementation("io.ktor:ktor-client-core:2.2.4")
//    implementation("io.ktor:ktor-client-cio:2.2.4")
//    implementation("io.ktor:ktor-client-logging:2.2.4")

    // logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.6")

    // consul
//    implementation("com.orbitz.consul:consul-client:1.5.3")

    // other modules
    implementation(project(":DaylinMicroservices-Core"))
    implementation(project(":DaylinMicroservices-Serializables"))
    implementation("com.github.DaylightNebula:DaylinMicroservices-Redis:0.1.1")

    // tests
    implementation(kotlin("test"))
}

tasks.getByName<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveFileName.set("DaylinMicroservices-Register.jar")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "daylightnebula.daylinmicroservices.register.RegisterKt"
    }
}

task<Exec>("build_tester") {
    dependsOn("shadowJar")
    commandLine("docker", "build", ".", "-t", "daylinmicroservices/register")
}
