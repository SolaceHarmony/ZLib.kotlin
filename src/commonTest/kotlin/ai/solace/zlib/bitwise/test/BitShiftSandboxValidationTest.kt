package ai.solace.zlib.bitwise.test

import ai.solace.zlib.bitwise.examples.BitShiftSandbox
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Test to validate that the BitShift sandbox runs without errors
 */
class BitShiftSandboxValidationTest {
    
    @Test
    fun testSandboxRunsWithoutErrors() {
        // This test validates that all sandbox demonstrations can run
        // without throwing exceptions
        
        var executedSuccessfully = false
        
        try {
            BitShiftSandbox.demonstrateBasicOperations()
            BitShiftSandbox.demonstrateCarryAndOverflow()
            BitShiftSandbox.demonstrateAdler32Integration()
            BitShiftSandbox.demonstrateCrossPlatformConsistency()
            BitShiftSandbox.demonstrateLegacyPatterns()
            executedSuccessfully = true
        } catch (e: Exception) {
            // Log the exception for debugging
            println("Sandbox failed with exception: ${e.message}")
            throw e
        }
        
        assertTrue(executedSuccessfully, "Sandbox demonstrations should execute without errors")
    }
}