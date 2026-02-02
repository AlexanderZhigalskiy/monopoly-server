package com.monopoly

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.cors.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.sql.*

@Serializable
data class Player(
    val id: Int,
    val name: String,
    var balance: Int = 1500
)

class Database {
    private var connection: Connection? = null

    init {
        connect()
        createTable()
    }

    private fun connect() {
        try {
            // ВАША СТРОКА ПОДКЛЮЧЕНИЯ ИЗ RENDER
            val databaseUrl = System.getenv("DATABASE_URL") ?:
            "postgresql://monopoly_64ir_user:FUK30dgIbnvTORZ5veNriGmIdYTsVb0c@dpg-d60av6juibrs73dcfjdg-a.frankfurt-postgres.render.com/monopoly_64ir"

            // Преобразуем для JDBC
            val jdbcUrl = if (databaseUrl.startsWith("postgresql://")) {
                databaseUrl.replace("postgresql://", "jdbc:postgresql://") + "?ssl=true&sslmode=require"
            } else {
                databaseUrl
            }

            println("🔄 Connecting to: $jdbcUrl")
            Class.forName("org.postgresql.Driver")
            connection = DriverManager.getConnection(jdbcUrl)
            println("✅ Connected to PostgreSQL database")
        } catch (e: Exception) {
            println("❌ Database connection failed: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun createTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS players (
                id SERIAL PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                balance INTEGER DEFAULT 1500
            )
        """

        try {
            connection?.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute(sql)
                    println("✅ Players table created/verified")
                }
            }
        } catch (e: Exception) {
            println("❌ Table creation failed: ${e.message}")
        }
    }

    fun getAllPlayers(): List<Player> {
        val players = mutableListOf<Player>()
        val sql = "SELECT * FROM players ORDER BY id"

        try {
            connection?.use { conn ->
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery(sql)
                    while (rs.next()) {
                        players.add(
                            Player(
                                id = rs.getInt("id"),
                                name = rs.getString("name"),
                                balance = rs.getInt("balance")
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            println("❌ Error getting players: ${e.message}")
        }
        return players
    }

    fun createPlayer(name: String): Player? {
        val sql = "INSERT INTO players (name) VALUES (?) RETURNING id, name, balance"

        return try {
            connection?.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, name)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        Player(
                            id = rs.getInt("id"),
                            name = rs.getString("name"),
                            balance = rs.getInt("balance")
                        )
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            println("❌ Error creating player: ${e.message}")
            null
        }
    }

    fun addMoney(playerId: Int, amount: Int): Boolean {
        val sql = "UPDATE players SET balance = balance + ? WHERE id = ? RETURNING balance"

        return try {
            connection?.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, amount)
                    stmt.setInt(2, playerId)
                    val rs = stmt.executeQuery()
                    rs.next()
                    true
                }
            } ?: false
        } catch (e: Exception) {
            println("❌ Error adding money: ${e.message}")
            false
        }
    }

    fun subtractMoney(playerId: Int, amount: Int): Boolean {
        val checkSql = "SELECT balance FROM players WHERE id = ?"
        val updateSql = "UPDATE players SET balance = balance - ? WHERE id = ? AND balance >= ?"

        return try {
            connection?.use { conn ->
                // Проверяем баланс
                var hasEnough = false
                conn.prepareStatement(checkSql).use { checkStmt ->
                    checkStmt.setInt(1, playerId)
                    val rs = checkStmt.executeQuery()
                    if (rs.next() && rs.getInt("balance") >= amount) {
                        hasEnough = true
                    }
                }

                if (hasEnough) {
                    // Снимаем деньги
                    conn.prepareStatement(updateSql).use { updateStmt ->
                        updateStmt.setInt(1, amount)
                        updateStmt.setInt(2, playerId)
                        updateStmt.setInt(3, amount)
                        updateStmt.executeUpdate() > 0
                    }
                } else {
                    false
                }
            } ?: false
        } catch (e: Exception) {
            println("❌ Error subtracting money: ${e.message}")
            false
        }
    }

    fun deletePlayer(playerId: Int): Boolean {
        val sql = "DELETE FROM players WHERE id = ?"

        return try {
            connection?.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, playerId)
                    stmt.executeUpdate() > 0
                }
            } ?: false
        } catch (e: Exception) {
            println("❌ Error deleting player: ${e.message}")
            false
        }
    }
}

fun main() {
    embeddedServer(Netty, port = getPort(), host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun getPort(): Int {
    return System.getenv("PORT")?.toIntOrNull() ?: 8080
}

fun Application.module() {
    // Настройка JSON сериализации
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
    }

    // Настройка CORS
    install(CORS) {
        anyHost()
        allowHeader("Content-Type")
        allowMethod(io.ktor.http.HttpMethod.Options)
        allowMethod(io.ktor.http.HttpMethod.Get)
        allowMethod(io.ktor.http.HttpMethod.Post)
        allowMethod(io.ktor.http.HttpMethod.Put)
        allowMethod(io.ktor.http.HttpMethod.Delete)
    }

    val database = Database()

    routing {
        get("/") {
            call.respondText("Monopoly Money API v1.0")
        }

        get("/health") {
            val players = database.getAllPlayers()
            call.respond(mapOf(
                "status" to "OK",
                "players_count" to players.size,
                "database" to "connected"
            ))
        }

        route("/players") {
            get {
                val players = database.getAllPlayers()
                call.respond(players)
            }

            post {
                try {
                    val data = call.receive<Map<String, String>>()
                    val name = data["name"] ?: ""

                    if (name.isBlank()) {
                        call.respond(mapOf("error" to "Name is required"))
                        return@post
                    }

                    val player = database.createPlayer(name)
                    if (player != null) {
                        call.respond(player)
                    } else {
                        call.respond(mapOf("error" to "Failed to create player"))
                    }
                } catch (e: Exception) {
                    call.respond(mapOf("error" to e.message.toString()))
                }
            }

            post("/{id}/add") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull()
                    val data = call.receive<Map<String, Int>>()
                    val amount = data["amount"] ?: 0

                    if (id == null || amount <= 0) {
                        call.respond(mapOf("error" to "Invalid ID or amount"))
                        return@post
                    }

                    val success = database.addMoney(id, amount)
                    call.respond(mapOf("success" to success))
                } catch (e: Exception) {
                    call.respond(mapOf("error" to e.message.toString()))
                }
            }

            post("/{id}/subtract") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull()
                    val data = call.receive<Map<String, Int>>()
                    val amount = data["amount"] ?: 0

                    if (id == null || amount <= 0) {
                        call.respond(mapOf("error" to "Invalid ID or amount"))
                        return@post
                    }

                    val success = database.subtractMoney(id, amount)
                    call.respond(mapOf("success" to success))
                } catch (e: Exception) {
                    call.respond(mapOf("error" to e.message.toString()))
                }
            }

            delete("/{id}") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull()
                    if (id == null) {
                        call.respond(mapOf("error" to "Invalid ID"))
                        return@delete
                    }

                    val success = database.deletePlayer(id)
                    call.respond(mapOf("success" to success))
                } catch (e: Exception) {
                    call.respond(mapOf("error" to e.message.toString()))
                }
            }
        }
    }
}