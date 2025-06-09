package com.liiceberg.utils

object Constants {

    var javaExtractTemplate = "getString(\$id)"

    object Preferences {
        const val STRING_WIELDER_PREFERENCES = "STRING_WIELDER_PREFERENCES"
        const val PREFIX = "PREFIX"
        const val INCLUDE_REGEX = "INCLUDE_REGEX_END"
        const val EXCLUDE_REGEX = "EXCLUDE_REGEX"
        const val IMPORT_PACKAGE = "IMPORT_PACKAGE"
    }

    object Titles {
        const val SETTINGS_TABLE = "Plugin Settings"
        const val HARDCODED_STRINGS_FOUND_TABLE = "Hardcoded Strings Found"
    }

    object Labels {
        const val INCLUDE_REGEX_LABEL = "Include files regex (Java/Kotlin):"
        const val EXCLUDE_REGEX_LABEL = "Exclude files regex (Java/Kotlin):"
        const val PREFIX_LABEL = "Key prefix:"
        const val INVALID_PREFIX_FIELD = "Please enter a valid prefix"
        const val LOADING_STRINGS = "Loading Strings...This may take some time."
        const val INVALID_KEY_FILED = "The key can consist of letters, numbers, and _"
        const val INVALID_EXCLUDE_REGEX_FILED = "Please enter a valid exclude regex pattern"
        const val INVALID_INCLUDE_REGEX_FILED = "Please enter a valid include regex pattern"
        const val INVALID_KEYS_FOUND = "Attention: incorrect keys were found. Edit them before continuing."
    }

    object Path {
        const val SOURCE_CODE_PATH = "app/src/main/java"
        const val STRINGS_XML_PATH = "app/src/main/res/values/strings.xml"
        const val RES_LAYOUT_PATH = "/src/main/res/layout"
        const val RES_MENU_PATH = "/src/main/res/menu"
    }

    object RegexTemplates {
        const val DEFAULT_INCLUDE_REGEX = ".*"
        const val INCLUDE_REGEX_END = "\\.(kt|java)\$"
        val PREFIX_REGEX = Regex("^[0-9a-zA-Z_]*\$")
        val KEY_REGEX = Regex("^[A-Za-z0-9-_]+$")
        val KEY_GENERATOR_REGEX = Regex("[^A-Za-z0-9_ ]")
        val DATA_BINDING_VARIABLE_REGEX = Regex("@\\{.*}")
    }
}

