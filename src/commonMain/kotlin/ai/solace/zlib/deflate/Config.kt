package ai.solace.zlib.deflate

// import ai.solace.zlib.common.* // Add if common constants are used by Config, otherwise not needed.

internal class Config(
    internal var good_length: Int,
    internal var max_lazy: Int,
    internal var nice_length: Int,
    internal var max_chain: Int,
    internal var func: Int
)
