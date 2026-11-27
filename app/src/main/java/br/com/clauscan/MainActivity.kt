package br.com.clauscan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var lastCodeText: TextView
    private lateinit var scanButton: Button
    private lateinit var connectButton: Button

    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var scanner: BarcodeScanner
    private val mainHandler = Handler(Looper.getMainLooper())
    private val httpClient = OkHttpClient()

    private var connected = false
    private var scanMode = false
    private var serverUrl: String? = null
    private var token: String? = null
    private var lastCode = ""
    private var lastCodeAt = 0L
    private var lastQr = ""
    private var lastQrAt = 0L

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else statusText.text = "Permissão da câmera necessária"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_ALL_FORMATS
            ).build()
        scanner = BarcodeScanning.getClient(options)

        buildUi()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun buildUi() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 24, 24, 18)
            setBackgroundColor(0xFFF2F7FB.toInt())
        }
        val title = TextView(this).apply {
            text = "ClauScan"
            textSize = 28f
            setTextColor(0xFF123B62.toInt())
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Scanner wireless de códigos de barras"
            textSize = 14f
            setTextColor(0xFF54718A.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 14)
        }
        statusText = TextView(this).apply {
            text = "Leia o QR Code exibido no Windows"
            textSize = 16f
            setTextColor(0xFF007FA8.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 12)
        }
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        connectButton = Button(this).apply {
            text = "1. Conectar pelo QR Code"
            setOnClickListener {
                scanMode = false
                statusText.text = "Aponte para o QR Code do ClauScan no Windows"
            }
        }
        scanButton = Button(this).apply {
            text = "2. Iniciar leitura de produtos"
            isEnabled = false
            setOnClickListener {
                if (!connected) {
                    Toast.makeText(this@MainActivity, "Conecte ao Windows primeiro", Toast.LENGTH_SHORT).show()
                } else {
                    scanMode = true
                    statusText.text = "Scanner ativo — leia os códigos de barras"
                }
            }
        }
        lastCodeText = TextView(this).apply {
            text = "Último código: —"
            textSize = 18f
            setTextColor(0xFF18324B.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 0)
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(statusText)
        layout.addView(previewView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        layout.addView(connectButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        layout.addView(scanButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        layout.addView(lastCodeText)
        setContentView(layout)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(cameraExecutor) { proxy ->
                val mediaImage = proxy.image
                if (mediaImage == null) {
                    proxy.close()
                    return@setAnalyzer
                }
                val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes -> handleBarcodes(barcodes) }
                    .addOnFailureListener { /* ignore single-frame recognition errors */ }
                    .addOnCompleteListener { proxy.close() }
            }
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                )
            } catch (e: Exception) {
                statusText.text = "Não foi possível iniciar a câmera"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleBarcodes(barcodes: List<Barcode>) {
        for (barcode in barcodes) {
            val value = barcode.rawValue ?: continue
            if (!scanMode && barcode.format == Barcode.FORMAT_QR_CODE) {
                val now = System.currentTimeMillis()
                if (value == lastQr && now - lastQrAt < 1800) continue
                lastQr = value
                lastQrAt = now
                connectFromQr(value)
                return
            }
            if (scanMode && barcode.format != Barcode.FORMAT_QR_CODE) {
                val now = System.currentTimeMillis()
                if (value == lastCode && now - lastCodeAt < 900) continue
                lastCode = value
                lastCodeAt = now
                sendBarcode(value)
                return
            }
        }
    }

    private fun connectFromQr(raw: String) {
        try {
            val qr = JSONObject(raw)
            if (qr.optString("app") != "ClauScan") {
                statusText.text = "Este QR Code não é do ClauScan"
                return
            }
            val url = qr.getString("url")
            val key = qr.getString("token")
            serverUrl = url
            token = key
            statusText.text = "Conectando ao Windows..."
            val request = Request.Builder().url(url.replace("ws://", "http://")).build()
            // OkHttp WebSocket uses the original ws:// address.
            val wsRequest = Request.Builder().url(url).build()
            socket = httpClient.newWebSocket(wsRequest, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    val hello = JSONObject()
                        .put("type", "hello")
                        .put("token", key)
                        .put("device", android.os.Build.MODEL ?: "Android")
                    webSocket.send(hello.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val response = JSONObject(text)
                        if (response.optString("type") == "ready") {
                            mainHandler.post {
                                connected = true
                                scanMode = false
                                statusText.text = "Conectado ao Windows! Agora inicie a leitura."
                                scanButton.isEnabled = true
                                connectButton.text = "Conectado — ler QR novamente"
                            }
                        }
                    } catch (_: Exception) {}
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                    mainHandler.post {
                        connected = false
                        scanButton.isEnabled = false
                        statusText.text = "Falha de conexão. Verifique Wi-Fi e Firewall."
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    mainHandler.post {
                        connected = false
                        scanButton.isEnabled = false
                        statusText.text = "Conexão encerrada"
                    }
                }
            })
        } catch (e: Exception) {
            statusText.text = "QR Code inválido. Leia o QR Code do ClauScan Windows."
        }
    }

    private var socket: WebSocket? = null

    private fun sendBarcode(code: String) {
        val message = JSONObject().put("type", "barcode").put("code", code).toString()
        val sent = socket?.send(message) ?: false
        mainHandler.post {
            if (sent) {
                lastCodeText.text = "Último código: $code"
                statusText.text = "Enviado ao Windows"
            } else {
                statusText.text = "Sem conexão. Reconecte pelo QR Code."
                connected = false
                scanButton.isEnabled = false
            }
        }
    }

    override fun onDestroy() {
        socket?.close(1000, "App encerrado")
        cameraProvider?.unbindAll()
        scanner.close()
        cameraExecutor.shutdown()
        httpClient.dispatcher.executorService.shutdown()
        super.onDestroy()
    }
}
