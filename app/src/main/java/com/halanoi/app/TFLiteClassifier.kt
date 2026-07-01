package com.halanoi.app

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp

class TFLiteClassifier(context: Context) {
    private val TAG = "TFLiteClassifier"
    private var interpreter: Interpreter? = null
    
    // Safety flag to let the Service know we are ready
    @Volatile
    var isReady = false
        private set

    // The "Decoder Ring" from your python environment
    private val vocab = mutableMapOf<String, Int>()
    
    // Must match the max_length=128 from your train_transformer_tf.py
    private val MAX_SEQ_LEN = 128 
    
    // Dynamically loaded labels
    private val labels = mutableListOf<String>()

    init {
        try {
            // 1. Load the labels.txt (Ensures sync with model output layers)
            loadLabels(context)

            // 2. Load the vocab.txt decoder ring
            loadVocab(context)
            
            // 3. Load the TFLite AI Model
            val options = Interpreter.Options().apply {
                setNumThreads(4) // Use multi-threading for faster scanning
            }
            
            val modelBuffer = loadModelFile(context, "halanoi_transformer.tflite")
            interpreter = Interpreter(modelBuffer, options)
            
            isReady = true
            Log.d(TAG, "✅ Brain successfully loaded and ready for inference!")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load the model or vocab: ${e.message}")
            isReady = false
        }
    }

    private fun loadLabels(context: Context) {
        context.assets.open("labels.txt").bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.isNotBlank()) labels.add(line.trim())
            }
        }
    }

    private fun loadVocab(context: Context) {
        val reader = BufferedReader(InputStreamReader(context.assets.open("vocab.txt")))
        var index = 0
        reader.forEachLine { line ->
            vocab[line.trim()] = index++
        }
        reader.close()
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    fun classifyText(text: String): Map<String, Float> {
        if (interpreter == null || !isReady) return emptyMap()

        // --- THE TRANSLATION PIPELINE ---

        // 1. Clean the text (strip weird UI symbols) and split into words
        val words = text.lowercase().replace(Regex("[^a-z0-9 ]"), "").split(" ")
        
        // 2. Setup the arrays (Filled with 0s for Padding)
        val inputIds = IntArray(MAX_SEQ_LEN) { 0 } 
        val attentionMask = IntArray(MAX_SEQ_LEN) { 0 }
        
        // 3. Inject the Hidden [CLS] token at the start
        inputIds[0] = 101 // HuggingFace [CLS] token
        attentionMask[0] = 1
        
        // 4. Translate English words to Math IDs
        var currentPos = 1
        for (word in words) {
            if (currentPos >= MAX_SEQ_LEN - 1) break // Save room for the [SEP] tag
            if (word.isNotBlank()) {
                // If word isn't in vocab.txt, fallback to [UNK] (100)
                val id = vocab[word] ?: 100 
                inputIds[currentPos] = id
                attentionMask[currentPos] = 1
                currentPos++
            }
        }
        
        // 5. Inject the Hidden [SEP] token at the end
        inputIds[currentPos] = 102 // HuggingFace [SEP] token
        attentionMask[currentPos] = 1

        // --- RUNNING INFERENCE ---
        
        // We dynamically check which tensor is input_ids vs attention_mask to prevent a total crash.
        val tensorCount = interpreter?.inputTensorCount ?: 0
        val inputs = arrayOfNulls<Any>(tensorCount)
        
        for (i in 0 until tensorCount) {
            val name = interpreter!!.getInputTensor(i).name()
            if (name.contains("attention") || name.contains("mask")) {
                inputs[i] = arrayOf(attentionMask)
            } else {
                inputs[i] = arrayOf(inputIds)
            }
        }

        // Setup the output box [1, N] for your specific classes
        val outputMap = mutableMapOf<Int, Any>()
        val outputArray = Array(1) { FloatArray(labels.size) }
        outputMap[0] = outputArray
        
        // Run the neural network
        try {
            interpreter?.runForMultipleInputsOutputs(inputs, outputMap)
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}")
            return emptyMap()
        }
        
        // --- DECODING THE OUTPUT (Softmax) ---
        val logits = outputArray[0]
        val maxLogit = logits.maxOrNull() ?: 0f
        var sumExp = 0f
        val expLogits = FloatArray(logits.size)
        
        for (i in logits.indices) {
            val ex = exp((logits[i] - maxLogit).toDouble()).toFloat()
            expLogits[i] = ex
            sumExp += ex
        }

        val results = mutableMapOf<String, Float>()
        for (i in labels.indices) {
            val prob = if (sumExp > 0) expLogits[i] / sumExp else 0f
            results[labels[i]] = prob
        }

        // Return the labels sorted by highest probability
        return results.toList().sortedByDescending { it.second }.toMap()
    }

    fun close() {
        isReady = false
        interpreter?.close()
        interpreter = null
    }
}
