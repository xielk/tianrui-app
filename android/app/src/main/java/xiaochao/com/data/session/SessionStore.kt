package xiaochao.com.data.session

interface SessionStore {
    fun getUuid(): String
    fun setUuid(uuid: String)

    fun getToken(): String
    fun setToken(token: String)

    fun getUserInfoJson(): String
    fun setUserInfoJson(json: String)

    fun getLastDeviceKey(): String
    fun setLastDeviceKey(deviceKey: String)

    fun getLastBluetoothAddress(): String
    fun setLastBluetoothAddress(address: String)

    fun isAgreedToTerms(): Boolean
    fun setAgreedToTerms(agreed: Boolean)
}
