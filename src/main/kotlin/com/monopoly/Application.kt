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
    var balance: Int = 0
)

@Serializable
data class PlayerCreateRequest(
    val name: String
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

    fun getAllPlayers(): List<Player> = players.toList()

    fun addPlayer(name: String): Player {
        val newPlayer = Player(id = nextId++, name = name)
        players.add(newPlayer)
        return newPlayer
    }

    fun updateBalance(playerId: Int, delta: Int): Boolean {
        val player = players.find { it.id == playerId }
        return player?.let {
            if (it.balance + delta >= 0) {
                it.balance += delta
                true
            } else {
                false
            }
        } ?: false
    }

    fun setBalance(playerId: Int, newBalance: Int): Boolean {
        val player = players.find { it.id == playerId }
        return player?.let {
            if (newBalance >= 0) {
                it.balance = newBalance
                true
            } else {
                false
            }
        } ?: false
    }

    fun deletePlayer(playerId: Int): Boolean {
        return players.removeIf { it.id == playerId }
    }
}

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
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
                call.respondText("Monopoly Money Server is running")
            }

            get("/health") {
                call.respond(mapOf("status" to "OK"))
            }

            route("/players") {
                get {
                    call.respond(Game.getAllPlayers())
                }

                post {
                    try {
                        val request = call.receive<PlayerCreateRequest>()
                        val name = request.name.trim()

                        if (name.isEmpty()) {
                            call.respond(SimpleResponse(false, error = "Player name cannot be empty"))
                            return@post
                        }

                        val newPlayer = Game.addPlayer(name)
                        call.respond(newPlayer)
                    } catch (e: Exception) {
                        call.respond(SimpleResponse(false, error = e.message))
                    }
                }

                post("/{id}/add") {
                    try {
                        val playerId = call.parameters["id"]?.toIntOrNull()
                        val request = call.receive<MoneyRequest>()

                        if (playerId == null || request.amount <= 0) {
                            call.respond(SimpleResponse(false, error = "Invalid request"))
                            return@post
                        }

                        val success = Game.updateBalance(playerId, request.amount)
                        call.respond(SimpleResponse(success,
                            if (success) "Money added" else "Player not found"))
                    } catch (e: Exception) {
                        call.respond(SimpleResponse(false, error = e.message))
                    }
                }

                post("/{id}/subtract") {
                    try {
                        val playerId = call.parameters["id"]?.toIntOrNull()
                        val request = call.receive<MoneyRequest>()

                        if (playerId == null || request.amount <= 0) {
                            call.respond(SimpleResponse(false, error = "Invalid request"))
                            return@post
                        }

                        val success = Game.updateBalance(playerId, -request.amount)
                        call.respond(SimpleResponse(success,
                            if (success) "Money subtracted" else "Player not found or insufficient funds"))
                    } catch (e: Exception) {
                        call.respond(SimpleResponse(false, error = e.message))
                    }
                }

                put("/{id}/balance") {
                    try {
                        val playerId = call.parameters["id"]?.toIntOrNull()
                        val request = call.receive<MoneyRequest>()

                        if (playerId == null || request.amount < 0) {
                            call.respond(SimpleResponse(false, error = "Invalid request"))
                            return@put
                        }

                        val success = Game.setBalance(playerId, request.amount)
                        call.respond(SimpleResponse(success,
                            if (success) "Balance updated" else "Player not found"))
                    } catch (e: Exception) {
                        call.respond(SimpleResponse(false, error = e.message))
                    }
                }

                delete("/{id}") {
                    try {
                        val playerId = call.parameters["id"]?.toIntOrNull()
                        if (playerId == null) {
                            call.respond(SimpleResponse(false, error = "Invalid player ID"))
                            return@delete
                        }

                        val success = Game.deletePlayer(playerId)
                        call.respond(SimpleResponse(success,
                            if (success) "Player deleted" else "Player not found"))
                    } catch (e: Exception) {
                        call.respond(SimpleResponse(false, error = e.message))
                    }
                }
            }
        }
    }.start(wait = true)
}