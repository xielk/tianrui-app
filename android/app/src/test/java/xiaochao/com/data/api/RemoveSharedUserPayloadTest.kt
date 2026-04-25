package xiaochao.com.data.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import xiaochao.com.data.network.RemoveSharedUserBody

class RemoveSharedUserPayloadTest {
    private val json = Json { encodeDefaults = false }

    @Test
    fun buildRemoveSharedUserBody_numericMemberId_encodedAsNumber() {
        val body = ApiRepositoryImpl.buildRemoveSharedUserBody(deviceKey = "d-1", memberId = "1001")
        val encoded = json.encodeToString(RemoveSharedUserBody.serializer(), body)

        assertTrue(encoded.contains("\"device_key\":\"d-1\""))
        assertTrue(encoded.contains("\"member_id\":1001"))
    }

    @Test
    fun buildRemoveSharedUserBody_textMemberId_encodedAsString() {
        val body = ApiRepositoryImpl.buildRemoveSharedUserBody(deviceKey = "d-1", memberId = "m-1001")
        val encoded = json.encodeToString(RemoveSharedUserBody.serializer(), body)

        assertTrue(encoded.contains("\"device_key\":\"d-1\""))
        assertTrue(encoded.contains("\"member_id\":\"m-1001\""))
    }
}
