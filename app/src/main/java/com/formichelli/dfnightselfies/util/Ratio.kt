package com.formichelli.dfnightselfies.util

enum class Ratio(val label: String, val width: Int, val height: Int) {
    ANY("ANY", 0, 0), _16_9("16:9", 16, 9), _4_3("4:3", 4, 3), _1_1("1:1", 1, 1);

    fun matches(width: Int, height: Int) = matches(doubleValue(width, height))

    fun matches(ratio: Double) = this == Ratio.ANY || Math.abs(doubleValue() - ratio) < 0.1

    fun doubleValue() = if (this.height != 0) doubleValue(width, height) else 0.0

    companion object {
        fun fromLabel(label: String): Ratio {
            enumValues<Ratio>().forEach {
                if (it.label == label)
                    return it
            }

            return Ratio.ANY
        }

        fun fromRatio(ratio: Double): Ratio {
            return enumValues<Ratio>().filter { it != ANY && it.matches(ratio) }.getOrElse(0) { Ratio.ANY }
        }

        fun fromRatio(width: Int, height: Int) = fromRatio(doubleValue(width, height))

        fun doubleValue(width: Int, height: Int) = width.toDouble() / height.toDouble()
    }
}