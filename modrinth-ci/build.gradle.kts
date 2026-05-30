plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "8.3.5"
    application
}

kotlin { jvmToolchain(21) }

application {
    mainClass.set("dev.vector.ci.MainKt")
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
