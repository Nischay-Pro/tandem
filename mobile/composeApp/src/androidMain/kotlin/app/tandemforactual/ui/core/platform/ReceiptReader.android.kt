package app.tandemforactual.ui.core.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * PP-OCRv6 on LiteRT: finds text lines, straightens the photo when the lines are tilted, then reads each line and joins
 * lines that share a printed row, so a label and its amount come back together.
 */
internal class ReceiptReader(
    context: Context,
) : Closeable {
    private val options = Interpreter.Options().setNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
    private val detector = Interpreter(model(context, "receipts/detect.tflite"), options)
    private val reader = Interpreter(model(context, "receipts/read.tflite"), options)
    private val keys =
        context.assets
            .open("receipts/keys.txt")
            .use { it.readBytes().decodeToString() }
            .split('\n')
            .dropLast(1)
    private val classes = reader.getOutputTensor(0).shape()[2]
    private val steps = reader.getOutputTensor(0).shape()[1]

    private class Box(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    )

    private class Line(
        val text: String,
        val centerY: Float,
        val height: Float,
        val left: Float,
    )

    fun read(photo: Bitmap): List<String> {
        val (boxes, skew) = detect(photo)
        if (abs(skew) <= MIN_SKEW_DEGREES) return rows(recognize(photo, boxes))
        val straight = rotate(photo, -skew)
        return try {
            rows(recognize(straight, detect(straight).first))
        } finally {
            straight.recycle()
        }
    }

    private fun detect(photo: Bitmap): Pair<List<Box>, Float> {
        val scale = DETECT_SIZE.toFloat() / maxOf(photo.width, photo.height)
        val w = (photo.width * scale).roundToInt().coerceIn(1, DETECT_SIZE)
        val h = (photo.height * scale).roundToInt().coerceIn(1, DETECT_SIZE)
        val scaled = Bitmap.createScaledBitmap(photo, w, h, true)
        val pixels = IntArray(w * h).also { scaled.getPixels(it, 0, w, 0, 0, w, h) }
        if (scaled !== photo) scaled.recycle()
        val input = floatBuffer(DETECT_SIZE * DETECT_SIZE * 3)
        for (y in 0 until h) {
            input.position(y * DETECT_SIZE * 3)
            for (x in 0 until w) {
                val c = pixels[y * w + x]
                input.put((Color.blue(c) / 255f - 0.485f) / 0.229f)
                input.put((Color.green(c) / 255f - 0.456f) / 0.224f)
                input.put((Color.red(c) / 255f - 0.406f) / 0.225f)
            }
        }
        input.rewind()
        val output = floatBuffer(DETECT_SIZE * DETECT_SIZE)
        detector.run(input, output)
        output.rewind()
        val probability = FloatArray(w * h)
        for (y in 0 until h) {
            output.position(y * DETECT_SIZE)
            output.get(probability, y * w, w)
        }
        return components(probability, w, h, photo.width / w.toFloat(), photo.height / h.toFloat())
    }

    private fun components(
        probability: FloatArray,
        w: Int,
        h: Int,
        scaleX: Float,
        scaleY: Float,
    ): Pair<List<Box>, Float> {
        val seen = BooleanArray(w * h)
        val stack = IntArray(w * h)
        val boxes = mutableListOf<Box>()
        val angles = mutableListOf<Pair<Double, Int>>()
        for (start in probability.indices) {
            if (seen[start] || probability[start] <= TEXT_THRESHOLD) continue
            var size = 0
            var top = 0
            stack[top++] = start
            seen[start] = true
            var minX = w
            var maxX = 0
            var minY = h
            var maxY = 0
            var score = 0.0
            var sx = 0.0
            var sy = 0.0
            var sxx = 0.0
            var syy = 0.0
            var sxy = 0.0
            while (top > 0) {
                val index = stack[--top]
                val x = index % w
                val y = index / w
                size++
                score += probability[index]
                sx += x
                sy += y
                sxx += x.toDouble() * x
                syy += y.toDouble() * y
                sxy += x.toDouble() * y
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                for (next in intArrayOf(index - 1, index + 1, index - w, index + w)) {
                    if (next < 0 || next >= w * h || seen[next] || probability[next] <= TEXT_THRESHOLD) continue
                    if ((next == index - 1 && x == 0) || (next == index + 1 && x == w - 1)) continue
                    seen[next] = true
                    stack[top++] = next
                }
            }
            val boxW = maxX - minX + 1
            val boxH = maxY - minY + 1
            if (minOf(boxW, boxH) < 3 || score / size < BOX_THRESHOLD) continue
            if (boxW > 3 * boxH) {
                val mx = sx / size
                val my = sy / size
                val angle = 0.5 * atan2(2 * (sxy / size - mx * my), (sxx / size - mx * mx) - (syy / size - my * my))
                angles += Math.toDegrees(angle) to size
            }
            val grow = boxW * boxH * UNCLIP_RATIO / (2f * (boxW + boxH))
            boxes +=
                Box(
                    ((minX - grow) * scaleX).coerceAtLeast(0f),
                    ((minY - grow) * scaleY).coerceAtLeast(0f),
                    ((maxX + 1 + grow) * scaleX).coerceAtMost(w * scaleX),
                    ((maxY + 1 + grow) * scaleY).coerceAtMost(h * scaleY),
                )
        }
        return boxes to weightedMedian(angles).toFloat()
    }

    private fun weightedMedian(values: List<Pair<Double, Int>>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sortedBy { it.first }
        val half = sorted.sumOf { it.second } / 2.0
        var total = 0.0
        return sorted.first { (_, weight) -> (total + weight >= half).also { total += weight } }.first
    }

    private fun recognize(
        photo: Bitmap,
        boxes: List<Box>,
    ): List<Line> =
        boxes.mapNotNull { box ->
            val left = box.left.toInt()
            val top = box.top.toInt()
            val width = (ceil(box.right).toInt() - left).coerceAtMost(photo.width - left)
            val height = (ceil(box.bottom).toInt() - top).coerceAtMost(photo.height - top)
            if (width < 2 || height < 2) return@mapNotNull null
            val targetWidth = ceil(READ_HEIGHT * width / height.toFloat()).toInt().coerceIn(READ_HEIGHT / 3, READ_WIDTH)
            val crop = Bitmap.createBitmap(photo, left, top, width, height)
            val scaled = Bitmap.createScaledBitmap(crop, targetWidth, READ_HEIGHT, true)
            if (crop !== photo && crop !== scaled) crop.recycle()
            val pixels = IntArray(targetWidth * READ_HEIGHT).also { scaled.getPixels(it, 0, targetWidth, 0, 0, targetWidth, READ_HEIGHT) }
            scaled.recycle()
            val input = floatBuffer(READ_HEIGHT * READ_WIDTH * 3)
            for (y in 0 until READ_HEIGHT) {
                input.position(y * READ_WIDTH * 3)
                for (x in 0 until targetWidth) {
                    val c = pixels[y * targetWidth + x]
                    input.put(Color.blue(c) / 127.5f - 1f)
                    input.put(Color.green(c) / 127.5f - 1f)
                    input.put(Color.red(c) / 127.5f - 1f)
                }
            }
            input.rewind()
            val output = floatBuffer(steps * classes)
            reader.run(input, output)
            val text = decode(output)
            text.takeIf { it.isNotBlank() }?.let { Line(it.trim(), (box.top + box.bottom) / 2, box.bottom - box.top, box.left) }
        }

    private fun decode(output: FloatBuffer): String =
        buildString {
            var previous = -1
            for (step in 0 until steps) {
                var best = 0
                var bestScore = Float.NEGATIVE_INFINITY
                for (c in 0 until classes) {
                    val score = output.get(step * classes + c)
                    if (score > bestScore) {
                        bestScore = score
                        best = c
                    }
                }
                if (best != previous && best != 0) append(if (best <= keys.size) keys[best - 1] else " ")
                previous = best
            }
        }

    private fun rows(lines: List<Line>): List<String> {
        val rows = mutableListOf<MutableList<Line>>()
        for (line in lines.sortedBy { it.centerY }) {
            val first = rows.lastOrNull()?.first()
            if (first != null && abs(first.centerY - line.centerY) < minOf(first.height, line.height) / 2) {
                rows.last() += line
            } else {
                rows += mutableListOf(line)
            }
        }
        return rows.map { row -> row.sortedBy { it.left }.joinToString("  ") { it.text } }
    }

    private fun rotate(
        photo: Bitmap,
        degrees: Float,
    ): Bitmap {
        val matrix = Matrix().apply { setRotate(degrees) }
        val bounds = android.graphics.RectF(0f, 0f, photo.width.toFloat(), photo.height.toFloat()).also(matrix::mapRect)
        matrix.postTranslate(-bounds.left, -bounds.top)
        val rotated = Bitmap.createBitmap(bounds.width().roundToInt(), bounds.height().roundToInt(), Bitmap.Config.ARGB_8888)
        Canvas(rotated).apply {
            drawColor(Color.WHITE)
            drawBitmap(photo, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return rotated
    }

    override fun close() {
        detector.close()
        reader.close()
    }

    private companion object {
        const val DETECT_SIZE = 960
        const val READ_HEIGHT = 48
        const val READ_WIDTH = 960
        const val TEXT_THRESHOLD = 0.2f
        const val BOX_THRESHOLD = 0.4
        const val UNCLIP_RATIO = 1.4f
        const val MIN_SKEW_DEGREES = 0.5f

        fun floatBuffer(count: Int): FloatBuffer = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

        fun model(
            context: Context,
            path: String,
        ): ByteBuffer =
            context.assets.openFd(path).use { file ->
                FileInputStream(file.fileDescriptor).channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, file.startOffset, file.declaredLength)
                }
            }
    }
}
