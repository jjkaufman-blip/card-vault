package com.cardvault.scan

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** On-device text and barcode reading (Google ML Kit). Free and works offline. */
object Ocr {
    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val barcodeScanner by lazy { BarcodeScanning.getClient() }

    suspend fun readText(bitmap: Bitmap): List<TextLine> {
        val result = textRecognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        return result.textBlocks.flatMap { block ->
            block.lines.map { line -> TextLine(line.text, line.boundingBox?.height() ?: 0) }
        }
    }

    suspend fun readBarcodes(bitmap: Bitmap): List<String> {
        val result = barcodeScanner.process(InputImage.fromBitmap(bitmap, 0)).await()
        return result.mapNotNull { it.rawValue }
    }
}
