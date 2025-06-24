package ai.solace.zlib.deflate

// import ai.solace.zlib.common.* // Add if common constants are used by Config, otherwise not needed.

internal class Config(
    internal var goodLength: Int,
    internal var maxLazy: Int,
    internal var niceLength: Int,
    internal var maxChain: Int,
    internal var func: Int
)
