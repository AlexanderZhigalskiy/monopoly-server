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

@Serializable
data class Player(
    val id: Int,
    val name: String,
    var balance: Int = 0  // Изменено: по умолчанию 0 вместо 1500
)

@Serializable
data class PlayerCreateRequest(
    val name: String
    // Убрал поле balance - баланс всегда 0 при создании
)

@Serializable
data class MoneyRequest(val amount: Int)

@Serializable
data class SimpleResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null
)

// Простейшее "хранилище" в оперативной памяти
object Game {
    private val players = mutableListOf<Player>()
    private var nextId = 1

    fun getAllPlayers(): List<Player> = synchronized(players) { players.toList() }

    fun addPlayer(name: String, initialBalance: Int = 0): Player = synchronized(players) {
        val newPlayer = Player(id = nextId++, name = name, balance = initialBalance)
        players.add(newPlayer)
        newPlayer
    }

    fun updateBalance(playerId: Int, delta: Int): Boolean = synchronized(players) {
        val player = players.find { it.id == playerId }
        player?.let {
            if (it.balance + delta >= 0) {
                it.balance += delta
                true
            } else {
                false
            }
        } ?: false
    }

    fun setBalance(playerId: Int, newBalance: Int): Boolean = synchronized(players) {
        val player = players.find { it.id == playerId }
        player?.let {
            if (newBalance >= 0) {
                it.balance = newBalance
                true
            } else {
                false
            }
        } ?: false
    }

    fun deletePlayer(playerId: Int): Boolean = synchronized(players) {
        players.removeIf { it.id == playerId }
    }

    fun getPlayersCount(): Int = synchronized(players) { players.size }
}

fun main() {
    embeddedServer(Netty, port = (System.getenv("PORT")?.toIntOrNull() ?: 8080), host = "0.0.0.0") {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
        install(CORS) {
            anyHost()
            allowHeader("Content-Type")
        }

        routing {
            get("/") {
                call.respondText("Monopoly Money Server is running. Use /players API.")
            }

            get("/health") {
                call.respond(mapOf<String, Any>(
                    "status" to "OK",
                    "service" to "monopoly",
                    "timestamp" to System.currentTimeMillis(),
                    "version" to "1.0.1"
                ))
            }

            route("/players") {
                get {
                    try {
                        val players = Game.getAllPlayers()
                        call.respond(players)
                    } catch (e: Exception) {
                        call.respond(SimpleResponse(false, null, "Error getting players: ${e.message}"))
                    }
                }

                post {
                    try {
                        val request = call.receive<PlayerCreateRequest>()
                        val name = request.name.trim()

                        if (name.isEmpty()) {
                            call.respond(SimpleResponse(false, null, "Player name cannot be empty"))
                            return@post
                        }

                        val newPlayer = Game.addPlayer(name) // Баланс будет 0 по умолчанию
                        call.respond(newPlayer)

                    } catch (e: Exception) {
                        call.respond(SimpleResponse(false, null, "Error: ${e.localizedMessage}"))
                    }
                }

                post("/{id}/add") {
                    try {
                        val playerId = call.parameters["id"]?.toIntOrNull()
                        val request = call.receive<MoneyRequest>()

                        if (playerId == null || request.amount <= 0) {
                            call.respond(SimpleResponse(false, null, "Invalid request"))
                            return@post
                        }

                        val success = Game.updateBalance(playerId, request.amount)
                        val message = if (success) "Money added" else "Player not found"
                        call.respond(SimpleResponse(success, message, null))

                    } catch (e: Exception) {
                        call.respond(SimpleResponse(false, null, "Error: ${e.localizedMessage}"))
                    }
                }

                post("/{id}/subtract") {
                    try {
                        val playerId = call.parameters["id"]?.toIntOrNull()
                        val request = call.receive<MoneyRequest>()

                        if (playerId == null || request.amount <= 0) {
                            call.respond(SimpleResponse(false, null, "Invalid request"))
                            return@post
                        }

                        val success = Game.updateBalance(playerId, -request.amount)
                        if (success) {
                            call.respond(SimpleResponse(true, "Money subtracted", null))
                        } else {
                            call.respond(SimpleResponse(false, null, "Player not found or insufficient funds"))
                        }

                    } catch (e: Exception) {
                        call.respond(SimpleResponse(false, null, "Error: ${e.localizedMessage}"))
                    }
                }

                put("/{id}/balance") {
                    try {
                        val playerId = call.parameters["id"]?.toIntOrNull()
                        val request = call.receive<MoneyRequest>()

                        if (playerId == null) {
                            call.respond(SimpleResponse(false, null, "Invalid player ID"))
                            return@put
                        }

                        if (request.amount < 0) {
                            call.respond(SimpleResponse(false, null, "Balance cannot be negative"))
                            return@put
                        }

                        val success = Game.setBalance(playerId, request.amount)
                        if (success) {
                            call.respond(SimpleResponse(true, "Balance updated", null))
                        } else {
                            call.respond(SimpleResponse(false, null, "Player not found"))
                        }

                    } catch (e: Exception) {
                        call.respond(SimpleResponse(false, null, "Error: ${e.localizedMessage}"))
                    }
                }

                delete("/{id}") {
                    try {
                        val playerId = call.parameters["id"]?.toIntOrNull()
                        if (playerId == null) {
                            call.respond(SimpleResponse(false, null, "Invalid player ID"))
                            return@delete
                        }

                        val success = Game.deletePlayer(playerId)
                        val message = if (success) "Player deleted" else "Player not found"
                        call.respond(SimpleResponse(success, message, null))

                    } catch (e: Exception) {
                        call.respond(SimpleResponse(false, null, "Error: ${e.localizedMessage}"))
                    }
                }
            }
        }
    }.start(wait = true)
}