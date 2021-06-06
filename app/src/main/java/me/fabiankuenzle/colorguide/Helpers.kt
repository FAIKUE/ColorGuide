package me.fabiankuenzle.colorguide

import android.content.Context

object Helpers {
    fun getColorNamesMap(context: Context): MutableMap<String, Int> {
        val colors: MutableMap<String, Int> = HashMap()
        colors[context.getString(R.string.colorNameRed)] = 0
        colors[context.getString(R.string.colorNameOrange)] = 30
        colors[context.getString(R.string.colorNameYellow)] = 60
        colors[context.getString(R.string.colorNameYellowGreen)] = 90
        colors[context.getString(R.string.colorNameGreen)] = 120
        colors[context.getString(R.string.colorNameGreenCyan)] = 150
        colors[context.getString(R.string.colorNameCyan)] = 180
        colors[context.getString(R.string.colorNameBlueCyan)] = 210
        colors[context.getString(R.string.colorNameBlue)] = 240
        colors[context.getString(R.string.colorNameViolet)] = 270
        colors[context.getString(R.string.colorNameMagenta)] = 300
        colors[context.getString(R.string.colorNameRose)] = 330
        return colors
    }

    fun convertHSVToHSL(hsv: FloatArray): FloatArray {
        val h = hsv[0]
        var s = hsv[1]
        val v = hsv[2]

        // both hsv and hsl values are in [0, 1]
        val l = (2 - s) * v / 2

        if (l != 0.0F) {
            s = when {
                l == 1.0F -> {
                    0.0F
                }
                l < 0.5 -> {
                    s * v / (l * 2)
                }
                else -> {
                    s * v / (2 - l * 2)
                }
            }
        }

        return floatArrayOf(h, s, l)
    }
}