package com.example.opsc6312_poe

import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ValidationTest {
    @Test
    fun `accepts a valid email`() {
        assertNull(Validation.email("jared@example.com"))
    }

    @Test
    fun `rejects invalid account details`() {
        assertEquals("Enter a valid email address.", Validation.email("not-an-email"))
        assertEquals("Use at least 6 characters.", Validation.password("12345"))
    }

    @Test
    fun `requires safety form values`() {
        assertEquals("Contact is required.", Validation.required("Contact", "  "))
    }

    @Test
    fun `validates South African contact numbers`() {
        assertNull(Validation.southAfricanPhone("082 123 4567"))
        assertNull(Validation.southAfricanPhone("+27 (82) 123-4567"))
        assertEquals("Enter a valid South African phone number.", Validation.southAfricanPhone("12345"))
    }
}
