package com.monopoly

import com.monopoly.model.Player
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.cors.routing.*
import kotlinx.serialization.json.Json

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

    // Настройка CORS для мобильного приложения
    install(CORS) {
        anyHost()
        allowHeader("Content-Type")
    }

    // Инициализируем "базу данных" в памяти
    val playerRepository = PlayerRepository()

    routing {
        // Health check для render.com
        get("/health") {
            call.respond(mapOf("status" to "OK"))
        }

        // API для игроков
        route("/players") {
            // GET /players - получить всех игроков
            get {
                val players = playerRepository.getAllPlayers()
                call.respond(players)
            }

            // POST /players - создать игрока
            post {
                try {
                    val player = call.receive<Player>()
                    if (player.name.isBlank()) {
                        call.respond(mapOf("error" to "Player name is required"))
                        return@post
                    }

                    val createdPlayer = playerRepository.createPlayer(player.copy(balance = 1500))
                    call.respond(createdPlayer)
                } catch (e: Exception) {
                    call.respond(mapOf("error" to "Invalid request"))
                }
            }

            // GET /players/{id} - получить игрока по ID
            get("{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(mapOf("error" to "Invalid ID"))
                    return@get
                }

                val player = playerRepository.getPlayer(id)
                if (player != null) {
                    call.respond(player)
                } else {
                    call.respond(mapOf("error" to "Player not found"))
                }
            }

            // PUT /players/{id} - обновить игрока
            put("{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(mapOf("error" to "Invalid ID"))
                    return@put
                }

                try {
                    val player = call.receive<Player>()
                    val success = playerRepository.updatePlayer(id, player)
                    if (success) {
                        call.respond(mapOf("success" to true))
                    } else {
                        call.respond(mapOf("error" to "Player not found"))
                    }
                } catch (e: Exception) {
                    call.respond(mapOf("error" to "Invalid request"))
                }
            }

            // DELETE /players/{id} - удалить игрока
            delete("{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(mapOf("error" to "Invalid ID"))
                    return@delete
                }

                val success = playerRepository.deletePlayer(id)
                if (success) {
                    call.respond(mapOf("success" to true))
                } else {
                    call.respond(mapOf("error" to "Player not found"))
                }
            }

            // POST /players/{id}/add - добавить деньги
            post("{id}/add") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(mapOf("error" to "Invalid ID"))
                    return@post
                }

                try {
                    val request = call.receive<Map<String, Int>>()
                    val amount = request["amount"] ?: 0

                    if (amount <= 0) {
                        call.respond(mapOf("error" to "Amount must be positive"))
                        return@post
                    }

                    val success = playerRepository.addMoney(id, amount)
                    if (success) {
                        call.respond(mapOf("success" to true))
                    } else {
                        call.respond(mapOf("error" to "Player not found"))
                    }
                } catch (e: Exception) {
                    call.respond(mapOf("error" to "Invalid request"))
                }
            }

            // POST /players/{id}/subtract - снять деньги
            post("{id}/subtract") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(mapOf("error" to "Invalid ID"))
                    return@post
                }

                try {
                    val request = call.receive<Map<String, Int>>()
                    val amount = request["amount"] ?: 0

                    if (amount <= 0) {
                        call.respond(mapOf("error" to "Amount must be positive"))
                        return@post
                    }

                    val success = playerRepository.subtractMoney(id, amount)
                    if (success) {
                        call.respond(mapOf("success" to true))
                    } else {
                        call.respond(mapOf("error" to "Insufficient funds or player not found"))
                    }
                } catch (e: Exception) {
                    call.respond(mapOf("error" to "Invalid request"))
                }
            }
        }
    }
}