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
import java.util.*

@Serializable
data class Player(
    val id: Int? = null,
    val name: String,
    var balance: Int = 1500
)

// ПРОСТОЙ РЕПОЗИТОРИЙ В ПАМЯТИ (для теста)
object PlayerRepository {
    private val players = mutableListOf<Player>()
    private var nextId = 1

    fun getAllPlayers(): List<Player> = players.toList()

    fun createPlayer(name: String): Player {
        val player = Player(id = nextId++, name = name, balance = 1500)
        players.add(player)
        return player
    }

    fun addMoney(playerId: Int, amount: Int): Boolean {
        val player = players.find { it.id == playerId }
        player?.let {
            it.balance += amount
            return true
        }
        return false
    }

    fun subtractMoney(playerId: Int, amount: Int): Boolean {
        val player = players.find { it.id == playerId }
        player?.let {
            if (it.balance >= amount) {
                it.balance -= amount
                return true
            }
        }
        return false
    }

    fun deletePlayer(playerId: Int): Boolean {
        return players.removeIf { it.id == playerId }
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
        })
    }

    install(CORS) {
        anyHost()
        allowHeader("Content-Type")
    }

    routing {
        get("/") {
            call.respondText("Monopoly Money API v1.0 (In-Memory Database)")
        }

        get("/health") {
            call.respond(mapOf(
                "status" to "OK",
                "players" to PlayerRepository.getAllPlayers().size,
                "database" to "in-memory"
            ))
        }

        route("/players") {
            get {
                val players = PlayerRepository.getAllPlayers()
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

                    val player = PlayerRepository.createPlayer(name)
                    call.respond(player)
                } catch (e: Exception) {
                    call.respond(mapOf("error" to "Invalid request: ${e.message}"))
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

                    val success = PlayerRepository.addMoney(id, amount)
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

                    val success = PlayerRepository.subtractMoney(id, amount)
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

                    val success = PlayerRepository.deletePlayer(id)
                    call.respond(mapOf("success" to success))
                } catch (e: Exception) {
                    call.respond(mapOf("error" to e.message.toString()))
                }
            }
        }
    }
}