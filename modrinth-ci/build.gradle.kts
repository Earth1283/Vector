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

tasks.named("run") {
    mustRunAfter(":vector-proxy:shadowJar")
}

// shadowJar uses archiveClassifier="" so its output path collides with the plain jar.
// The application plugin's distribution and start-script tasks consume that file without
// declaring a dependency, which trips Gradle's task-input validation. Wire every such
// task to both producers so ordering is well-defined regardless of which jar they read.
tasks.withType<org.gradle.api.tasks.application.CreateStartScripts>().configureEach {
    dependsOn(tasks.jar, tasks.shadowJar)
}
tasks.withType<AbstractArchiveTask>().configureEach {
    if (name != "jar" && name != "shadowJar") dependsOn(tasks.jar, tasks.shadowJar)
}
