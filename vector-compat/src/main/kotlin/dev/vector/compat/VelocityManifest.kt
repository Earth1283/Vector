package dev.vector.compat

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class VelocityManifest(
    val id: String = "",
    val name: String? = null,
    val version: String? = null,
    val description: String? = null,
    val url: String? = null,
    val authors: List<String> = emptyList(),
    val dependencies: List<Dependency> = emptyList(),
    val main: String = "",
) {
    data class Dependency(
        val id: String = "",
        val optional: Boolean = false,
    )

    companion object {
        private val GSON = Gson()

        fun parse(json: String): VelocityManifest = GSON.fromJson(json, VelocityManifest::class.java)
    }
}
