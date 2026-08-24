package com.lovestory.app.domain.usecase

import com.lovestory.app.domain.repository.CoupleRepository

// отвязка: чистка документа пары и локальной сессии
class UnpairUseCase(private val repository: CoupleRepository) {
    suspend operator fun invoke(): Boolean = repository.unpair()
}
