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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Serializable
data class Player(
    val id: Int,
    val name: String,
    var balance: Int = 1500
)

class PlayerRepository {
    private val players = mutableListOf<Player>()
    private val mutex = Mutex()
    private var nextId = 1

    suspend fun getAllPlayers(): List<Player> = mutex.withLock {
        players.toList()
    }

    suspend fun createPlayer(name: String): Player = mutex.withLock {
        val player = Player(id = nextId++, name = name)
        players.add(player)
        player
    }

    suspend fun addMoney(id: Int, amount: Int): Boolean = mutex.withLock {
        val player = players.find { it.id == id }
        player?.let {
            it.balance += amount
            true
        } ?: false
    }

    suspend fun subtractMoney(id: Int, amount: Int): Boolean = mutex.withLock {
        val player = players.find { it.id == id }
        player?.let {
            if (it.balance >= amount) {
                it.balance -= amount
                true
            } else {
                false
            }
        } ?: false
    }

    suspend fun deletePlayer(id: Int): Boolean = mutex.withLock {
        players.removeIf { it.id == id }
    }
}

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
        })
    }

    install(CORS) {
        anyHost()
    }

    val playerRepository = PlayerRepository()

    routing {
        get("/") {
            call.respondText("Monopoly Money API is running!")
        }

        get("/health") {
            call.respond(mapOf("status" to "OK"))
        }

        route("/players") {
            get {
                val players = playerRepository.getAllPlayers()
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

                    val player = playerRepository.createPlayer(name)
                    call.respond(player)
                } catch (e: Exception) {
                    call.respond(mapOf("error" to "Invalid request"))
                }
            }

            post("/{id}/add") {
                val id = call.parameters["id"]?.toIntOrNull()
                val data = call.receive<Map<String, Int>>()
                val amount = data["amount"] ?: 0

                if (id == null || amount <= 0) {
                    call.respond(mapOf("error" to "Invalid request"))
                    return@post
                }

                val success = playerRepository.addMoney(id, amount)
                call.respond(mapOf("success" to success))
            }

            post("/{id}/subtract") {
                val id = call.parameters["id"]?.toIntOrNull()
                val data = call.receive<Map<String, Int>>()
                val amount = data["amount"] ?: 0

                if (id == null || amount <= 0) {
                    call.respond(mapOf("error" to "Invalid request"))
                    return@post
                }

                val success = playerRepository.subtractMoney(id, amount)
                call.respond(mapOf("success" to success))
            }

            delete("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(mapOf("error" to "Invalid ID"))
                    return@delete
                }

                val success = playerRepository.deletePlayer(id)
                call.respond(mapOf("success" to success))
            }
        }
    }
}