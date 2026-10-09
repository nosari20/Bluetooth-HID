package com.fnosari.bluetoothhid.activities.scan.components

import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/** ML Kit analyzer: reports the first decoded value, then stays quiet until re-enabled. */
class QrAnalyzer(private val onCode: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build()
    )

    @Volatile
    var enabled = true

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (!enabled || media == null) {
            image.close()
            return
        }
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { codes ->
                val value = codes.firstNotNullOfOrNull { it.rawValue }
                if (value != null && enabled) {
                    enabled = false
                    onCode(value)
                }
            }
            .addOnCompleteListener { image.close() }
    }

    fun close() = scanner.close()
}

/** Back camera preview with barcode analysis. [onCode] runs on the main thread. */
@Composable
fun CameraPreview(enabled: Boolean, onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val analyzer = remember { QrAnalyzer { code -> context.mainExecutor.execute { currentOnCode(code) } } }
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(enabled) { analyzer.enabled = enabled }

    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        val executor = Executors.newSingleThreadExecutor()
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
            provider.unbindAll()
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, context.mainExecutor)
        onDispose {
            if (future.isDone) future.get().unbindAll()
            executor.shutdown()
            analyzer.close()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
