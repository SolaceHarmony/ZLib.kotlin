package ai.solace.zlib.bitwise.huffman

import ai.solace.zlib.bitwise.BitwiseOps
import ai.solace.zlib.bitwise.ArithmeticBitwiseOps

/**
 * Utilities for Huffman coding operations
 * Based on patterns from ZLib.kotlin's InfCodes implementation
 * 
 * Provides both standard bitwise operations (for platforms where they work reliably)
 * and arithmetic-only operations (for maximum cross-platform compatibility)
 */
class HuffmanUtils {
    companion object {
        // Arithmetic-only operations for cross-platform compatibility
        private val arithmeticOps = ArithmeticBitwiseOps.BITS_32
        
        /**
         * Extracts a Huffman code from a bit buffer
         * @param bitBuffer The bit buffer containing encoded data
         * @param bitMask Mask for the relevant bits
         * @return The extracted Huffman code
         */
        fun extractCode(bitBuffer: Int, bitMask: Int): Int {
            return bitBuffer and bitMask
        }
        
        /**
         * Extracts a Huffman code from a bit buffer using arithmetic operations
         * @param bitBuffer The bit buffer containing encoded data
         * @param bitMask Mask for the relevant bits
         * @return The extracted Huffman code
         */
        fun extractCodeArithmetic(bitBuffer: Int, bitMask: Int): Int {
            return arithmeticOps.and(bitBuffer.toLong(), bitMask.toLong()).toInt()
        }
        
        /**
         * Consumes bits from a bit buffer after code extraction
         * @param bitBuffer The current bit buffer
         * @param bitsToConsume Number of bits to consume
         * @return The updated bit buffer
         */
        fun consumeBits(bitBuffer: Int, bitsToConsume: Int): Int {
            return bitBuffer ushr bitsToConsume
        }
        
        /**
         * Consumes bits from a bit buffer using arithmetic operations
         * @param bitBuffer The current bit buffer
         * @param bitsToConsume Number of bits to consume
         * @return The updated bit buffer
         */
        fun consumeBitsArithmetic(bitBuffer: Int, bitsToConsume: Int): Int {
            return arithmeticOps.rightShift(bitBuffer.toLong(), bitsToConsume).toInt()
        }
        
        /**
         * Adds bits to a bit buffer from an input byte
         * @param bitBuffer The current bit buffer
         * @param inputByte The byte to add to the buffer
         * @param currentBitCount Current number of bits in the buffer
         * @return The updated bit buffer
         */
        fun addBits(bitBuffer: Int, inputByte: Byte, currentBitCount: Int): Int {
            return bitBuffer or (BitwiseOps.byteToUnsignedInt(inputByte) shl currentBitCount)
        }
        
        /**
         * Adds bits to a bit buffer using arithmetic operations
         * @param bitBuffer The current bit buffer
         * @param inputByte The byte to add to the buffer
         * @param currentBitCount Current number of bits in the buffer
         * @return The updated bit buffer
         */
        fun addBitsArithmetic(bitBuffer: Int, inputByte: Byte, currentBitCount: Int): Int {
            val unsignedByte = BitwiseOps.byteToUnsignedInt(inputByte).toLong()
            val shiftedByte = arithmeticOps.leftShift(unsignedByte, currentBitCount)
            return arithmeticOps.or(bitBuffer.toLong(), shiftedByte).toInt()
        }
        
        /**
         * Checks if a bit is set in a value
         * @param value The value to check
         * @param bitPosition The position of the bit to check (0-based from LSB)
         * @return true if the bit is set, false otherwise
         */
        fun isBitSet(value: Int, bitPosition: Int): Boolean {
            return (value and (1 shl bitPosition)) != 0
        }
        
        /**
         * Checks if a bit is set using arithmetic operations
         * @param value The value to check
         * @param bitPosition The position of the bit to check (0-based from LSB)
         * @return true if the bit is set, false otherwise
         */
        fun isBitSetArithmetic(value: Int, bitPosition: Int): Boolean {
            return arithmeticOps.isBitSet(value.toLong(), bitPosition)
        }
    }
}