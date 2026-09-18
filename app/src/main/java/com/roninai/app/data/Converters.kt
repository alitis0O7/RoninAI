package com.roninai.app.data

import androidx.room.TypeConverter
import com.roninai.app.data.entity.MessageRole
import com.roninai.app.data.entity.MessageType

class Converters {

    @TypeConverter
    fun fromMessageRole(role: MessageRole): String = role.name

    @TypeConverter
    fun toMessageRole(value: String): MessageRole = MessageRole.valueOf(value)

    @TypeConverter
    fun fromMessageType(type: MessageType): String = type.name

    @TypeConverter
    fun toMessageType(value: String): MessageType = MessageType.valueOf(value)
}
