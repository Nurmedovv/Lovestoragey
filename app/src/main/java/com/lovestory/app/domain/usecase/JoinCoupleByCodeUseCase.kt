package com.lovestory.app.domain.usecase

import com.lovestory.app.domain.repository.CoupleRepository

// вступление в существующую пару по 6-символьному коду
class JoinCoupleByCodeUseCase(private val repository: CoupleRepository) {
    suspend operator fun invoke(code: String, userUid: String, userName: String): Boolean =
        repository.joinCoupleByCode(code, userUid, userName)
}
