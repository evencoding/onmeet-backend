package com.onmeet.auth.dto

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChangePasswordRequestTest {

    private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `should fail when password does not meet complexity requirements`() {
        // Missing special char
        val result1 = validator.validate(ChangePasswordRequest("old", "Password123"))
        assertEquals(1, result1.size)
        assertTrue(result1.first().message.contains("special character") || result1.first().message.contains("New password is not valid"))

        // Missing number
        val result2 = validator.validate(ChangePasswordRequest("old", "Password!"))
        assertEquals(1, result2.size)
        
        // No letter
        val result3 = validator.validate(ChangePasswordRequest("old", "12345678!"))
        assertEquals(1, result3.size)
       
        // Too short
        val result4 = validator.validate(ChangePasswordRequest("old", "Pas1!"))
        assertTrue(result4.any { it.message.contains("at least 8 characters") })
    }

    @Test
    fun `should pass when password meets requirements`() {
        // Fails until implemented
        val request = ChangePasswordRequest("old", "Password123!")
        val violations = validator.validate(request)
        assertTrue(violations.isEmpty())
    }
}
