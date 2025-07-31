/* InfFixed.kt -- table for decoding fixed codes
 * Converted from inffixed.h - Generated automatically by makefixed().
 * 
 * This is a direct conversion of Mark Adler's C implementation from zlib
 */

package ai.solace.zlib.deflate

/**
 * Code structure matching the C implementation:
 * - op: operation code (0=literal, 16=length base, etc.)
 * - bits: number of bits for this code
 * - val: value/symbol
 */
internal data class Code(
    val op: Int,     // operation, extra bits, table bits
    val bits: Int,   // bits in this part of the code  
    val value: Int   // offset in table or code value
)

/**
 * Fixed literal/length table from C inffixed.h
 * Direct conversion of: static const code lenfix[512]
 */
internal val LENFIX = arrayOf(
    Code(96,7,0), Code(0,8,80), Code(0,8,16), Code(20,8,115), Code(18,7,31), Code(0,8,112), Code(0,8,48),
    Code(0,9,192), Code(16,7,10), Code(0,8,96), Code(0,8,32), Code(0,9,160), Code(0,8,0), Code(0,8,128),
    Code(0,8,64), Code(0,9,224), Code(16,7,6), Code(0,8,88), Code(0,8,24), Code(0,9,144), Code(19,7,59),
    Code(0,8,120), Code(0,8,56), Code(0,9,208), Code(17,7,17), Code(0,8,104), Code(0,8,40), Code(0,9,176),
    Code(0,8,8), Code(0,8,136), Code(0,8,72), Code(0,9,240), Code(16,7,4), Code(0,8,84), Code(0,8,20),
    Code(21,8,227), Code(19,7,43), Code(0,8,116), Code(0,8,52), Code(0,9,200), Code(17,7,13), Code(0,8,100),
    Code(0,8,36), Code(0,9,168), Code(0,8,4), Code(0,8,132), Code(0,8,68), Code(0,9,232), Code(16,7,8),
    Code(0,8,92), Code(0,8,28), Code(0,9,152), Code(20,7,83), Code(0,8,124), Code(0,8,60), Code(0,9,216),
    Code(18,7,23), Code(0,8,108), Code(0,8,44), Code(0,9,184), Code(0,8,12), Code(0,8,140), Code(0,8,76),
    Code(0,9,248), Code(16,7,3), Code(0,8,82), Code(0,8,18), Code(21,8,163), Code(19,7,35), Code(0,8,114),
    Code(0,8,50), Code(0,9,196), Code(17,7,11), Code(0,8,98), Code(0,8,34), Code(0,9,164), Code(0,8,2),
    Code(0,8,130), Code(0,8,66), Code(0,9,228), Code(16,7,7), Code(0,8,90), Code(0,8,26), Code(0,9,148),
    Code(20,7,67), Code(0,8,122), Code(0,8,58), Code(0,9,212), Code(18,7,19), Code(0,8,106), Code(0,8,42),
    Code(0,9,180), Code(0,8,10), Code(0,8,138), Code(0,8,74), Code(0,9,244), Code(16,7,5), Code(0,8,86),
    Code(0,8,22), Code(64,8,0), Code(19,7,51), Code(0,8,118), Code(0,8,54), Code(0,9,204), Code(17,7,15),
    Code(0,8,102), Code(0,8,38), Code(0,9,172), Code(0,8,6), Code(0,8,134), Code(0,8,70), Code(0,9,236),
    Code(16,7,9), Code(0,8,94), Code(0,8,30), Code(0,9,156), Code(20,7,99), Code(0,8,126), Code(0,8,62),
    Code(0,9,220), Code(18,7,27), Code(0,8,110), Code(0,8,46), Code(0,9,188), Code(0,8,14), Code(0,8,142),
    Code(0,8,78), Code(0,9,252), Code(96,7,0), Code(0,8,81), Code(0,8,17), Code(21,8,131), Code(18,7,31),
    Code(0,8,113), Code(0,8,49), Code(0,9,194), Code(16,7,10), Code(0,8,97), Code(0,8,33), Code(0,9,162),
    Code(0,8,1), Code(0,8,129), Code(0,8,65), Code(0,9,226), Code(16,7,6), Code(0,8,89), Code(0,8,25),
    Code(0,9,146), Code(19,7,59), Code(0,8,121), Code(0,8,57), Code(0,9,210), Code(17,7,17), Code(0,8,105),
    Code(0,8,41), Code(0,9,178), Code(0,8,9), Code(0,8,137), Code(0,8,73), Code(0,9,242), Code(16,7,4),
    Code(0,8,85), Code(0,8,21), Code(16,8,258), Code(19,7,43), Code(0,8,117), Code(0,8,53), Code(0,9,202),
    Code(17,7,13), Code(0,8,101), Code(0,8,37), Code(0,9,170), Code(0,8,5), Code(0,8,133), Code(0,8,69),
    Code(0,9,234), Code(16,7,8), Code(0,8,93), Code(0,8,29), Code(0,9,154), Code(20,7,83), Code(0,8,125),
    Code(0,8,61), Code(0,9,218), Code(18,7,23), Code(0,8,109), Code(0,8,45), Code(0,9,186), Code(0,8,13),
    Code(0,8,141), Code(0,8,77), Code(0,9,250), Code(16,7,3), Code(0,8,83), Code(0,8,19), Code(21,8,195),
    Code(19,7,35), Code(0,8,115), Code(0,8,51), Code(0,9,198), Code(17,7,11), Code(0,8,99), Code(0,8,35),
    Code(0,9,166), Code(0,8,3), Code(0,8,131), Code(0,8,67), Code(0,9,230), Code(16,7,7), Code(0,8,91),
    Code(0,8,27), Code(0,9,150), Code(20,7,67), Code(0,8,123), Code(0,8,59), Code(0,9,214), Code(18,7,19),
    Code(0,8,107), Code(0,8,43), Code(0,9,182), Code(0,8,11), Code(0,8,139), Code(0,8,75), Code(0,9,246),
    Code(16,7,5), Code(0,8,87), Code(0,8,23), Code(64,8,0), Code(19,7,51), Code(0,8,119), Code(0,8,55),
    Code(0,9,206), Code(17,7,15), Code(0,8,103), Code(0,8,39), Code(0,9,174), Code(0,8,7), Code(0,8,135),
    Code(0,8,71), Code(0,9,238), Code(16,7,9), Code(0,8,95), Code(0,8,31), Code(0,9,158), Code(20,7,99),
    Code(0,8,127), Code(0,8,63), Code(0,9,222), Code(18,7,27), Code(0,8,111), Code(0,8,47), Code(0,9,190),
    Code(0,8,15), Code(0,8,143), Code(0,8,79), Code(0,9,254), Code(96,7,0), Code(0,8,80), Code(0,8,16),
    Code(20,8,115), Code(18,7,31), Code(0,8,112), Code(0,8,48), Code(0,9,193), Code(16,7,10), Code(0,8,96),
    Code(0,8,32), Code(0,9,161), Code(0,8,0), Code(0,8,128), Code(0,8,64), Code(0,9,225), Code(16,7,6),
    Code(0,8,88), Code(0,8,24), Code(0,9,145), Code(19,7,59), Code(0,8,120), Code(0,8,56), Code(0,9,209),
    Code(17,7,17), Code(0,8,104), Code(0,8,40), Code(0,9,177), Code(0,8,8), Code(0,8,136), Code(0,8,72),
    Code(0,9,241), Code(16,7,4), Code(0,8,84), Code(0,8,20), Code(21,8,227), Code(19,7,43), Code(0,8,116),
    Code(0,8,52), Code(0,9,201), Code(17,7,13), Code(0,8,100), Code(0,8,36), Code(0,9,169), Code(0,8,4),
    Code(0,8,132), Code(0,8,68), Code(0,9,233), Code(16,7,8), Code(0,8,92), Code(0,8,28), Code(0,9,153),
    Code(20,7,83), Code(0,8,124), Code(0,8,60), Code(0,9,217), Code(18,7,23), Code(0,8,108), Code(0,8,44),
    Code(0,9,185), Code(0,8,12), Code(0,8,140), Code(0,8,76), Code(0,9,249), Code(16,7,3), Code(0,8,82),
    Code(0,8,18), Code(21,8,163), Code(19,7,35), Code(0,8,114), Code(0,8,50), Code(0,9,197), Code(17,7,11),
    Code(0,8,98), Code(0,8,34), Code(0,9,165), Code(0,8,2), Code(0,8,130), Code(0,8,66), Code(0,9,229),
    Code(16,7,7), Code(0,8,90), Code(0,8,26), Code(0,9,149), Code(20,7,67), Code(0,8,122), Code(0,8,58),
    Code(0,9,213), Code(18,7,19), Code(0,8,106), Code(0,8,42), Code(0,9,181), Code(0,8,10), Code(0,8,138),
    Code(0,8,74), Code(0,9,245), Code(16,7,5), Code(0,8,86), Code(0,8,22), Code(64,8,0), Code(19,7,51),
    Code(0,8,118), Code(0,8,54), Code(0,9,205), Code(17,7,15), Code(0,8,102), Code(0,8,38), Code(0,9,173),
    Code(0,8,6), Code(0,8,134), Code(0,8,70), Code(0,9,237), Code(16,7,9), Code(0,8,94), Code(0,8,30),
    Code(0,9,157), Code(20,7,99), Code(0,8,126), Code(0,8,62), Code(0,9,221), Code(18,7,27), Code(0,8,110),
    Code(0,8,46), Code(0,9,189), Code(0,8,14), Code(0,8,142), Code(0,8,78), Code(0,9,253), Code(96,7,0),
    Code(0,8,81), Code(0,8,17), Code(21,8,131), Code(18,7,31), Code(0,8,113), Code(0,8,49), Code(0,9,195),
    Code(16,7,10), Code(0,8,97), Code(0,8,33), Code(0,9,163), Code(0,8,1), Code(0,8,129), Code(0,8,65),
    Code(0,9,227), Code(16,7,6), Code(0,8,89), Code(0,8,25), Code(0,9,147), Code(19,7,59), Code(0,8,121),
    Code(0,8,57), Code(0,9,211), Code(17,7,17), Code(0,8,105), Code(0,8,41), Code(0,9,179), Code(0,8,9),
    Code(0,8,137), Code(0,8,73), Code(0,9,243), Code(16,7,4), Code(0,8,85), Code(0,8,21), Code(16,8,258),
    Code(19,7,43), Code(0,8,117), Code(0,8,53), Code(0,9,203), Code(17,7,13), Code(0,8,101), Code(0,8,37),
    Code(0,9,171), Code(0,8,5), Code(0,8,133), Code(0,8,69), Code(0,9,235), Code(16,7,8), Code(0,8,93),
    Code(0,8,29), Code(0,9,155), Code(20,7,83), Code(0,8,125), Code(0,8,61), Code(0,9,219), Code(18,7,23),
    Code(0,8,109), Code(0,8,45), Code(0,9,187), Code(0,8,13), Code(0,8,141), Code(0,8,77), Code(0,9,251),
    Code(16,7,3), Code(0,8,83), Code(0,8,19), Code(21,8,195), Code(19,7,35), Code(0,8,115), Code(0,8,51),
    Code(0,9,199), Code(17,7,11), Code(0,8,99), Code(0,8,35), Code(0,9,167), Code(0,8,3), Code(0,8,131),
    Code(0,8,67), Code(0,9,231), Code(16,7,7), Code(0,8,91), Code(0,8,27), Code(0,9,151), Code(20,7,67),
    Code(0,8,123), Code(0,8,59), Code(0,9,215), Code(18,7,19), Code(0,8,107), Code(0,8,43), Code(0,9,183),
    Code(0,8,11), Code(0,8,139), Code(0,8,75), Code(0,9,247), Code(16,7,5), Code(0,8,87), Code(0,8,23),
    Code(64,8,0), Code(19,7,51), Code(0,8,119), Code(0,8,55), Code(0,9,207), Code(17,7,15), Code(0,8,103),
    Code(0,8,39), Code(0,9,175), Code(0,8,7), Code(0,8,135), Code(0,8,71), Code(0,9,239), Code(16,7,9),
    Code(0,8,95), Code(0,8,31), Code(0,9,159), Code(20,7,99), Code(0,8,127), Code(0,8,63), Code(0,9,223),
    Code(18,7,27), Code(0,8,111), Code(0,8,47), Code(0,9,191), Code(0,8,15), Code(0,8,143), Code(0,8,79),
    Code(0,9,255)
)

/**
 * Fixed distance table from C inffixed.h  
 * Direct conversion of: static const code distfix[32]
 */
internal val DISTFIX = arrayOf(
    Code(16,5,1), Code(23,5,257), Code(19,5,17), Code(27,5,4097), Code(17,5,5), Code(25,5,1025),
    Code(21,5,65), Code(29,5,16385), Code(16,5,3), Code(24,5,513), Code(20,5,33), Code(28,5,8193),
    Code(18,5,9), Code(26,5,2049), Code(22,5,129), Code(64,5,0), Code(16,5,2), Code(23,5,385),
    Code(19,5,25), Code(27,5,6145), Code(17,5,7), Code(25,5,1537), Code(21,5,97), Code(29,5,24577),
    Code(16,5,4), Code(24,5,769), Code(20,5,49), Code(28,5,12289), Code(18,5,13), Code(26,5,3073),
    Code(22,5,193), Code(64,5,0)
)
