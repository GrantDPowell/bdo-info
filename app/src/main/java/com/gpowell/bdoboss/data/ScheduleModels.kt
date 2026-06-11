package com.gpowell.bdoboss.data

import java.time.DayOfWeek
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object DayOfWeekSerializer : KSerializer<DayOfWeek> {
    override val descriptor = PrimitiveSerialDescriptor("DayOfWeek", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: DayOfWeek) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): DayOfWeek =
        DayOfWeek.valueOf(decoder.decodeString().uppercase())
}

@Serializable
data class SpawnSlot(
    @Serializable(with = DayOfWeekSerializer::class)
    val day: DayOfWeek,
    val time: String,       // "HH:mm" in schedule timezone
    val bosses: List<String>,
)

@Serializable
data class Schedule(
    val region: String,
    val timezone: String,
    val version: Int,
    val bosses: List<String>,
    val slots: List<SpawnSlot>,
)
