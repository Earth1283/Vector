plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api("net.kyori:adventure-api:4.17.0")
    api("org.slf4j:slf4j-api:2.0.13")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
