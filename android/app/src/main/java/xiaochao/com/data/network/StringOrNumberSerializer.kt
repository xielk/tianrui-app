package xiaochao.com.data.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

object StringOrNumberSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("StringOrNumberSerializer", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val input = decoder as? JsonDecoder ?: return decoder.decodeString()
        val element = input.decodeJsonElement()
        return (element as? JsonPrimitive)?.content ?: ""
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}
