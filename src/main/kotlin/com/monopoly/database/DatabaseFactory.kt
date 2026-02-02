package com.monopoly.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import com.monopoly.model.Players

object DatabaseFactory {
    fun init() {
        Database.connect(hikari())

        transaction {
            SchemaUtils.create(Players)
        }
    }

    private fun hikari(): HikariDataSource {
        val config = HikariConfig()

        // Для локальной разработки
        val databaseUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/monopoly"
        val databaseUser = System.getenv("DATABASE_USER") ?: "postgres"
        val databasePassword = System.getenv("DATABASE_PASSWORD") ?: "password"

        config.driverClassName = "org.postgresql.Driver"
        config.jdbcUrl = databaseUrl
        config.username = databaseUser
        config.password = databasePassword
        config.maximumPoolSize = 3
        config.isAutoCommit = false
        config.transactionIsolation = "TRANSACTION_REPEATABLE_READ"

        config.validate()

        return HikariDataSource(config)
    }

    suspend fun <T> dbQuery(block: () -> T): T =
        withContext(Dispatchers.IO) {
            transaction { block() }
        }
}