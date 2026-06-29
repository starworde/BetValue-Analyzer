package com.soliano.betvalueanalyzer.ui

fun t(language: String, fr: String, en: String, es: String, de: String): String =
    when (language.lowercase()) {
        "en" -> en
        "es" -> es
        "de" -> de
        else -> fr
    }

