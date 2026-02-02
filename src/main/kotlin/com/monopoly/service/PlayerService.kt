package com.monopoly.service

import com.monopoly.model.PlayerDto
import com.monopoly.repository.PlayerRepository

class PlayerService(private val repository: PlayerRepository) {

    suspend fun getAllPlayers(): List<PlayerDto> {
        return repository.getAllPlayers()
    }

    suspend fun getPlayer(id: Int): PlayerDto? {
        return repository.getPlayer(id)
    }

    suspend fun createPlayer(player: PlayerDto): PlayerDto {
        return repository.createPlayer(player)
    }

    suspend fun updatePlayer(id: Int, player: PlayerDto): Boolean {
        return repository.updatePlayer(id, player)
    }

    suspend fun addMoney(id: Int, amount: Int): Boolean {
        require(amount > 0) { "Amount must be positive" }
        return repository.updatePlayerBalance(id, amount)
    }

    suspend fun subtractMoney(id: Int, amount: Int): Boolean {
        require(amount > 0) { "Amount must be positive" }
        return repository.updatePlayerBalance(id, -amount)
    }

    suspend fun deletePlayer(id: Int): Boolean {
        return repository.deletePlayer(id)
    }
}