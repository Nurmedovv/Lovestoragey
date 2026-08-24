package com.lovestory.app.domain.usecase

import com.lovestory.app.domain.repository.CoupleRepository

// создание новой пары: текущий пользователь становится partner1
class CreateCoupleUseCase(private val repository: CoupleRepository) {
    suspend operator fun invoke(userUid: String, userName: String): String =
        repository.createCouple(userUid, userName)
}
