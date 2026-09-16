package com.terminus.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.net.URI
import java.sql.Connection

object Database {
    private val dataSource: HikariDataSource by lazy {
        val rawUrl = Env.databaseUrl
        val cloudUri = rawUrl.takeIf { it.startsWith("postgres://") || it.startsWith("postgresql://") }?.let(::URI)
        val config = HikariConfig().apply {
            jdbcUrl = cloudUri?.let { uri ->
                val port = if (uri.port > 0) ":${uri.port}" else ""
                val query = uri.rawQuery?.let { "?$it" }.orEmpty()
                "jdbc:postgresql://${uri.host}$port${uri.path}$query"
            } ?: rawUrl
            username = System.getenv("DATABASE_USER") ?: cloudUri?.userInfo?.substringBefore(':') ?: "terminus"
            password = System.getenv("DATABASE_PASSWORD") ?: cloudUri?.userInfo?.substringAfter(':', "") ?: "terminus"
            maximumPoolSize = 8
            minimumIdle = 1
            isAutoCommit = true
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
        }
        HikariDataSource(config).also { source ->
            Flyway.configure().dataSource(source).load().migrate()
        }
    }

    fun <T> query(block: (Connection) -> T): T = dataSource.connection.use(block)

    fun <T> transaction(block: (Connection) -> T): T = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            block(connection).also { connection.commit() }
        } catch (error: Throwable) {
            connection.rollback()
            throw error
        }
    }
}

object Env {
    val databaseUrl: String get() = value("DATABASE_URL", "jdbc:postgresql://localhost:5432/terminus")
    val bundleDays: Int get() = value("BUNDLE_DAYS", "7").toInt()
    val port: Int get() = value("PORT", "8080").toInt()

    fun value(name: String, fallback: String? = null): String =
        System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: fallback
            ?: error("Missing environment variable $name")
}
