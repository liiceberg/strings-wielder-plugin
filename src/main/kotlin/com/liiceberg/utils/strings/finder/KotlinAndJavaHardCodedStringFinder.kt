package com.liiceberg.utils.strings.finder

class KotlinAndJavaHardCodedStringFinder : HardCodedStringFinder() {

    override fun regex() = Regex("\".*?\"")

    override fun shouldInclude(it: String): Boolean {
        return it.isNotBlank()
    }

    override fun extractHardCodedString(it: String) = it.replace("\"", "")

}