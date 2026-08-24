package com.lovestory.app.domain.repository

// локальное хранилище состояния привязки (SharedPreferences)
interface CoupleSessionStore {
    fun isPaired(): Boolean
    fun getCoupleId(): String?
    fun getMyUid(): String?
    fun saveIdentity(coupleId: String, myUid: String)
    fun saveCreatorUid(uid: String)
    fun savePartnerName(name: String)

    fun getLastSentMessage(): String?
    fun saveLastSentMessage(text: String)

    fun getLastPartnerMessage(): String?
    fun saveLastPartnerMessage(text: String)
    fun getLastPartnerMessageTimestamp(): Long
    fun saveLastPartnerMessageTimestamp(timestamp: Long)

    fun getFcmToken(): String?
    fun setFcmToken(token: String)

    // очищает только идентификационные ключи пары
    fun clear()
}
