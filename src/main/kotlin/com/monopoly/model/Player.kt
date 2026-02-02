package com.monopoly.model

import kotlinx.serialization.Serializable

@Serializable
data class Player(
    val id: Int? = null,
    val name: String,
    var balance: Int = 1500
)