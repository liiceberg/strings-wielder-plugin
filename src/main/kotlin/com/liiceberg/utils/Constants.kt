package com.liiceberg.utils

object Constants {

    const val STRING_RESOURCE_FILE = "strings.xml"
    const val STRING_RESOURCE_FILE_DIR = "values"

    object Preferences {
        const val STRING_WIELDER_PREFERENCES = "STRING_WIELDER_PREFERENCES"
        const val LOCAL_STORAGE = "LOCAL_STORAGE"
        const val BASE_LANGUAGE = "BASE_LANGUAGE"
    }

    object Titles {
        const val HARDCODED_STRINGS_FOUND_TABLE = "Hardcoded Strings Found"
    }

    object Labels {
        const val INVALID_KEY_FILED = "The key can consist of letters, numbers, and _"
        const val INVALID_KEYS_FOUND = "Attention: incorrect keys were found. Edit them before continuing."
    }

    object RegexTemplates {
        val KEY_REGEX = Regex("^[a-z0-9-_]+$")
        val KEY_GENERATOR_REGEX = Regex("[^A-Za-z0-9_ ]")
        val DIGIT_REGEX = Regex("\\d+")
    }
}
