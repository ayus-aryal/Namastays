package com.example.namastays.trek.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.example.namastays.trek.domain.WaypointType

object WaypointIcons {

    /**
     * Programmatically generates a circular icon bitmap for each waypoint type
     * This avoids needing actual image files in assets
     */
    fun getBitmap(context: Context, type: WaypointType): Bitmap {
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = getColor(type)
            style = Paint.Style.FILL
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 22f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        val radius = size / 2f - 2f
        canvas.drawCircle(size / 2f, size / 2f, radius, bgPaint)
        canvas.drawCircle(size / 2f, size / 2f, radius, borderPaint)

        // Draw emoji/letter as icon
        val text = getSymbol(type)
        val yPos = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(text, size / 2f, yPos, textPaint)

        return bitmap
    }

    private fun getColor(type: WaypointType): Int {
        return when (type) {
            WaypointType.TEAHOUSE         -> 0xFF1B4332.toInt()  // dark green
            WaypointType.WATER            -> 0xFF1565C0.toInt()  // blue
            WaypointType.VIEWPOINT        -> 0xFFD4A017.toInt()  // ochre
            WaypointType.CAMPSITE         -> 0xFF6A1B9A.toInt()  // purple
            WaypointType.TRAILHEAD        -> 0xFF2E7D32.toInt()  // green
            WaypointType.CHECKPOINT       -> 0xFFE65100.toInt()  // orange
            WaypointType.VILLAGE          -> 0xFF4E342E.toInt()  // brown
            WaypointType.EMERGENCY        -> 0xFFC62828.toInt()  // red
            WaypointType.PASS             -> 0xFF546E7A.toInt()  // blue grey
            WaypointType.SUSPENSION_BRIDGE -> 0xFF795548.toInt() // brown
        }
    }

    private fun getSymbol(type: WaypointType): String {
        return when (type) {
            WaypointType.TEAHOUSE          -> "T"
            WaypointType.WATER             -> "W"
            WaypointType.VIEWPOINT         -> "V"
            WaypointType.CAMPSITE          -> "C"
            WaypointType.TRAILHEAD         -> "S"
            WaypointType.CHECKPOINT        -> "!"
            WaypointType.VILLAGE           -> "V"
            WaypointType.EMERGENCY         -> "+"
            WaypointType.PASS              -> "P"
            WaypointType.SUSPENSION_BRIDGE -> "B"
        }
    }

    fun getIconId(type: WaypointType): String {
        return "waypoint-icon-${type.name.lowercase()}"
    }
}