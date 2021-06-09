package me.fabiankuenzle.colorguide

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

typealias ColorListener = (color: String) -> Unit

private const val ANALYZER_FPS: Double = 1.0

class MainActivity : AppCompatActivity() {
    private var torchEnabled: Boolean = false
    private lateinit var camera: Camera
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request camera permissions
        if (allPermissionsGranted()) {
            cameraExecutor = Executors.newCachedThreadPool()
            startCamera()
            showCrosshair()
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun showCrosshair() {
        val crosshairImageView = findViewById<ImageView>(R.id.crosshair)
        crosshairImageView.setImageResource(R.drawable.ic_outline_crop_din_24)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(viewFinder.createSurfaceProvider())
                    }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            val width = Resources.getSystem().displayMetrics.widthPixels
            // Use a 10th of display with for image analyzer width and height, so 1 pixel are 100 in the real image.
            val size = Size(width/10, width/10)

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(size)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ColorAnalyzer (this.applicationContext) { color ->
                        colorTextView.text = color
                    })
                }

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults:
            IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
                baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "MainActiviy"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    private class ColorAnalyzer(private val context: Context, private val listener: ColorListener) : ImageAnalysis.Analyzer {
        private var lastAnalyzedTimestamp: Long = 0

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        private fun getCenterHSVFromImage(image: ImageProxy): FloatArray {
            val planes = image.planes

            val height = image.height
            val width = image.width

            // Y
            val yArr = planes[0].buffer
            val yArrByteArray = yArr.toByteArray()
            val yPixelStride = planes[0].pixelStride
            val yRowStride = planes[0].rowStride

            // U
            val uArr = planes[1].buffer
            val uArrByteArray = uArr.toByteArray()
            val uPixelStride = planes[1].pixelStride
            val uRowStride = planes[1].rowStride

            // V
            val vArr = planes[2].buffer
            val vArrByteArray = vArr.toByteArray()
            val vPixelStride = planes[2].pixelStride
            val vRowStride = planes[2].rowStride

//            var ySum = 0
//            var uSum = 0
//            var vSum = 0
//            var valueCount = 0
//
//            for (y in height - height/10..height + height/10)
//            {
//                for (x in width - width/10..width + width/10) {
//                    val yValue = yArrByteArray[(y*yRowStride + x * yPixelStride)/2].toInt() and 255
//                    val uValue = (uArrByteArray[(y*uRowStride + x * uPixelStride)/4].toInt() and 255) - 128
//                    val vValue = (vArrByteArray[(y*vRowStride + x * vPixelStride)/4].toInt() and 255) - 128
//
//                    ySum += yValue
//                    uSum += uValue
//                    vSum += vValue
//                    valueCount++
//                }
//            }
//
//            val y = ySum / valueCount.toFloat()
//            val u = uSum / valueCount.toFloat()
//            val v = vSum / valueCount.toFloat()

            val y = yArrByteArray[(height * yRowStride + width * yPixelStride) / 2].toInt() and 255
            val u = (uArrByteArray[(height * uRowStride + width * uPixelStride) / 4].toInt() and 255) - 128
            val v = (vArrByteArray[(height * vRowStride + width * vPixelStride) / 4].toInt() and 255) - 128

            val r:Int = (y + (1.370705 * v)).roundToInt()
            val g:Int = (y - (0.698001 * v) - (0.337633 * u)).roundToInt()
            val b:Int = (y + (1.732446 * u)).roundToInt()

            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV(r, g, b, hsv)

            return hsv
        }

        fun getColorNameFromHsv(hsl: FloatArray): String {
            val colors: MutableMap<String, Int> = Helpers.getColorNamesMap(context)
            val hue = hsl[0]
            val saturation = hsl[1]
            val lightness = hsl[2]
            var colorPrefix = ""
            var colorName = ""

            if (lightness > 0.7) {
                colorName = context.getString(R.string.colorNameWhite)
            } else if (lightness < 0.1) {
                colorName = context.getString(R.string.colorNameBlack)
            } else {
                if (saturation < 0.1) {
                    colorName = context.getString(R.string.colorNameGrey)
                }
            }

            if (colorName == "") {
                // colours
                var nearestColorName = ""
                var nearestColorDistance = 360.0F

                for (color in colors)
                {
                    val colorDistance = (color.value - hue).absoluteValue
                    if (colorDistance < nearestColorDistance) {
                        nearestColorDistance = colorDistance
                        nearestColorName = color.key
                    }
                }

                when {
                    nearestColorName == context.getString(R.string.colorNameOrange) &&
                        lightness < 0.6 && lightness > 0.1 -> {
                        // brown
                        nearestColorName = context.getString(R.string.colorNameBrown)
                    } lightness < 0.2 -> {
                        // dark
                        colorPrefix += context.getString(R.string.colorPrefixNameDark) + " "
                    } lightness > 0.7 -> {
                        // light
                        colorPrefix += context.getString(R.string.colorPrefixNameLight) + " "
                    } lightness > 0.8 -> {
                        // very light
                        colorPrefix += context.getString(R.string.colorPrefixNameVeryLight) + " "
                    }
                }

                if (saturation < 0.2) {
                    // greyish colour
                    colorPrefix += context.getString(R.string.colorPrefixNameGreyish) + " "
                }

                Log.d(TAG, "Nearest Color: $nearestColorName with distance: $nearestColorDistance, hue: $hue")
                colorName = nearestColorName
            }

            val fullColorName = "$colorPrefix$colorName"

            Log.d(TAG, "Color: $fullColorName, hsl: ${hsl[0]} ${hsl[1]} ${hsl[2]}")
            return  fullColorName
        }

        // analyze the color
        override fun analyze(image: ImageProxy) {
            val currentTimestamp = System.currentTimeMillis()
            if (currentTimestamp - lastAnalyzedTimestamp >= 1000 / ANALYZER_FPS) {
                val hsv = getCenterHSVFromImage(image)
                val hsl = Helpers.convertHSVToHSL(hsv)
                val colorString = getColorNameFromHsv(hsl)
                lastAnalyzedTimestamp = currentTimestamp
                listener(colorString)
            }

            image.close()
        }
    }

    fun toggleFlash(view: View) {
        if (camera.cameraInfo.hasFlashUnit()) {
            torchEnabled = if (torchEnabled) {
                camera.cameraControl.enableTorch(false)
                false
            } else {
                camera.cameraControl.enableTorch(true)
                true
            }
        }
    }
}