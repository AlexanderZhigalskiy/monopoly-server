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
import java.text.SimpleDateFormat
import java.util.*

// Модель транзакции (сохраняется на сервере)
@Serializable
data class Transaction(
    val id: Int,
    val playerId: Int,
    val amount: String,  // "+100", "-50", "=500"
    val description: String,
    val date: String
)

// Модель игрока с историей
@Serializable
data class Player(
    val id: Int,
    val name: String,
    var balance: Int = 0,
    val createdAt: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
)

// Запрос для создания игрока
@Serializable
data class PlayerCreateRequest(
    val name: String
)

// Запрос для операций с деньгами
@Serializable
data class MoneyRequest(val amount: Int)

// Ответ сервера
@Serializable
data class SimpleResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null
)

// Расширенное "хранилище" в оперативной памяти
object Game {
    private val players = mutableListOf<Player>()
    private val transactions = mutableListOf<Transaction>()
    private var nextPlayerId = 1
    private var nextTransactionId = 1
    private val dateFormat = SimpleDateFormat("HH:mm")
    private val fullDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    // Игроки
    fun getAllPlayers(): List<Player> = players.toList()

    fun addPlayer(name: String): Player {
        val newPlayer = Player(id = nextPlayerId++, name = name)
        players.add(newPlayer)

        // Создаем начальную транзакцию
        addTransaction(
            playerId = newPlayer.id,
            amount = "=0",
            description = "Player created"
        )

        return newPlayer
    }

    fun updateBalance(playerId: Int, delta: Int, description: String = ""): Boolean {
        val player = players.find { it.id == playerId }
        return player?.let {
            if (it.balance + delta >= 0) {
                it.balance += delta

                // Добавляем транзакцию
                addTransaction(
                    playerId = playerId,
                    amount = if (delta >= 0) "+$delta" else "$delta",
                    description = description.ifEmpty {
                        if (delta >= 0) "Added money" else "Subtracted money"
                    }
                )

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
                val oldBalance = it.balance
                it.balance = newBalance

                // Добавляем транзакцию
                addTransaction(
                    playerId = playerId,
                    amount = "=$newBalance",
                    description = "Balance updated (was $oldBalance)"
                )

                true
            } else {
                false
            }
        } ?: false
    }

    fun deletePlayer(playerId: Int): Boolean {
        // Удаляем все транзакции игрока
        transactions.removeIf { it.playerId == playerId }
        return players.removeIf { it.id == playerId }
    }

    // Транзакции
    private fun addTransaction(playerId: Int, amount: String, description: String) {
        val transaction = Transaction(
            id = nextTransactionId++,
            playerId = playerId,
            amount = amount,
            description = description,
            date = dateFormat.format(Date())
        )
        transactions.add(transaction)

        // Ограничиваем историю 50 последними транзакциями на игрока
        val playerTransactions = transactions.filter { it.playerId == playerId }
        if (playerTransactions.size > 50) {
            val toRemove = playerTransactions.sortedBy { it.id }.take(playerTransactions.size - 50)
            transactions.removeAll(toRemove.toSet())
        }
    }

    fun getPlayerTransactions(playerId: Int): List<Transaction> {
        return transactions
            .filter { it.playerId == playerId }
            .sortedByDescending { it.id } // Новые сверху
            .take(20) // Ограничиваем для клиента
    }

    fun getAllTransactions(): List<Transaction> {
        return transactions.sortedByDescending { it.id }
    }

    // Получение игрока по ID
    fun getPlayer(playerId: Int): Player? {
        return players.find { it.id == playerId }
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
            allowMethod(io.ktor.http.HttpMethod.Options)
            allowMethod(io.ktor.http.HttpMethod.Get)
            allowMethod(io.ktor.http.HttpMethod.Post)
            allowMethod(io.ktor.http.HttpMethod.Put)
            allowMethod(io.ktor.http.HttpMethod.Delete)
        }

        routing {
            get("/") {
                call.respondText("Monopoly Money Server is running")
            }

            get("/health") {
                call.respond(mapOf(
                    "status" to "OK",
                    "players" to Game.getAllPlayers().size,
                    "transactions" to Game.getAllTransactions().size
                ))
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

                        if (name.length > 50) {
                            call.respond(SimpleResponse(false, error = "Player name too long"))
                            return@post
                        }

                        val newPlayer = Game.addPlayer(name)
                        call.respond(newPlayer)
                    } catch (e: Exception) {
                        call.respond(SimpleResponse(false, error = "Error creating player: ${e.message}"))
                    }
                }

                // Получить историю транзакций игрока
                get("/{id}/history") {
                    try {
                        val playerId = call.parameters["id"]?.toIntOrNull()
                        if (playerId == null) {
                            call.respond(SimpleResponse(false, error = "Invalid player ID"))
                            return@get
                        }

                        val transactions = Game.getPlayerTransactions(playerId)
                        call.respond(transactions)
                    } catch (e: Exception) {
                        call.respond(SimpleResponse(false, error = "Error getting history: ${e.message}"))
                    }
                }

                post("/{id}/add") {
                    try {
                        val playerId = call.parameters["id"]?.toIntOrNull()
                        val request = call.receive<MoneyRequest>()

                        if (playerId == null || request.amount <= 0) {
                            call.respond(SimpleResponse(false, error = "Invalid request: amount must be positive"))
                            return@post
                        }

                        val success = Game.updateBalance(playerId, request.amount, "Added money")
                        call.respond(SimpleResponse(success,
                            if (success) "Money added successfully" else "Player not found"))
                    } catch (e: Exception) {
                        call.respond(SimpleResponse(false, error = "Error adding money: ${e.message}"))
                    }
                }

                post("/{id}/subtract") {
                    try {
                        val playerId = call.parameters["id"]?.toIntOrNull()
                        val request = call.receive<MoneyRequest>()

                        if (playerId == null || request.amount <= 0) {
                            call.respond(SimpleResponse(false, error = "Invalid request: amount must be positive"))
                            return@post
                        }

                        val success = Game.updateBalance(playerId, -request.amount, "Subtracted money")
                        call.respond(SimpleResponse(success,
                            if (success) "Money subtracted successfully" else "Player not found or insufficient funds"))
                    } catch (e: Exception) {
                        call.respond(SimpleResponse(false, error = "Error subtracting money: ${e.message}"))
                    }
                }

                put("/{id}/balance") {
                    try {
                        val playerId = call.parameters["id"]?.toIntOrNull()
                        val request = call.receive<MoneyRequest>()

                        if (playerId == null || request.amount < 0) {
                            call.respond(SimpleResponse(false, error = "Invalid request: balance cannot be negative"))
                            return@put
                        }

                        val success = Game.setBalance(playerId, request.amount)
                        call.respond(SimpleResponse(success,
                            if (success) "Balance updated successfully" else "Player not found"))
                    } catch (e: Exception) {
                        call.respond(SimpleResponse(false, error = "Error updating balance: ${e.message}"))
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
                            if (success) "Player deleted successfully" else "Player not found"))
                    } catch (e: Exception) {
                        call.respond(SimpleResponse(false, error = "Error deleting player: ${e.message}"))
                    }
                }
            }

            // Получить все транзакции (для администрирования)
            get("/transactions") {
                try {
                    val allTransactions = Game.getAllTransactions()
                    call.respond(allTransactions)
                } catch (e: Exception) {
                    call.respond(SimpleResponse(false, error = "Error getting transactions: ${e.message}"))
                }
            }
        }
    }.start(wait = true)
}