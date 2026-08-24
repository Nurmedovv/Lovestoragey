package com.lovestory.app.domain.repository

// операции над парой: привязка, сообщения, FCM-токены
interface CoupleRepository {
    suspend fun createCouple(userUid: String, userName: String): String
    suspend fun joinCouple(coupleId: String, userUid: String, userName: String): Boolean
    suspend fun joinCoupleByCode(code: String, userUid: String, userName: String): Boolean
    suspend fun sendMessage(text: String): Boolean

    suspend fun getOtherPartnerName(): String?
    suspend fun getOtherPartnerUid(): String?

    fun startListening(onMessage: (message: String, senderUid: String, timestamp: Long) -> Unit)
    fun stopListening()

    fun refreshFcmToken()

    // обновляет своё имя в документе пары (без ожидания результата)
    fun updateMyName(newName: String)

    // последнее послание текущего пользователя: из Firestore или локального кэша
    suspend fun getMyLastSentMessage(): String?

    suspend fun unpair(): Boolean
}
