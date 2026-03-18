package com.browntowndev.pocketcrew.core.data.local

import androidx.room.TypeConverter
import com.browntowndev.pocketcrew.domain.model.chat.Role

/**
 * Type converters for Room to convert Role enum to/from String.
 */
class RoleConverters {
    @TypeConverter
    fun fromRole(role: Role): String = role.apiValue

    @TypeConverter
    fun toRole(value: String): Role = Role.fromApiValue(value)
}
