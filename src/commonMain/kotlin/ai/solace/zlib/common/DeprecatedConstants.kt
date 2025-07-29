package ai.solace.zlib.common

/**
 * DEPRECATED: This file is maintained for backward compatibility only.
 * All constants have been moved to ConstantsObject.kt.
 * Please use ConstantsObject for accessing constants in new code.
 * 
 * This file simply re-exports the ConstantsObject to maintain backward compatibility.
 * Files that were previously using constants directly should be updated to use
 * ConstantsObject directly.
 */

// Re-export ConstantsObject as Constants for backward compatibility
@Deprecated("Use ConstantsObject directly instead", ReplaceWith("ConstantsObject"))
typealias Constants = ConstantsObject