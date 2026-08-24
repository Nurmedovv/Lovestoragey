package com.lovestory.app.domain.usecase

import com.lovestory.app.domain.repository.FilesRepository

// единый сценарий удаления: файл с диска + запись из Room,
// раньше эта пара вызовов повторялась в четырёх местах UI
class DeleteFileUseCase(private val filesRepository: FilesRepository) {

    // возвращает результат удаления файла с диска;
    // запись из БД убирается независимо от него (как и раньше)
    suspend operator fun invoke(internalPath: String): Boolean =
        filesRepository.deleteFile(internalPath).also {
            filesRepository.deleteFileFromDb(internalPath)
        }
}
