package ai.solace.zlib.bitwise.examples

import ai.solace.zlib.bitwise.ArithmeticBitwiseOps

/**
 * Example showing how to use ArithmeticBitwiseOps for porting legacy programs
 * from different architectures (8-bit, 16-bit, 32-bit) to Kotlin Multiplatform
 */
class LegacyPortingExamples {
    
    /**
     * Example: Porting an 8-bit microcontroller program
     * Common in embedded systems, Arduino, etc.
     */
    fun example8BitMicrocontroller() {
        val ops = ArithmeticBitwiseOps.BITS_8
        
        // Simulating 8-bit register operations
        var statusRegister = 0L
        
        // Set status flags (common in 8-bit systems)
        statusRegister = ops.or(statusRegister, 0x01L) // Set carry flag
        statusRegister = ops.or(statusRegister, 0x80L) // Set negative flag
        
        // Check specific flags
        val carrySet = ops.isBitSet(statusRegister, 0)
        val zeroSet = ops.isBitSet(statusRegister, 1)
        val negativeSet = ops.isBitSet(statusRegister, 7)
        
        println("8-bit Status Register: 0x${statusRegister.toString(16)}")
        println("Carry: $carrySet, Zero: $zeroSet, Negative: $negativeSet")
        
        // Arithmetic with overflow behavior matching 8-bit systems
        var counter = 255L
        counter = ops.normalize(counter + 1) // Should wrap to 0
        println("8-bit counter after overflow: $counter")
    }
    
    /**
     * Example: Porting a 16-bit DOS/Windows 3.1 program
     * Common in legacy PC applications
     */
    fun example16BitDosProgram() {
        val ops = ArithmeticBitwiseOps.BITS_16
        
        // Simulating 16-bit segmented memory addressing
        val segment = 0x1000L
        val offset = 0x0200L
        
        // Combine segment:offset addressing (segment << 4 + offset)
        val physicalAddress = ops.normalize((segment * 16) + offset)
        
        // 16-bit arithmetic operations
        val value1 = 0x8000L // Large 16-bit value
        val value2 = 0x8000L
        val sum = ops.normalize(value1 + value2) // Should wrap around
        
        println("16-bit Physical Address: 0x${physicalAddress.toString(16)}")
        println("16-bit arithmetic: 0x8000 + 0x8000 = 0x${sum.toString(16)}")
        
        // Bit manipulation for 16-bit flags
        var dosFlags = 0L
        dosFlags = ops.or(dosFlags, 0x0001L) // Carry flag
        dosFlags = ops.or(dosFlags, 0x0040L) // Zero flag
        dosFlags = ops.or(dosFlags, 0x0200L) // Interrupt enable
        
        println("DOS Flags Register: 0x${dosFlags.toString(16)}")
    }
    
    /**
     * Example: Porting a 32-bit legacy application
     * Common in older game engines, compression algorithms, etc.
     */
    fun example32BitLegacyApp() {
        val ops = ArithmeticBitwiseOps.BITS_32
        
        // Simulating hash function from legacy codebase
        fun legacyHash(data: ByteArray): Long {
            var hash = 0x12345678L
            
            for (byte in data) {
                // Convert signed byte to unsigned
                val unsignedByte = (byte.toLong() and 0xFFL)
                
                // Legacy hash algorithm using arithmetic operations
                hash = ops.xor(hash, unsignedByte)
                hash = ops.rotateLeft(hash, 7)
                hash = ops.xor(hash, 0xABCDEF00L)
            }
            
            return hash
        }
        
        val testData = "Hello, World!".encodeToByteArray()
        val hashResult = legacyHash(testData)
        println("Legacy 32-bit hash: 0x${hashResult.toString(16)}")
        
        // Simulating color manipulation (common in graphics programming)
        val red = 0xFFL
        val green = 0x80L
        val blue = 0x40L
        val alpha = 0xFFL
        
        // Pack RGBA into 32-bit value
        var color = ops.leftShift(alpha, 24)
        color = ops.or(color, ops.leftShift(red, 16))
        color = ops.or(color, ops.leftShift(green, 8))
        color = ops.or(color, blue)
        
        println("32-bit ARGB color: 0x${color.toString(16)}")
        
        // Extract components back
        val extractedAlpha = ops.rightShift(color, 24)
        val extractedRed = ops.and(ops.rightShift(color, 16), 0xFFL)
        val extractedGreen = ops.and(ops.rightShift(color, 8), 0xFFL)
        val extractedBlue = ops.and(color, 0xFFL)
        
        println("Extracted ARGB: A=$extractedAlpha, R=$extractedRed, G=$extractedGreen, B=$extractedBlue")
    }
    
    /**
     * Example: Porting CRC calculation with specific bit width
     */
    fun exampleCrcCalculation() {
        val ops16 = ArithmeticBitwiseOps.BITS_16
        
        fun crc16(data: ByteArray, polynomial: Long = 0x8005L): Long {
            var crc = 0L
            
            for (byte in data) {
                val unsignedByte = (byte.toLong() and 0xFFL)
                crc = ops16.xor(crc, ops16.leftShift(unsignedByte, 8))
                
                repeat(8) {
                    if (ops16.isBitSet(crc, 15)) {
                        crc = ops16.xor(ops16.leftShift(crc, 1), polynomial)
                    } else {
                        crc = ops16.leftShift(crc, 1)
                    }
                }
            }
            
            return crc
        }
        
        val testData = "123456789".encodeToByteArray()
        val crcResult = crc16(testData)
        println("CRC-16 result: 0x${crcResult.toString(16)}")
    }
    
    /**
     * Example: Bit field manipulation (common in hardware interfacing)
     */
    fun exampleBitFields() {
        val ops = ArithmeticBitwiseOps.BITS_16
        
        // Simulating hardware register with bit fields
        var controlRegister = 0L
        
        // Set specific bit fields
        // Bits 0-2: Mode (3 bits)
        val mode = 5L // 101 binary
        controlRegister = ops.or(controlRegister, ops.and(mode, 0x07L))
        
        // Bits 3-5: Speed (3 bits)  
        val speed = 3L // 011 binary
        controlRegister = ops.or(controlRegister, ops.leftShift(ops.and(speed, 0x07L), 3))
        
        // Bit 6: Enable flag
        controlRegister = ops.or(controlRegister, ops.leftShift(1L, 6))
        
        // Bits 8-15: ID (8 bits)
        val deviceId = 0xABL
        controlRegister = ops.or(controlRegister, ops.leftShift(deviceId, 8))
        
        println("Control Register: 0x${controlRegister.toString(16)}")
        
        // Extract bit fields back
        val extractedMode = ops.and(controlRegister, 0x07L)
        val extractedSpeed = ops.and(ops.rightShift(controlRegister, 3), 0x07L)
        val enableFlag = ops.isBitSet(controlRegister, 6)
        val extractedId = ops.rightShift(controlRegister, 8)
        
        println("Extracted - Mode: $extractedMode, Speed: $extractedSpeed, Enable: $enableFlag, ID: 0x${extractedId.toString(16)}")
    }
}

/**
 * Demonstration of all examples
 */
fun main() {
    val examples = LegacyPortingExamples()
    
    println("=== 8-bit Microcontroller Example ===")
    examples.example8BitMicrocontroller()
    
    println("\n=== 16-bit DOS Program Example ===")
    examples.example16BitDosProgram()
    
    println("\n=== 32-bit Legacy Application Example ===")
    examples.example32BitLegacyApp()
    
    println("\n=== CRC Calculation Example ===")
    examples.exampleCrcCalculation()
    
    println("\n=== Bit Fields Example ===")
    examples.exampleBitFields()
}
