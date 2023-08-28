plugins {
    id("java")
    kotlin("jvm") version "1.8.21"
}

repositories {
    mavenCentral()
}

dependencies {
//    implementation(kotlin("stdlib-jvm"))
    implementation("org.json:json:20230227")
    testImplementation(kotlin("test"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}

//kotlin {
//    jvm()
//
//    sourceSets {
//        val commonMain by getting {
//            dependencies {
//            }
//        }
//
//        val commonTest by getting {
//            dependencies {
//                implementation("org.jetbrains.kotlin:kotlin-test:1.8.21")
//                implementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
//            }
//        }
//    }
//}