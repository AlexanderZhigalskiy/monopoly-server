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
import java.util.concurrent.atomic.AtomicInteger

@Serializable
data class Player(
    val id: Int? = null,
    val name: String,
    var balance: Int = 1500
)

@Serializable
data class MoneyRequest(val amount: Int)

@Serializable
data class ApiResponse(val success: Boolean, val error: String? = null)

object PlayerRepository {
    private val players = mutableListOf<Player>()
    private val nextId = AtomicInteger(1)
    private val lock = Any()

    fun getAllPlayers(): List<Player> = synchronized(lock) {
        players.toList()
    }

    fun createPlayer(name: String): Player = synchronized(lock) {
        val player = Player(id = nextId.getAndIncrement(), name = name, balance = 1500)
        players.add(player)
        player
    }

    fun addMoney(playerId: Int, amount: Int): Boolean = synchronized(lock) {
        val player = players.find { it.id == playerId }
        player?.let {
            it.balance += amount
            true
        } ?: false
    }

    fun subtractMoney(playerId: Int, amount: Int): Boolean = synchronized(lock) {
        val player = players.find { it.id == playerId }
        player?.let {
            if (it.balance >= amount) {
                it.balance -= amount
                true
            } else {
                false
            }
        } ?: false
    }

    fun deletePlayer(playerId: Int): Boolean = synchronized(lock) {
        players.removeIf { it.id == playerId }
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
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }

    install(CORS) {
        anyHost()
        allowHeader("Content-Type")
        allowMethod(io.ktor.http.HttpMethod.Options)
        allowMethod(io.ktor.http.HttpMethod.Get)
        allowMethod(io.ktor.http.HttpMethod.Post)
        allowMethod(io.ktor.http.HttpMethod.Put)
        allowMethod(io.ktor.http.HttpMethod.Delete)
    }

    routing {
        // Главная страница
        get("/") {
            call.respondText("""
                Monopoly Money API v1.0
                
                Доступные эндпоинты:
                GET  /health    - Проверка работы
                GET  /players   - Получить всех игроков
                POST /players   - Создать нового игрока (тело: {"name": "Имя"})
                POST /players/{id}/add - Добавить деньги (тело: {"amount": 100})
                POST /players/{id}/subtract - Снять деньги (тело: {"amount": 100})
                DELETE /players/{id} - Удалить игрока
            """.trimIndent())
        }

        // Health check (упрощенный)
        get("/health") {
            try {
                val players = PlayerRepository.getAllPlayers()
                call.respond(mapOf(
                    "status" to "OK",
                    "players_count" to players.size,
                    "version" to "1.0.0"
                ))
            } catch (e: Exception) {
                call.respond(mapOf(
                    "status" to "ERROR",
                    "message" to e.message ?: "Unknown error"
                ))
            }
        }

        // API для игроков
        route("/players") {
            // Получить всех игроков
            get {
                try {
                    val players = PlayerRepository.getAllPlayers()
                    call.respond(players)
                } catch (e: Exception) {
                    call.respond(mapOf("error" to "Failed to get players: ${e.message}"))
                }
            }

            // Создать нового игрока
            post {
                try {
                    val request = call.receive<Map<String, Any>>()
                    val name = request["name"]?.toString() ?: ""

                    if (name.isBlank()) {
                        call.respond(mapOf("error" to "Player name is required"))
                        return@post
                    }

                    if (name.length > 100) {
                        call.respond(mapOf("error" to "Player name too long (max 100 chars)"))
                        return@post
                    }

                    val player = PlayerRepository.createPlayer(name)
                    call.respond(player)
                } catch (e: Exception) {
                    call.respond(mapOf("error" to "Failed to create player: ${e.message}"))
                }
            }

            // Добавить деньги игроку
            post("/{id}/add") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull()
                    val request = call.receive<MoneyRequest>()

                    if (id == null) {
                        call.respond(ApiResponse(success = false, error = "Invalid player ID"))
                        return@post
                    }

                    if (request.amount <= 0) {
                        call.respond(ApiResponse(success = false, error = "Amount must be positive"))
                        return@post
                    }

                    val success = PlayerRepository.addMoney(id, request.amount)
                    call.respond(ApiResponse(success = success))
                } catch (e: Exception) {
                    call.respond(ApiResponse(success = false, error = "Failed to add money: ${e.message}"))
                }
            }

            // Снять деньги у игрока
            post("/{id}/subtract") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull()
                    val request = call.receive<MoneyRequest>()

                    if (id == null) {
                        call.respond(ApiResponse(success = false, error = "Invalid player ID"))
                        return@post
                    }

                    if (request.amount <= 0) {
                        call.respond(ApiResponse(success = false, error = "Amount must be positive"))
                        return@post
                    }

                    val success = PlayerRepository.subtractMoney(id, request.amount)
                    call.respond(ApiResponse(success = success))
                } catch (e: Exception) {
                    call.respond(ApiResponse(success = false, error = "Failed to subtract money: ${e.message}"))
                }
            }

            // Удалить игрока
            delete("/{id}") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull()

                    if (id == null) {
                        call.respond(ApiResponse(success = false, error = "Invalid player ID"))
                        return@delete
                    }

                    val success = PlayerRepository.deletePlayer(id)
                    call.respond(ApiResponse(success = success))
                } catch (e: Exception) {
                    call.respond(ApiResponse(success = false, error = "Failed to delete player: ${e.message}"))
                }
            }
        }
    }
}