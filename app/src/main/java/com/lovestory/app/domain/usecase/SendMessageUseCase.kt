package com.lovestory.app.domain.usecase

import com.lovestory.app.domain.repository.CoupleRepository

// отправка послания партнёру; лимит длины живёт здесь
class SendMessageUseCase(private val repository: CoupleRepository) {
    suspend operator fun invoke(text: String): Boolean {
        if (text.length > MAX_MESSAGE_LENGTH) return false
        return repository.sendMessage(text)
    }

    companion object {
        const val MAX_MESSAGE_LENGTH = 20
    }
}
