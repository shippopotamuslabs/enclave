package com.browntowndev.enclave.core.data.repository.message

import com.browntowndev.enclave.core.data.local.dao.message.MessageWithFiles
import com.browntowndev.enclave.core.data.security.SensitiveEntityFieldEncryptors
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepair
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageEncryptionCoordinator
    @Inject
    constructor(
        private val healer: SensitivePersistenceSelfHealer,
    ) {
        fun decryptMessageWithFiles(
            row: MessageWithFiles,
            readRepairs: MutableList<SensitiveRoomReadRepair>?,
        ): MessageWithFiles =
            row.copy(
                message = SensitiveEntityFieldEncryptors.decrypt(row.message, healer, readRepairs),
                files = row.files.map { SensitiveEntityFieldEncryptors.decrypt(it, healer, readRepairs) },
            )
    }
