import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("multiplatform") version "1.8.21"
    kotlin("plugin.serialization") version "1.8.21"
}

repositories {
    mavenCentral()
}

dependencies {

}

kotlin {
    jvm()
    js(IR)

    sourceSets {
        val commonMain by getting {
            dependencies {
                // basics
                implementation(project(":DaylinMicroservices-Serializables"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")

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
            }
        }

        val commonTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test:1.8.21")
                implementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
            }
        }
    }
}