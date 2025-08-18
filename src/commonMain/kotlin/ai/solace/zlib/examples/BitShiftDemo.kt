package ai.solace.zlib.examples

import ai.solace.zlib.bitwise.examples.BitShiftSandbox

/**
 * Simple CLI demonstration of the BitShift Sandbox
 * This can be called to demonstrate the bit shift functionality.
 */
fun main() {
    println("ZLib.kotlin BitShift Sandbox Demonstration")
    println("==========================================")
    
    try {
        BitShiftSandbox.runAllDemonstrations()
        println("\n✅ All demonstrations completed successfully!")
    } catch (e: Exception) {
        println("\n❌ Error during demonstration: ${e.message}")
        e.printStackTrace()
    }
}