fun main() {
    val bitBuffer = 0b10110101 // 181 in decimal
    println("Original: $bitBuffer (binary: ${bitBuffer.toString(2)})")
    
    // Test right shift vs division
    val shiftResult = bitBuffer ushr 1
    val divResult = bitBuffer / 2
    println("Right shift by 1: $shiftResult (binary: ${shiftResult.toString(2)})")
    println("Division by 2: $divResult (binary: ${divResult.toString(2)})")
    println("Match: ${shiftResult == divResult}")
    
    // Test with different amounts
    for (i in 1..3) {
        val shift = bitBuffer ushr i
        val power = 1 shl i
        val div = bitBuffer / power
        println("Shift by $i: $shift, Div by $power: $div, Match: ${shift == div}")
    }
}
