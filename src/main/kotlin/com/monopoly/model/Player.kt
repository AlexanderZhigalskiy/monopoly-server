package com.monopoly

// УДАЛИТЕ старый класс и оставьте только это:
data class Player(
    val id: Int? = null,  // Сделайте ID nullable
    val name: String,
    var balance: Int = 0
) {
    fun addMoney(amount: Int) {
        balance += amount
    }

    fun subtractMoney(amount: Int): Boolean {
        return if (balance >= amount) {
            balance -= amount
            true
        } else {
            false
        }
    }
}