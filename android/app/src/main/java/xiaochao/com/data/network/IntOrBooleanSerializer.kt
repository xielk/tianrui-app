package xiaochao.com.data.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

object IntOrBooleanSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("IntOrBooleanSerializer", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        val input = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val element = input.decodeJsonElement() as? JsonPrimitive ?: return 0
        return when {
            element.isString -> element.content.toIntOrNull() ?: 0
            element.content.equals("true", ignoreCase = true) -> 1
            element.content.equals("false", ignoreCase = true) -> 0
            else -> element.content.toIntOrNull() ?: 0
        }
    }

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }
}
