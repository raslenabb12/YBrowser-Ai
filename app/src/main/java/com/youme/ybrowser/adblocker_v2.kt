package com.youme.ybrowser

import android.content.Context
import android.net.Uri
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.regex.Pattern
import kotlin.math.ln
import kotlin.math.log2
import androidx.core.net.toUri

class AdBlockerAI_v2(context: Context) {

    private var interpreter: Interpreter
    private val means = floatArrayOf(
        154.82928f, 0.38151714f, 1.0f, 0.6184829f, 0.010316768f, 0.0028812424f, 0.0022037458f,
        0.065062255f, 0.035056684f, 0.3834066f, 0.18376154f, 4.641534f, 3.476188f, 0.1318385f,
        2.6373906f, 0.0060334834f, 0.9966069f, 1.881935E-5f, 0.14800477f, 0.28604847f,
        0.37590334f, 0.052353546f, 0.14228182f, 0.030884434f, 0.0019722679f, 0.04026776f, 0.00083369715f
    )

    private val scales = floatArrayOf(
        409.0351f, 0.48575902f, 1.0f, 0.48575902f, 0.10104618f, 0.05359982f, 0.046892315f,
        0.24663568f, 0.18392311f, 0.48621598f, 0.3872896f, 0.46486488f, 2.1969473f, 0.16794993f,
        7.8286037f, 0.07744082f, 0.45079568f, 0.0043380866f, 0.3551047f, 0.4519123f,
        0.48435527f, 0.22273898f, 0.34933895f, 0.17300458f, 0.044366404f, 0.19658655f, 0.028861776f
    )

    private val RESOURCE_TYPES = listOf(
        "script", "image", "stylesheet", "xmlhttprequest",
        "sub_frame", "media", "font", "other"
    )

    private val AD_REGEX = Pattern.compile(
        "(^|[/._?&=-])(ad|ads|advert|banner|track|pixel|beacon|analytics|telemetry|sponsor|promo|click|imp|doubleclick|googlesyndication)([/._?&=-]|$)",
        Pattern.CASE_INSENSITIVE
    )

    init {
        val modelBuffer = loadModelFile(context)
        val options = Interpreter.Options().setNumThreads(2)
        interpreter = Interpreter(modelBuffer, options)
    }

    /**
     * @param url The request URL
     * @param documentUrl The main page URL (to check is_third_party)
     * @param resourceType The type (e.g., "script", "image") from WebView
     */
    fun shouldBlock(url: String, documentUrl: String, resourceType: String?): Boolean {
        try {
            val uri = url.toUri()
            val docUri = documentUrl.toUri()

            // 1. Compute Features (Must be in same order as X.columns in Python)
            val features = FloatArray(27)
            var idx = 0

            // --- Group 1: URL_FEATURES (11) ---
            features[idx++] = url.length.toFloat()
            features[idx++] = (isSubdomain(uri))
            features[idx++] = if (uri.query != null) 1f else 0f
            features[idx++] = isThirdParty(uri, docUri)
            features[idx++] = if (url.contains(uri.host ?: "")) 1f else 0f
            features[idx++] = if (uri.query?.contains(";") == true) 1f else 0f
            features[idx++] = if (hasScreenSize(url)) 1f else 0f
            features[idx++] = if (hasAdSize(url)) 1f else 0f
            features[idx++] = if (hasAdSize(uri.query ?: "")) 1f else 0f
            features[idx++] = if (AD_REGEX.matcher(url).find()) 1f else 0f
            features[idx++] = 0f

            features[idx++] = calculateEntropy(url)
            val path = uri.path ?: "/"
            features[idx++] = path.count { it == '/' }.toFloat()
            features[idx++] = (path.count { it.isDigit() }.toFloat() / path.length.coerceAtLeast(1))
            features[idx++] = uri.queryParameterNames.size.toFloat()
            features[idx++] = if (uri.fragment != null) 1f else 0f
            features[idx++] = (uri.host?.split(".")?.size?.minus(2)?.coerceAtLeast(0) ?: 0).toFloat()
            features[idx++] = if (isIpAddress(uri.host ?: "")) 1f else 0f
            features[idx++] = if (AD_REGEX.matcher(url).find()) 1f else 0f

            val type = resourceType?.lowercase() ?: "other"
            RESOURCE_TYPES.forEach { rt ->
                features[idx++] = if (type == rt) 1f else 0f
            }

            for (i in features.indices) {
                features[i] = (features[i] - means[i]) / scales[i]
            }

            val output = Array(1) { FloatArray(1) }
            interpreter.run(arrayOf(features), output)

            return output[0][0] > 0.5f
        } catch (e: Exception) {
            return false
        }
    }

    private fun isThirdParty(uri: Uri, docUri: Uri): Float {
        val host = uri.host ?: return 0f
        val docHost = docUri.host ?: return 0f
        return if (host.endsWith(docHost) || docHost.endsWith(host)) 0f else 1f
    }

    private fun isSubdomain(uri: Uri): Float {
        val host = uri.host ?: return 0f
        return if (host.split(".").size > 2) 1f else 0f
    }

    private fun calculateEntropy(s: String): Float {
        val counts = s.groupingBy { it }.eachCount()
        val len = s.length.toDouble()
        return counts.values.sumOf {
            val p = it / len
            -(p * log2(p))
        }.toFloat()
    }

    private fun hasScreenSize(url: String) = url.contains(Regex("\\d{3,4}x\\d{3,4}"))
    private fun hasAdSize(url: String) = url.contains(Regex("(300x250|728x90|160x600)"))
    private fun isIpAddress(host: String) = host.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$"))

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("ad_blocker_quant.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }
}