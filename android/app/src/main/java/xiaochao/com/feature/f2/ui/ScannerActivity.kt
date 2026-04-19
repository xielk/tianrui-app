package xiaochao.com.feature.f2.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import xiaochao.com.R

class ScannerActivity : Activity() {
    private lateinit var barcodeView: DecoratedBarcodeView
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        barcodeView = findViewById(R.id.barcode_scanner)
        val title = findViewById<TextView>(R.id.scan_title)
        val back = findViewById<ImageView>(R.id.scan_back)

        title.text = "请扫描二维码或车架码"
        back.setOnClickListener { cancelScan() }

        barcodeView.barcodeView.decoderFactory = DefaultDecoderFactory(
            listOf(
                BarcodeFormat.QR_CODE,
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODE_39,
                BarcodeFormat.EAN_13,
            )
        )
        barcodeView.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                if (handled) return
                val text = result?.text?.trim().orEmpty()
                if (text.isEmpty()) return
                handled = true
                val data = Intent().putExtra("scan_result", text)
                setResult(Activity.RESULT_OK, data)
                finish()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        handled = false
        barcodeView.resume()
    }

    override fun onPause() {
        barcodeView.pause()
        super.onPause()
    }

    override fun onBackPressed() {
        cancelScan()
    }

    private fun cancelScan() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}
