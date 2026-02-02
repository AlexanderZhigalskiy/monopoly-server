package com.monopoly.api

import com.monopoly.model.PlayerDto
import com.monopoly.service.PlayerService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.playerApi(playerService: PlayerService) {

    route("/players") {

        // GET /players - получить всех игроков
        get {
            try {
                val players = playerService.getAllPlayers()
                call.respond(HttpStatusCode.OK, players)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }

        // POST /players - создать нового игрока
        post {
            try {
                val player = call.receive<PlayerDto>()
                val createdPlayer = playerService.createPlayer(player)
                call.respond(HttpStatusCode.Created, createdPlayer)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        }

        // GET /players/{id} - получить игрока по ID
        get("{id}") {
            try {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))
                    return@get
                }

                val player = playerService.getPlayer(id)
                if (player != null) {
                    call.respond(HttpStatusCode.OK, player)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Player not found"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }

        // PUT /players/{id} - обновить игрока
        put("{id}") {
            try {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))
                    return@put
                }

                val player = call.receive<PlayerDto>()
                val updated = playerService.updatePlayer(id, player)

                if (updated) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Player updated"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Player not found"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        }

        // DELETE /players/{id} - удалить игрока
        delete("{id}") {
            try {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))
                    return@delete
                }

                val deleted = playerService.deletePlayer(id)
                if (deleted) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Player deleted"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Player not found"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }

        // POST /players/{id}/add - добавить деньги
        post("{id}/add") {
            try {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))
                    return@post
                }

                val body = call.receive<Map<String, Int>>()
                val amount = body["amount"] ?: throw IllegalArgumentException("Amount is required")

                if (amount <= 0) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Amount must be positive"))
                    return@post
                }

                val success = playerService.addMoney(id, amount)
                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Money added"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Insufficient funds or player not found"))
                }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }

        // POST /players/{id}/subtract - снять деньги
        post("{id}/subtract") {
            try {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))
                    return@post
                }

                val body = call.receive<Map<String, Int>>()
                val amount = body["amount"] ?: throw IllegalArgumentException("Amount is required")

                if (amount <= 0) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Amount must be positive"))
                    return@post
                }

                val success = playerService.subtractMoney(id, amount)
                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Money subtracted"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Insufficient funds or player not found"))
                }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
    }
}