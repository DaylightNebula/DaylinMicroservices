import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.21"
    kotlin("plugin.serialization") version "1.8.21"
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

repositories {
    mavenCentral()
}

dependencies {
    // json
    implementation("org.json:json:20230227")

    // ktor server
    implementation("io.ktor:ktor-server-core:2.2.4")
    implementation("io.ktor:ktor-server-netty:2.2.4")

    // ktor client
    implementation("io.ktor:ktor-client-core:2.2.4")
    implementation("io.ktor:ktor-client-cio:2.2.4")
    implementation("io.ktor:ktor-client-logging:2.2.4")

    // logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.6")

    // consul
    implementation("com.orbitz.consul:consul-client:1.5.3")

    // other modules
    implementation(project(":DaylinMicroservices-Serializables"))

    // tests
    implementation(kotlin("test"))
}

tasks.getByName<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveFileName.set("DaylinMicroservices-Core.jar")
}

//kotlin {
//    jvm()
//
//    sourceSets {
//        val commonMain by getting {
//        }
//
//        val commonTest by getting {
//            dependencies {
//                implementation("org.jetbrains.kotlin:kotlin-test:1.8.21")
//                implementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
//                implementation(project(":DaylinMicroservices-Serializables"))
//            }
//        }
//    }
//}