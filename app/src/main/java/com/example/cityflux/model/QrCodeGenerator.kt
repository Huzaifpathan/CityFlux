package com.example.cityflux.model

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * QR Code generation utility
 * Phase 4: Generate QR codes for parking bookings
 */
object QrCodeGenerator {
    
    /**
     * Generate QR code bitmap from string data
     */
    fun generateQrCode(
        data: String,
        size: Int = 512,
        backgroundColor: Int = Color.WHITE,
        foregroundColor: Int = Color.BLACK
    ): Bitmap? {
        return try {
            val hints = hashMapOf<EncodeHintType, Any>()
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.H
            hints[EncodeHintType.MARGIN] = 1
            
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(
                data,
                BarcodeFormat.QR_CODE,
                size,
                size,
                hints
            )
            
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(
                        x,
                        y,
                        if (bitMatrix[x, y]) foregroundColor else backgroundColor
                    )
                }
            }
            
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Generate QR code with CityFlux branding colors
     */
    fun generateBrandedQrCode(data: String, size: Int = 512): Bitmap? {
        return generateQrCode(
            data = data,
            size = size,
            backgroundColor = Color.parseColor("#FFFFFF"),
            foregroundColor = Color.parseColor("#3B82F6") // Primary blue
        )
    }
}
