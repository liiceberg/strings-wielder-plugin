package com.liiceberg.strings.detector

object PluralDetector : Detector {

    private val numberRegex = Regex("""(%\d*\$?d|\d+)""")
    private val wordRegex = Regex("""\p{L}+""")
    private val digitRegex = Regex("\\d+")

    override fun detect(text: String): List<Pattern> {
        val result = mutableListOf<Pattern>()

        val numberMatches = numberRegex.findAll(text)
        for (numMatch in numberMatches) {
            val numRange = numMatch.range

            val rightPart = text.substring(numRange.last + 1)
            val rightWord = wordRegex.find(rightPart)

            val leftPart = text.substring(0, numRange.first)
            val leftWord = wordRegex.findAll(leftPart).lastOrNull()

            when {
                rightWord != null -> {
                    val range = numRange.first..(numRange.last + 1 + rightWord.range.last)
                    result += Pattern(
                        type = PatternType.PLURAL,
                        value = text.substring(range),
                        range = range
                    )
                }

                leftWord != null -> {
                    val range = leftWord.range.first..numRange.last
                    result += Pattern(
                        type = PatternType.PLURAL,
                        value = text.substring(range),
                        range = range
                    )
                }
            }
        }

        return result
    }

    fun getNumber(input: String) : String {
        return digitRegex.findAll(input).firstOrNull()?.value ?: ""
    }

}