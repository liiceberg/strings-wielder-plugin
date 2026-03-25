package com.liiceberg.strings.detector

interface Detector {
    fun detect(text: String) : List<Pattern>
}