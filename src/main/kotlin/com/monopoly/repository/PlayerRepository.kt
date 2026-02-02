package com.monopoly

import com.monopoly.model.Player
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PlayerRepository {
    private val players = mutableListOf<Player>()
    private val mutex = Mutex()
    private var nextId = 1

    suspend fun getAllPlayers(): List<Player> = mutex.withLock {
        players.toList()
    }

    suspend fun getPlayer(id: Int): Player? = mutex.withLock {
        players.find { it.id == id }
    }

    suspend fun createPlayer(player: Player): Player = mutex.withLock {
        val newPlayer = player.copy(id = nextId++)
        players.add(newPlayer)
        newPlayer
    }

    suspend fun updatePlayer(id: Int, player: Player): Boolean = mutex.withLock {
        val index = players.indexOfFirst { it.id == id }
        if (index != -1) {
            players[index] = player.copy(id = id)
            true
        } else {
            false
        }
    }

    suspend fun deletePlayer(id: Int): Boolean = mutex.withLock {
        val index = players.indexOfFirst { it.id == id }
        if (index != -1) {
            players.removeAt(index)
            true
        } else {
            false
        }
    }

    suspend fun addMoney(id: Int, amount: Int): Boolean = mutex.withLock {
        val index = players.indexOfFirst { it.id == id }
        if (index != -1) {
            val player = players[index]
            players[index] = player.copy(balance = player.balance + amount)
            true
        } else {
            false
        }
    }

    suspend fun subtractMoney(id: Int, amount: Int): Boolean = mutex.withLock {
        val index = players.indexOfFirst { it.id == id }
        if (index != -1) {
            val player = players[index]
            if (player.balance >= amount) {
                players[index] = player.copy(balance = player.balance - amount)
                true
            } else {
                false
            }
        } else {
            false
        }
    }
}