package xiaochao.com.data.push

import android.content.Context
import android.util.Log
import com.tencent.android.tpush.XGPushBaseReceiver
import com.tencent.android.tpush.XGPushClickedResult
import com.tencent.android.tpush.XGPushRegisterResult
import com.tencent.android.tpush.XGPushShowedResult
import com.tencent.android.tpush.XGPushTextMessage

class TpnsMessageReceiver : XGPushBaseReceiver() {
    override fun onRegisterResult(context: Context, errorCode: Int, message: XGPushRegisterResult) {
        Log.d("TPNS", "receiver register result code=$errorCode token=${message.token}")
    }

    override fun onUnregisterResult(context: Context, errorCode: Int) {
        Log.d("TPNS", "receiver unregister result code=$errorCode")
    }

    override fun onSetTagResult(context: Context, errorCode: Int, tagName: String) {
        Log.d("TPNS", "receiver setTag code=$errorCode tag=$tagName")
    }

    override fun onDeleteTagResult(context: Context, errorCode: Int, tagName: String) {
        Log.d("TPNS", "receiver deleteTag code=$errorCode tag=$tagName")
    }

    override fun onSetAccountResult(context: Context, errorCode: Int, account: String) {
        Log.d("TPNS", "receiver setAccount code=$errorCode account=$account")
    }

    override fun onDeleteAccountResult(context: Context, errorCode: Int, account: String) {
        Log.d("TPNS", "receiver deleteAccount code=$errorCode account=$account")
    }

    override fun onSetAttributeResult(context: Context, errorCode: Int, attribute: String) {
        Log.d("TPNS", "receiver setAttribute code=$errorCode attribute=$attribute")
    }

    override fun onQueryTagsResult(context: Context, errorCode: Int, tagName: String, operationName: String) {
        Log.d("TPNS", "receiver queryTags code=$errorCode tag=$tagName op=$operationName")
    }

    override fun onDeleteAttributeResult(context: Context, errorCode: Int, attribute: String) {
        Log.d("TPNS", "receiver deleteAttribute code=$errorCode attr=$attribute")
    }

    override fun onTextMessage(context: Context, message: XGPushTextMessage) {
        Log.i(
            "TPNS",
            "receiver textMessage content=${message.content} custom=${message.customContent} msgId=${message.msgId}"
        )
        TpnsCommandDispatcher.handleRaw(context, message.content, message.customContent)
    }

    override fun onNotificationClickedResult(context: Context, message: XGPushClickedResult) {
        Log.i("TPNS", "receiver notificationClicked title=${message.title} content=${message.content}")
        TpnsCommandDispatcher.handleRaw(context, message.content, message.customContent)
    }

    override fun onNotificationShowedResult(context: Context, message: XGPushShowedResult) {
        Log.d("TPNS", "receiver notificationShowed title=${message.title}")
    }

    override fun onInMsgReceivedResult(context: Context, message: XGPushTextMessage) {
        Log.i(
            "TPNS",
            "receiver inMsgReceived content=${message.content} custom=${message.customContent} msgId=${message.msgId}"
        )
        TpnsCommandDispatcher.handleRaw(context, message.content, message.customContent)
    }
}
