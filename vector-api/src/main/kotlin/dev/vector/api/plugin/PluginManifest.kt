package dev.vector.api.plugin

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: String,
    val entrypoint: String,
    val language: PluginLanguage,
    val hardDeps: List<String>,
    val softDeps: List<String>,
)
