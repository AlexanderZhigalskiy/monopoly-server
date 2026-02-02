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
    var balance: Int = 1500
)

@Serializable
data class MoneyRequest(val amount: Int)

@Serializable
data class SimpleResponse(val success: Boolean, val message: String? = null)

// Простейшее "хранилище" в оперативной памяти
object Game {
    private val players = mutableListOf<Player>()
    private var nextId = 1

    fun getAllPlayers(): List<Player> = synchronized(players) { players.toList() }

    fun addPlayer(name: String): Player = synchronized(players) {
        val newPlayer = Player(id = nextId++, name = name)
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
                // Упрощенный и надежный health check
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
                        call.respond(SimpleResponse(false, "Error getting players: ${e.message}"))
                    }
                }

                post {
                    try {
                        val request = call.receive<Map<String, String>>()
                        val name = request["name"]?.trim()

                        if (name.isNullOrEmpty()) {
                            call.respond(SimpleResponse(false, "Player name cannot be empty"))
                            return@post
                        }

                        val newPlayer = Game.addPlayer(name)
                        call.respond(newPlayer)

                    } catch (e: Exception) {
                        call.respond(SimpleResponse(false, "Error: ${e.localizedMessage}"))
                    }
                }

                post("/{id}/add") {
                    try {
                        val playerId = call.parameters["id"]?.toIntOrNull()
                        val request = call.receive<MoneyRequest>()

                        if (playerId == null || request.amount <= 0) {
                            call.respond(SimpleResponse(false, "Invalid request"))
                            return@post
                        }

                        val success = Game.updateBalance(playerId, request.amount)
                        val message = if (success) "Money added" else "Player not found"
                        call.respond(SimpleResponse(success, message))

                    } catch (e: Exception) {
                        call.respond(SimpleResponse(false, "Error: ${e.localizedMessage}"))
                    }
                }

                post("/{id}/subtract") {
                    try {
                        val playerId = call.parameters["id"]?.toIntOrNull()
                        val request = call.receive<MoneyRequest>()

                        if (playerId == null || request.amount <= 0) {
                            call.respond(SimpleResponse(false, "Invalid request"))
                            return@post
                        }

                        val success = Game.updateBalance(playerId, -request.amount)
                        val message = if (success) "Money subtracted" else "Player not found or insufficient funds"
                        call.respond(SimpleResponse(success, message))

                    } catch (e: Exception) {
                        call.respond(SimpleResponse(false, "Error: ${e.localizedMessage}"))
                    }
                }

                delete("/{id}") {
                    try {
                        val playerId = call.parameters["id"]?.toIntOrNull()
                        if (playerId == null) {
                            call.respond(SimpleResponse(false, "Invalid player ID"))
                            return@delete
                        }

                        val success = Game.deletePlayer(playerId)
                        val message = if (success) "Player deleted" else "Player not found"
                        call.respond(SimpleResponse(success, message))

                    } catch (e: Exception) {
                        call.respond(SimpleResponse(false, "Error: ${e.localizedMessage}"))
                    }
                }
            }
        }
    }.start(wait = true)
}