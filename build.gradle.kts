plugins {
    kotlin("jvm") version "2.1.0" apply false
    kotlin("plugin.serialization") version "2.1.0" apply false
    id("com.gradleup.shadow") version "8.3.5" apply false
}

allprojects {
    group = "dev.vector"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

tasks.register("shadowJar") {
    dependsOn(":vector-proxy:shadowJar")
}
