package com.example.myganapp

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.qualcomm.qti.snpe.SNPE
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var loadModelButton: Button
    private lateinit var generateButton: Button
    private lateinit var resultImageView: ImageView
    private lateinit var statusTextView: TextView
    private lateinit var timeTextView: TextView

    private var inferenceTask: GANInferenceTask? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentModelUri: Uri? = null

    // --- GAN Parameters (CRITICAL: Adjust these to match your model!) ---
    private val LATENT_DIM = 256   // Size of the random input vector
    private val OUTPUT_CHANNELS = 3 // RGB = 3
    private val OUTPUT_HEIGHT = 256 // Example image height
    private val OUTPUT_WIDTH = 256  // Example image width

    companion object {
        private const val REQUEST_CODE_OPEN_DOCUMENT = 1001
        private const val REQUEST_CODE_PERMISSION_READ_STORAGE = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadModelButton = findViewById(R.id.loadModelButton)
        generateButton = findViewById(R.id.generateButton)
        resultImageView = findViewById(R.id.resultImageView)
        statusTextView = findViewById(R.id.statusTextView)
        timeTextView = findViewById(R.id.generationTimeTextView)

        loadModelButton.setOnClickListener { openFilePicker() }
        generateButton.setOnClickListener { generateImage() }
        generateButton.isEnabled = false // Disabled until a model is loaded

        // Request storage permission for Android 10 and below
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_CODE_PERMISSION_READ_STORAGE)
            }
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "application/x-dlc"))
        }
        startActivityForResult(intent, REQUEST_CODE_OPEN_DOCUMENT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OPEN_DOCUMENT && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                currentModelUri = uri
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                loadModelFromUri(uri)
            } ?: run {
                Toast.makeText(this, "Failed to get file URI", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadModelFromUri(uri: Uri) {
        statusTextView.text = "Loading model..."
        loadModelButton.isEnabled = false
        generateButton.isEnabled = false

        Thread {
            try {
                // 1. Copy the selected DLC file to internal storage
                val modelFile = copyUriToInternalStorage(uri)
                if (modelFile == null) {
                    throw Exception("Failed to copy model file")
                }

                // 2. Initialize the inference task with the copied file
                inferenceTask?.close()
                inferenceTask = GANInferenceTask(this@MainActivity, modelFile).apply {
                    initialize()
                }

                mainHandler.post {
                    statusTextView.text = "Model loaded: ${getFileNameFromUri(uri)}"
                    generateButton.isEnabled = true
                    Toast.makeText(this@MainActivity, "Model loaded successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading model", e)
                mainHandler.post {
                    statusTextView.text = "Model load failed: ${e.message}"
                    Toast.makeText(this@MainActivity, "Failed to load model: ${e.message}", Toast.LENGTH_LONG).show()
                    loadModelButton.isEnabled = true
                }
            }
        }.start()
    }

    private fun copyUriToInternalStorage(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val modelFile = File(filesDir, "user_model_${System.currentTimeMillis()}.dlc")
            FileOutputStream(modelFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()
            modelFile
        } catch (e: Exception) {
            Log.e("MainActivity", "Error copying file", e)
            null
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var fileName = "Unknown"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) fileName = cursor.getString(nameIndex)
            }
        }
        return fileName
    }

    private fun generateImage() {
        if (inferenceTask == null) {
            Toast.makeText(this, "No model loaded", Toast.LENGTH_SHORT).show()
            return
        }

        generateButton.isEnabled = false
        statusTextView.text = "Generating..."
        resultImageView.setImageBitmap(null)
        timeTextView.text = ""

        Thread {
            try {
                val startTime = System.nanoTime()

                // Generate random latent vector
                val random = java.util.Random()
                val latentVector = FloatArray(LATENT_DIM) { random.nextGaussian().toFloat() }
                val output = inferenceTask!!.runInference(latentVector)

                val endTime = System.nanoTime()
                val inferenceTimeMs = (endTime - startTime) / 1_000_000.0

                // Convert raw output to a Bitmap
                val bitmap = convertOutputToBitmap(output, OUTPUT_HEIGHT, OUTPUT_WIDTH, OUTPUT_CHANNELS)

                mainHandler.post {
                    resultImageView.setImageBitmap(bitmap)
                    timeTextView.text = String.format("Generation time: %.2f ms", inferenceTimeMs)
                    statusTextView.text = "Done"
                    generateButton.isEnabled = true
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Generation failed", e)
                mainHandler.post {
                    statusTextView.text = "Generation failed: ${e.message}"
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    generateButton.isEnabled = true
                }
            }
        }.start()
    }

    private fun convertOutputToBitmap(output: FloatArray, height: Int, width: Int, channels: Int): Bitmap {
        require(output.size == height * width * channels) {
            "Output size mismatch: expected ${height * width * channels}, got ${output.size}"
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = (y * width + x) * channels
                // Convert from [-1, 1] to [0, 255]
                val r = ((output[idx] + 1f) / 2f * 255f).coerceIn(0f, 255f).toInt()
                val g = ((output[idx + 1] + 1f) / 2f * 255f).coerceIn(0f, 255f).toInt()
                val b = ((output[idx + 2] + 1f) / 2f * 255f).coerceIn(0f, 255f).toInt()
                bitmap.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        return bitmap
    }

    override fun onDestroy() {
        super.onDestroy()
        inferenceTask?.close()
    }
}