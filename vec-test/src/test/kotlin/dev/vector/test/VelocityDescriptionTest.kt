package dev.vector.test

import dev.vector.compat.VelocityDescriptionShim
import dev.vector.compat.VelocityManifest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VelocityDescriptionTest {

    private val fullManifest = VelocityManifest(
        id = "testplugin",
        name = "Test Plugin",
        version = "1.5.0",
        description = "A desc",
        url = "https://example.com",
        authors = listOf("Alice"),
        dependencies = listOf(VelocityManifest.Dependency("dep1", false)),
        main = "com.example.TestPlugin",
    )

    @Test
    fun `id is always present`() {
        val desc = VelocityDescriptionShim(fullManifest)
        assertEquals("testplugin", desc.id)
    }

    @Test
    fun `optional fields return correct optionals`() {
        val desc = VelocityDescriptionShim(fullManifest)
        assertEquals("Test Plugin", desc.name.orElse(null))
        assertEquals("1.5.0", desc.version.orElse(null))
        assertEquals("A desc", desc.description.orElse(null))
        assertEquals("https://example.com", desc.url.orElse(null))
        assertEquals(listOf("Alice"), desc.authors)
    }

    @Test
    fun `null optional fields return empty`() {
        val minimal = VelocityManifest(id = "min", main = "com.example.Min")
        val desc = VelocityDescriptionShim(minimal)
        assertTrue(desc.name.isEmpty)
        assertTrue(desc.version.isEmpty)
        assertTrue(desc.description.isEmpty)
        assertTrue(desc.url.isEmpty)
        assertTrue(desc.source.isEmpty)
    }

    @Test
    fun `dependencies are mapped correctly`() {
        val desc = VelocityDescriptionShim(fullManifest)
        val deps = desc.dependencies
        assertEquals(1, deps.size)
        val dep = deps.first()
        assertEquals("dep1", dep.id)
        assertFalse(dep.isOptional)
    }
}
