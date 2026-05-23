package com.aistock.analysis.net

import kotlinx.serialization.Serializable

@Serializable
data class Envelope<T>(val data: T? = null, val error: String? = null)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val name: String? = null,
    val picture: String? = null,
)

@Serializable
data class GoogleAuthResp(val token: String, val user: UserDto)

@Serializable
data class TickersResp(val tickers: List<String>)

@Serializable
data class TrialInfo(val limit: Int, val used: Int, val remaining: Int)

@Serializable
data class SubscriptionInfo(
    val status: String,
    val expiryTime: String? = null,
)

@Serializable
data class StatusResp(
    val user: UserDto? = null,
    val subscription: SubscriptionInfo? = null,
    val subscriptionActive: Boolean = false,
    val trial: TrialInfo,
    val deviceBlocked: Boolean = false,
)

@Serializable
data class GateInfo(val mode: String, val remaining: Int? = null)

@Serializable
data class MetaEvent(val ticker: String, val gate: GateInfo)

@Serializable
data class DeltaEvent(val text: String)

@Serializable
data class ErrorEvent(val code: String, val message: String? = null)

@Serializable
data class VerifyResp(
    val status: String,
    val expiryTime: String? = null,
    val autoRenewing: Boolean = false,
)
