package com.lovestory.app.data.couple

import com.lovestory.app.domain.usecase.SendMessageUseCase

// контракт документа пары в Firestore и лимитов сообщений
object CoupleContract {
    const val COLLECTION_COUPLES = "couples"
    const val FIELD_PARTNER1 = "partner1"
    const val FIELD_PARTNER2 = "partner2"
    const val FIELD_MESSAGE_TEXT = "message_text"
    const val FIELD_MESSAGE_SENDER = "message_sender"
    const val FIELD_MESSAGE_TIMESTAMP = "message_timestamp"
    const val FIELD_FCM_TOKENS = "fcm_tokens"
    const val FIELD_NAMES = "names"
    const val MAX_MESSAGE_LENGTH = SendMessageUseCase.MAX_MESSAGE_LENGTH
}
