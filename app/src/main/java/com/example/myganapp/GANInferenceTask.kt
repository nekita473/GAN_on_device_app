package com.example.myganapp

import android.app.Application
import android.content.Context
import android.util.Log
import com.qualcomm.qti.snpe.SNPE
import com.qualcomm.qti.snpe.NeuralNetwork
import com.qualcomm.qti.snpe.FloatTensor
import java.io.File
import java.util.*

class GANInferenceTask(private val context: Context, private val modelFile: File) {
    private var neuralNetwork: NeuralNetwork? = null
    private lateinit var inputTensorName: String
    private lateinit var outputTensorName: String

    companion object {
        private const val TAG = "GANInferenceTask"
    }
    val app = context.applicationContext as Application
    private fun getDefaultBuilder(): SNPE.NeuralNetworkBuilder {
        val snpeBuilder: SNPE.NeuralNetworkBuilder = SNPE.NeuralNetworkBuilder(app)
        snpeBuilder.setPerformanceProfile(NeuralNetwork.PerformanceProfile.SUSTAINED_HIGH_PERFORMANCE)
        val runtimeCheck: NeuralNetwork.RuntimeCheckOption =
            NeuralNetwork.RuntimeCheckOption.UNSIGNEDPD_CHECK
        snpeBuilder.setRuntimeCheckOption(runtimeCheck)
        snpeBuilder.setDebugEnabled(false)
        snpeBuilder.setUnsignedPD(true)
        snpeBuilder.setCpuFallbackEnabled(false)
        return snpeBuilder
    }


    @Throws(Exception::class)
    fun initialize() {
        //val app = context.applicationContext as Application
        Log.d(TAG, "Initializing with model: ${modelFile.absolutePath}")

        // Set runtime priority: try DSP (NPU) first, then GPU, then CPU
        // val runtimeList = listOf(NeuralNetwork.Runtime.DSP, NeuralNetwork.Runtime.GPU, NeuralNetwork.Runtime.CPU)

        val builder = getDefaultBuilder()
            .setRuntimeOrder(NeuralNetwork.Runtime.DSP)
            .setModel(modelFile) // Load the dynamically selected model file

        neuralNetwork = builder.build()
            ?: throw Exception("Failed to build neural network")

        // Get input and output tensor names
        val inputTensorNames = neuralNetwork!!.inputTensorsNames
        val outputTensorNames = neuralNetwork!!.outputTensorsNames

        if (inputTensorNames.isEmpty() || outputTensorNames.isEmpty()) {
            throw Exception("Model has no input or output tensors")
        }

        inputTensorName = inputTensorNames.first()
        outputTensorName = outputTensorNames.first()

        Log.i(TAG, "Model loaded. Input: '$inputTensorName', Output: '$outputTensorName'")
    }

    @Throws(Exception::class)
    fun runInference(inputData: FloatArray): FloatArray {
        val network = neuralNetwork ?: throw Exception("Network not initialized")

        // Get the input tensor shape to determine dimensions
        // For a GAN, the latent vector is often 1x1xN, where N is the latent dimension
        val inputShape = network.inputTensorsShapes
        Log.d(TAG, "Input tensor shape: $inputShape")

        // Create the input tensor. Adjust dimensions as needed for your model.
        // This example assumes a shape of [1, 1, latent_dimension]
        val inputTensor = network.createFloatTensor(1, 256)
        inputTensor.write(inputData, 0, inputData.size)

        val inputsMap = mapOf(inputTensorName to inputTensor)
        val outputsMap = network.execute(inputsMap)
        val outputTensor = outputsMap[outputTensorName] ?: throw Exception("No output tensor found")

        val outputSize = outputTensor.size
        val outputData = FloatArray(outputSize)
        outputTensor.read(outputData, 0, outputSize)

        // Clean up tensors to release native resources
        inputTensor.release()
        outputTensor.release()

        return outputData
    }

    fun close() {
        neuralNetwork?.release()
        neuralNetwork = null
    }
}