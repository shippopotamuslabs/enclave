package com.browntowndev.pocketcrew.core.data.local

import com.browntowndev.pocketcrew.core.data.local.dao.message.MessageDao
import com.browntowndev.pocketcrew.core.data.local.dao.message.MessageDaoPersist

fun PocketCrewDatabase.messageDao(): MessageDao {
    val commands = messageCommandDao()
    val syncCommands = messageSyncCommandDao()
    return MessageDao(
        query = messageQueryDao(),
        command = commands,
        syncCommand = syncCommands,
        persist = MessageDaoPersist(database = this, commands, syncCommands),
    )
}
