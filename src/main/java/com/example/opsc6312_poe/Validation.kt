package com.example.opsc6312_poe

object Validation {
    fun email(value: String): String? = when {
        value.isBlank() -> "Email is required."
        !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(value.trim()) -> "Enter a valid email address."
        else -> null
    }

    fun password(value: String): String? = when {
        value.length < 6 -> "Use at least 6 characters."
        else -> null
    }

    fun required(label: String, value: String): String? =
        if (value.trim().isEmpty()) "$label is required." else null
}
