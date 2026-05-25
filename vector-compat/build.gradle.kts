plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":vector-api"))
    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
}
