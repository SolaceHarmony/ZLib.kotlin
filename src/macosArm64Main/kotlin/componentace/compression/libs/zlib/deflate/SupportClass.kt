package componentace.compression.libs.zlib.deflate

import ai.solace.core.kognitive.compute.fastmm.parser.NpzFile.Reader

@ExperimentalUnsignedTypes




class SupportClass {
    /// <summary>
    /// This method returns the literal value received
    /// </summary>
    /// <param name="literal">The literal to return</param>
    /// <returns>The received value</returns>
    companion object {
        fun Identity(literal: Long): Long {
            return literal
        }

        /// <summary>
        /// This method returns the literal value received
        /// </summary>
        /// <param name="literal">The literal to return</param>
        /// <returns>The received value</returns>
        fun Identity(literal: CustomULong): CustomULong {
            return literal
        }

        /// <summary>
        /// This method returns the literal value received
        /// </summary>
        /// <param name="literal">The literal to return</param>
        /// <returns>The received value</returns>
        fun Identity(literal: Float): Float {
            return literal
        }

        /// <summary>
        /// This method returns the literal value received
        /// </summary>
        /// <param name="literal">The literal to return</param>
        /// <returns>The received value</returns>
        fun Identity(literal: Double): Double {
            return literal
        }

        /*******************************/
        /// <summary>
        /// Performs an unsigned bitwise right shift with the specified number
        /// </summary>
        /// <param name="number">Number to operate on</param>
        /// <param name="bits">Amount of bits to shift</param>
        /// <returns>The resulting number from the shift operation</returns>
        fun URShift(number: Int, bits: Int): Int {
            return if (number >= 0) number shr bits else (number shr bits) + (2 shl bits.inv())
        }

        /// <summary>
        /// Performs an unsigned bitwise right shift with the specified number
        /// </summary>
        /// <param name="number">Number to operate on</param>
        /// <param name="bits">Amount of bits to shift</param>
        /// <returns>The resulting number from the shift operation</returns>
        fun URShift(number: Int, bits: Long): Int {
            return URShift(number, bits.toInt())
        }

        /// <summary>
        /// Performs an unsigned bitwise right shift with the specified number
        /// </summary>
        /// <param name="number">Number to operate on</param>
        /// <param name="bits">Amount of bits to shift</param>
        /// <returns>The resulting number from the shift operation</returns>
        fun URShift(number: Long, bits: Int): Long {
            return if (number >= 0) number shr bits else (number shr bits) + (2L shl bits.inv())
        }

        /// <summary>
        /// Performs an unsigned bitwise right shift with the specified number
        /// </summary>
        /// <param name="number">Number to operate on</param>
        /// <param name="bits">Amount of bits to shift</param>
        /// <returns>The resulting number from the shift operation</returns>
        fun URShift(number: Long, bits: Long): Long {
            return URShift(number, bits.toInt())
        }

        /*******************************/
        /// <summary>Reads a number of characters from the current source Stream and writes the data to the target array at the specified index.</summary>
        /// <param name="sourceStream">The source Stream to read from.</param>
        /// <param name="target">Contains the array of characteres read from the source Stream.</param>
        /// <param name="start">The starting index of the target array.</param>
        /// <param name="count">The maximum number of characters to read from the source Stream.</param>
        /// <returns>The number of characters read. The number will be less than or equal to count depending on the data available in the source Stream. Returns -1 if the end of the stream is reached.</returns>
        fun ReadInput(sourceStream: InputStream, target: ByteArray, start: Int, count: Int): Int {
            // Returns 0 bytes if not enough space in target
            if (target.isEmpty()) return 0

            val receiver = ByteArray(target.size)
            val bytesRead = sourceStream.read(receiver, start, count)

            // Returns -1 if EOF
            if (bytesRead == 0) return -1

            for (i in start until start + bytesRead) target[i] = receiver[i]

            return bytesRead
        }

        /// <summary>Reads a number of characters from the current source TextReader and writes the data to the target array at the specified index.</summary>
        /// <param name="sourceTextReader">The source TextReader to read from</param>
        /// <param name="target">Contains the array of characteres read from the source TextReader.</param>
        /// <param name="start">The starting index of the target array.</param>
        /// <param name="count">The maximum number of characters to read from the source TextReader.</param>
        /// <returns>The number of characters read. The number will be less than or equal to count depending on the data available in the source TextReader. Returns -1 if the end of the stream is reached.</returns>
        fun ReadInput(sourceTextReader: Reader, target: ByteArray, start: Int, count: Int): Int {
            // Returns 0 bytes if not enough space in target
            if (target.isEmpty()) return 0

            val charArray = CharArray(target.size)
            val bytesRead = sourceTextReader.read(charArray, start, count)

            // Returns -1 if EOF
            if (bytesRead == 0) return -1

            for (index in start until start + bytesRead) target[index] = charArray[index].code.toByte()

            return bytesRead
        }

        /// <summary>
        /// Converts a string to an array of bytes
        /// </summary>
        /// <param name="sourceString">The string to be converted</param>
        /// <returns>The new array of bytes</returns>
        fun ToByteArray(sourceString: String): ByteArray {
            return sourceString.toByteArray(UTF_8)
        }

        /// <summary>
        /// Converts an array of bytes to an array of chars
        /// </summary>
        /// <param name="byteArray">The array of bytes to convert</param>
        /// <returns>The new array of chars</returns>
        fun ToCharArray(byteArray: ByteArray): CharArray {
            return byteArray.toString(UTF_8).toCharArray()
        }
    }
}
class CustomULong
class CustomULong : UInt64
