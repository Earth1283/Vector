package dev.vector.api.storage

import java.sql.ResultSet

interface TransactionScope {
    suspend fun <T> query(sql: String, params: List<Any?> = emptyList(), mapper: (ResultSet) -> T): List<T>
    suspend fun execute(sql: String, params: List<Any?> = emptyList()): Int
}

interface StorageBackend : TransactionScope {
    suspend fun <T> transaction(block: suspend TransactionScope.() -> T): T
    fun migrate(pluginId: String, classLoader: ClassLoader, location: String = "db/migration")
    fun close()
}
