plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly(project(":vector-api-kotlin"))
    compileOnly(project(":vector-compat"))
    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
}
