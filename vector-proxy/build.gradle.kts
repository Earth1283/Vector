plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
    id("com.gradleup.shadow")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("dev.vector.proxy.VectorProxyKt")
}

dependencies {
    implementation(project(":vector-api"))
    implementation(project(":vector-api-kotlin"))
    implementation(project(":vector-compat"))

    implementation("io.netty:netty-all:4.1.111.Final")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("org.jline:jline:3.26.3")
    implementation("net.kyori:adventure-api:4.17.0")
    implementation("net.kyori:adventure-text-serializer-gson:4.17.0")
    implementation("com.akuleshov7:ktoml-core:0.5.1")
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("org.jetbrains.exposed:exposed-core:0.52.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.52.0")

    implementation("ch.qos.logback:logback-classic:1.5.6")
    implementation("org.flywaydb:flyway-core:9.22.3")
    runtimeOnly("org.xerial:sqlite-jdbc:3.46.0.0")
}

tasks.shadowJar {
    archiveBaseName.set("vector")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
}
