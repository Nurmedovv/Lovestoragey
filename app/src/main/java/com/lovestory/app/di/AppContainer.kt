package com.lovestory.app.di

import android.content.Context
import com.lovestory.app.AuthManager
import com.lovestory.app.data.backup.ExportManager
import com.lovestory.app.LoveStoryApp
import com.lovestory.app.SecurePreferences
import com.lovestory.app.data.auth.AuthRepositoryImpl
import com.lovestory.app.data.couple.CoupleRepositoryImpl
import com.lovestory.app.data.couple.CoupleSessionStoreImpl
import com.lovestory.app.data.files.FilesRepositoryImpl
import com.lovestory.app.domain.repository.AuthRepository
import com.lovestory.app.domain.repository.BackupRepository
import com.lovestory.app.domain.repository.CoupleRepository
import com.lovestory.app.domain.repository.CoupleSessionStore
import com.lovestory.app.domain.repository.FilesRepository
import com.lovestory.app.domain.repository.LockRepository
import com.lovestory.app.domain.usecase.CreateCoupleUseCase
import com.lovestory.app.domain.usecase.DeleteFileUseCase
import com.lovestory.app.domain.usecase.JoinCoupleByCodeUseCase
import com.lovestory.app.domain.usecase.SendMessageUseCase
import com.lovestory.app.domain.usecase.UnpairUseCase

// ручной контейнер зависимостей: единственное место,
// которое знает, как конструировать менеджеры приложения
class AppContainer(private val appContext: Context) {

    val authManager: AuthManager by lazy { AuthManager(appContext) }

    val coupleSessionStore: CoupleSessionStore by lazy { CoupleSessionStoreImpl(appContext) }

    val coupleRepository: CoupleRepository by lazy { CoupleRepositoryImpl(appContext) }

    val filesRepository: FilesRepository by lazy { FilesRepositoryImpl(appContext) }

    val authRepository: AuthRepository by lazy { AuthRepositoryImpl(authManager) }

    val lockRepository: LockRepository by lazy { SecurePreferences(appContext) }

    val backupRepository: BackupRepository by lazy { ExportManager(appContext) }

    val createCoupleUseCase: CreateCoupleUseCase by lazy { CreateCoupleUseCase(coupleRepository) }

    val joinCoupleByCodeUseCase: JoinCoupleByCodeUseCase by lazy {
        JoinCoupleByCodeUseCase(coupleRepository)
    }

    val unpairUseCase: UnpairUseCase by lazy { UnpairUseCase(coupleRepository) }

    val sendMessageUseCase: SendMessageUseCase by lazy { SendMessageUseCase(coupleRepository) }

    val deleteFileUseCase: DeleteFileUseCase by lazy { DeleteFileUseCase(filesRepository) }
}

// доступ к контейнеру из любого места с Context
val Context.appContainer: AppContainer
    get() = (applicationContext as LoveStoryApp).container
