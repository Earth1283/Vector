plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":vector-api"))
    api("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("net.kyori:adventure-text-serializer-gson:4.17.0")
    implementation("net.kyori:adventure-text-serializer-plain:4.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.slf4j:slf4j-api:2.0.13")
}
