package dev.vector.proxy.storage

import dev.vector.api.storage.StorageBackend
import dev.vector.api.storage.TransactionScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

class SqliteStorageBackend(file: String) : StorageBackend {

    private val logger = LoggerFactory.getLogger(SqliteStorageBackend::class.java)
    private val jdbcUrl = "jdbc:sqlite:$file"
    private val conn: Connection
    private val lock = Mutex()

    init {
        val dir = File(file).parentFile
        if (dir != null && !dir.exists()) dir.mkdirs()
        conn = DriverManager.getConnection(jdbcUrl)
        conn.createStatement().use { it.execute("PRAGMA journal_mode=WAL") }
        conn.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
        logger.info("SQLite storage opened: {}", file)
    }

    // Internal helpers (no locking, must be called while lock is held) 

    private fun <T> doQuery(sql: String, params: List<Any?>, mapper: (ResultSet) -> T): List<T> {
        return conn.prepareStatement(sql).use { stmt ->
            params.forEachIndexed { i, p -> stmt.setObject(i + 1, p) }
            stmt.executeQuery().use { rs ->
                val rows = mutableListOf<T>()
                while (rs.next()) rows.add(mapper(rs))
                rows
            }
        }
    }

    private fun doExecute(sql: String, params: List<Any?>): Int {
        return conn.prepareStatement(sql).use { stmt ->
            params.forEachIndexed { i, p -> stmt.setObject(i + 1, p) }
            stmt.executeUpdate()
        }
    }

    // StorageBackend impl 

    override suspend fun <T> query(sql: String, params: List<Any?>, mapper: (ResultSet) -> T): List<T> =
        withContext(Dispatchers.IO) {
            lock.withLock { doQuery(sql, params, mapper) }
        }

    override suspend fun execute(sql: String, params: List<Any?>): Int =
        withContext(Dispatchers.IO) {
            lock.withLock { doExecute(sql, params) }
        }

    override suspend fun <T> transaction(block: suspend TransactionScope.() -> T): T =
        withContext(Dispatchers.IO) {
            lock.withLock {
                conn.autoCommit = false
                try {
                    val result = txScope.block()
                    conn.commit()
                    result
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            }
        }

    override fun migrate(pluginId: String, classLoader: ClassLoader, location: String) {
        val tableName = "flyway_${pluginId.replace(Regex("[^a-zA-Z0-9]"), "_")}"
        val prev = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = classLoader
        try {
            val result = Flyway.configure()
                .dataSource(jdbcUrl, null, null)
                .locations("classpath:$location")
                .table(tableName)
                .load()
                .migrate()
            if (result.migrationsExecuted > 0) {
                logger.info("[{}] Applied {} migration(s)", pluginId, result.migrationsExecuted)
            }
        } finally {
            Thread.currentThread().contextClassLoader = prev
        }
    }

    override fun close() {
        try { conn.close() } catch (_: Exception) {}
        logger.info("SQLite storage closed.")
    }

    // Inner scope used inside transaction {} — bypasses lock (already held).
    private val txScope = object : TransactionScope {
        override suspend fun <T> query(sql: String, params: List<Any?>, mapper: (ResultSet) -> T): List<T> =
            doQuery(sql, params, mapper)

        override suspend fun execute(sql: String, params: List<Any?>): Int =
            doExecute(sql, params)
    }
}
