package com.lovestory.app.data.couple

import android.content.Context
import android.content.SharedPreferences
import com.lovestory.app.domain.repository.AppPrefs
import com.lovestory.app.domain.repository.CoupleSessionStore

// реализация поверх SharedPreferences "AppSettings"
// ключи и поведение сохранены 1:1 с прежним CoupleManager
class CoupleSessionStoreImpl(context: Context) : CoupleSessionStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isPaired(): Boolean = getCoupleId() != null

    override fun getCoupleId(): String? = prefs.getString(KEY_COUPLE_ID, null)

    override fun getMyUid(): String? = prefs.getString(KEY_PARTNER_UID, null)

    override fun saveIdentity(coupleId: String, myUid: String) {
        prefs.edit()
            .putString(KEY_COUPLE_ID, coupleId)
            .putString(KEY_PARTNER_UID, myUid)
            .apply()
    }

    override fun saveCreatorUid(uid: String) {
        prefs.edit().putString(KEY_CREATOR_UID, uid).apply()
    }

    override fun savePartnerName(name: String) {
        prefs.edit().putString(KEY_PARTNER_NAME, name).apply()
    }

    override fun getLastSentMessage(): String? = prefs.getString(KEY_LAST_SENT_MESSAGE, null)

    override fun saveLastSentMessage(text: String) {
        prefs.edit().putString(KEY_LAST_SENT_MESSAGE, text).apply()
    }

    override fun getLastPartnerMessage(): String? = prefs.getString(KEY_LAST_PARTNER_MESSAGE, null)

    override fun saveLastPartnerMessage(text: String) {
        prefs.edit().putString(KEY_LAST_PARTNER_MESSAGE, text).apply()
    }

    override fun getLastPartnerMessageTimestamp(): Long =
        prefs.getLong(KEY_LAST_PARTNER_MESSAGE_TIMESTAMP, 0L)

    override fun saveLastPartnerMessageTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_PARTNER_MESSAGE_TIMESTAMP, timestamp).apply()
    }

    override fun getFcmToken(): String? = prefs.getString(KEY_FCM_TOKEN, null)

    override fun setFcmToken(token: String) {
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    override fun clear() {
        prefs.edit()
            .remove(KEY_COUPLE_ID)
            .remove(KEY_PARTNER_UID)
            .remove(KEY_CREATOR_UID)
            .remove(KEY_LAST_PARTNER_MESSAGE)
            .remove(KEY_LAST_PARTNER_MESSAGE_TIMESTAMP)
            .remove(KEY_LAST_SENT_MESSAGE)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = AppPrefs.PREFS_NAME
        const val KEY_COUPLE_ID = "couple_id"
        const val KEY_PARTNER_UID = "partner_uid"
        const val KEY_PARTNER_NAME = "partner_name"
        const val KEY_CREATOR_UID = "creator_uid"
        const val KEY_LAST_PARTNER_MESSAGE = "last_partner_message"
        const val KEY_LAST_PARTNER_MESSAGE_TIMESTAMP = "last_partner_message_timestamp"
        const val KEY_LAST_SENT_MESSAGE = "last_sent_message"
        const val KEY_FCM_TOKEN = AppPrefs.KEY_FCM_TOKEN
    }
}
