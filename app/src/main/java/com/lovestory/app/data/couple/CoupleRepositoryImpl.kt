package com.lovestory.app.data.couple

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.lovestory.app.domain.repository.CoupleRepository
import com.lovestory.app.domain.repository.CoupleSessionStore
import kotlinx.coroutines.tasks.await

// Firestore-реализация операций над парой.
// Логика перенесена из прежнего CoupleManager без изменений поведения.
class CoupleRepositoryImpl(context: Context) : CoupleRepository {

    private val db = FirebaseFirestore.getInstance()
    private val store: CoupleSessionStore = CoupleSessionStoreImpl(context.applicationContext)
    private var messageListener: ListenerRegistration? = null

    override suspend fun createCouple(userUid: String, userName: String): String {
        val coupleData = hashMapOf(
            CoupleContract.FIELD_PARTNER1 to userUid,
            CoupleContract.FIELD_PARTNER2 to "",
            CoupleContract.FIELD_MESSAGE_TEXT to "",
            CoupleContract.FIELD_MESSAGE_SENDER to "",
            CoupleContract.FIELD_MESSAGE_TIMESTAMP to 0L,
            CoupleContract.FIELD_FCM_TOKENS to mapOf(userUid to store.getFcmToken().orEmpty()),
            CoupleContract.FIELD_NAMES to mapOf(userUid to userName)
        )
        val docRef = db.collection(CoupleContract.COLLECTION_COUPLES).add(coupleData).await()
        val coupleId = docRef.id
        store.saveIdentity(coupleId, userUid)
        store.savePartnerName(userName)
        store.saveCreatorUid(userUid)
        Log.d(TAG, "Пара создана: $coupleId")
        return coupleId
    }

    override suspend fun joinCouple(coupleId: String, userUid: String, userName: String): Boolean {
        val docRef = db.collection(CoupleContract.COLLECTION_COUPLES).document(coupleId)
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) {
            Log.w(TAG, "Пара не найдена: $coupleId")
            return false
        }
        val partner2 = snapshot.getString(CoupleContract.FIELD_PARTNER2).orEmpty()
        if (partner2.isNotEmpty()) {
            Log.w(TAG, "Пара уже занята: $coupleId")
            return false
        }
        val fcmToken = store.getFcmToken().orEmpty()
        val updateData = hashMapOf<String, Any>(
            CoupleContract.FIELD_PARTNER2 to userUid,
            "${CoupleContract.FIELD_FCM_TOKENS}.$userUid" to fcmToken,
            "${CoupleContract.FIELD_NAMES}.$userUid" to userName
        )
        docRef.update(updateData).await()
        store.saveIdentity(coupleId, userUid)
        store.savePartnerName(userName)
        Log.d(TAG, "Присоединились к паре: $coupleId")
        return true
    }

    override suspend fun joinCoupleByCode(code: String, userUid: String, userName: String): Boolean {
        // ищем пару с пустым partner2 (новая пара)
        val queryEmpty = db.collection(CoupleContract.COLLECTION_COUPLES)
            .whereEqualTo(CoupleContract.FIELD_PARTNER2, "")
            .get()
            .await()
        for (doc in queryEmpty.documents) {
            if (doc.id.lowercase().startsWith(code.lowercase())) {
                val success = joinCouple(doc.id, userUid, userName)
                if (success) return true
            }
        }
        // ищем пару, где пользователь уже был partner1 (повторное присоединение)
        val queryMyCouple = db.collection(CoupleContract.COLLECTION_COUPLES)
            .whereEqualTo(CoupleContract.FIELD_PARTNER1, userUid)
            .get()
            .await()
        for (doc in queryMyCouple.documents) {
            if (doc.id.lowercase().startsWith(code.lowercase())) {
                store.saveIdentity(doc.id, userUid)
                store.savePartnerName(userName)
                Log.d(TAG, "Повторное присоединение к своей паре: ${doc.id}")
                return true
            }
        }
        // ищем пару, где пользователь уже был partner2
        val queryAsPartner2 = db.collection(CoupleContract.COLLECTION_COUPLES)
            .whereEqualTo(CoupleContract.FIELD_PARTNER2, userUid)
            .get()
            .await()
        for (doc in queryAsPartner2.documents) {
            if (doc.id.lowercase().startsWith(code.lowercase())) {
                store.saveIdentity(doc.id, userUid)
                store.savePartnerName(userName)
                Log.d(TAG, "Повторное присоединение (partner2): ${doc.id}")
                return true
            }
        }
        Log.w(TAG, "Пара с кодом не найдена: $code")
        return false
    }

    override suspend fun sendMessage(text: String): Boolean {
        val coupleId = store.getCoupleId() ?: return false
        val senderUid = store.getMyUid() ?: return false
        if (text.length > CoupleContract.MAX_MESSAGE_LENGTH) {
            Log.w(TAG, "Сообщение слишком длинное: ${text.length}")
            return false
        }
        val messageData = hashMapOf<String, Any>(
            CoupleContract.FIELD_MESSAGE_TEXT to text,
            CoupleContract.FIELD_MESSAGE_SENDER to senderUid,
            CoupleContract.FIELD_MESSAGE_TIMESTAMP to System.currentTimeMillis()
        )
        db.collection(CoupleContract.COLLECTION_COUPLES).document(coupleId)
            .update(messageData).await()
        store.saveLastSentMessage(text)
        Log.d(TAG, "Сообщение отправлено: $text")
        return true
    }

    override suspend fun getOtherPartnerName(): String? {
        val otherUid = getOtherPartnerUid() ?: return null
        val coupleId = store.getCoupleId() ?: return null
        val snapshot = db.collection(CoupleContract.COLLECTION_COUPLES).document(coupleId).get().await()
        val namesMap = snapshot.get(CoupleContract.FIELD_NAMES) as? Map<*, *> ?: return null
        return namesMap[otherUid] as? String
    }

    override suspend fun getOtherPartnerUid(): String? {
        val coupleId = store.getCoupleId() ?: return null
        val myUid = store.getMyUid() ?: return null
        val snapshot = db.collection(CoupleContract.COLLECTION_COUPLES).document(coupleId).get().await()
        val partner1 = snapshot.getString(CoupleContract.FIELD_PARTNER1).orEmpty()
        val partner2 = snapshot.getString(CoupleContract.FIELD_PARTNER2).orEmpty()
        return when (myUid) {
            partner1 -> partner2.ifEmpty { null }
            partner2 -> partner1
            else -> null
        }
    }

    override fun startListening(onMessage: (message: String, senderUid: String, timestamp: Long) -> Unit) {
        val coupleId = store.getCoupleId() ?: return
        messageListener?.remove()
        messageListener = db.collection(CoupleContract.COLLECTION_COUPLES).document(coupleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Ошибка слушателя сообщений", error)
                    return@addSnapshotListener
                }
                val text = snapshot?.getString(CoupleContract.FIELD_MESSAGE_TEXT).orEmpty()
                val sender = snapshot?.getString(CoupleContract.FIELD_MESSAGE_SENDER).orEmpty()
                val timestamp = snapshot?.getLong(CoupleContract.FIELD_MESSAGE_TIMESTAMP) ?: 0L
                onMessage(text, sender, timestamp)
            }
    }

    override fun stopListening() {
        messageListener?.remove()
        messageListener = null
    }

    override fun updateMyName(newName: String) {
        val coupleId = store.getCoupleId() ?: return
        val myUid = store.getMyUid() ?: return
        try {
            db.collection(CoupleContract.COLLECTION_COUPLES).document(coupleId)
                .update("${CoupleContract.FIELD_NAMES}.$myUid", newName)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обновления имени в Firestore", e)
        }
    }

    override suspend fun getMyLastSentMessage(): String? {
        val coupleId = store.getCoupleId() ?: return null
        val myUid = store.getMyUid() ?: return null
        return try {
            val snapshot = db.collection(CoupleContract.COLLECTION_COUPLES).document(coupleId).get().await()
            val sender = snapshot.getString(CoupleContract.FIELD_MESSAGE_SENDER).orEmpty()
            val text = snapshot.getString(CoupleContract.FIELD_MESSAGE_TEXT).orEmpty()
            if (sender == myUid && text.isNotEmpty()) text
            else store.getLastSentMessage()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки сообщения", e)
            null
        }
    }

    override fun refreshFcmToken() {
        val coupleId = store.getCoupleId() ?: return
        val myUid = store.getMyUid() ?: return
        val token = store.getFcmToken() ?: return
        try {
            db.collection(CoupleContract.COLLECTION_COUPLES).document(coupleId)
                .update("${CoupleContract.FIELD_FCM_TOKENS}.$myUid", token)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обновления FCM-токена", e)
        }
    }

    override suspend fun unpair(): Boolean {
        val coupleId = store.getCoupleId() ?: return false
        val myUid = store.getMyUid() ?: return false
        try {
            val docRef = db.collection(CoupleContract.COLLECTION_COUPLES).document(coupleId)
            val snapshot = docRef.get().await()
            val partner1 = snapshot.getString(CoupleContract.FIELD_PARTNER1).orEmpty()
            val partner2 = snapshot.getString(CoupleContract.FIELD_PARTNER2).orEmpty()

            if (myUid == partner1) {
                if (partner2.isEmpty()) {
                    docRef.delete().await()
                } else {
                    docRef.update(
                        mapOf(
                            CoupleContract.FIELD_PARTNER1 to partner2,
                            CoupleContract.FIELD_PARTNER2 to "",
                            CoupleContract.FIELD_MESSAGE_TEXT to "",
                            CoupleContract.FIELD_MESSAGE_SENDER to "",
                            CoupleContract.FIELD_MESSAGE_TIMESTAMP to 0L
                        )
                    ).await()
                }
            } else {
                docRef.update(
                    mapOf(
                        CoupleContract.FIELD_PARTNER2 to "",
                        CoupleContract.FIELD_MESSAGE_TEXT to "",
                        CoupleContract.FIELD_MESSAGE_SENDER to "",
                        CoupleContract.FIELD_MESSAGE_TIMESTAMP to 0L
                    )
                ).await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отвязки", e)
        }
        stopListening()
        store.clear()
        return true
    }

    private companion object {
        const val TAG = "CoupleRepository"
    }
}
