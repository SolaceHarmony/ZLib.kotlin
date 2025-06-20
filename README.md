# ZLib.kotlin

[![License: Zlib](https://img.shields.io/badge/license-Zlib-lightgrey.svg)](https://opensource.org/licenses/Zlib)
[![Kotlin](https://img.shields.io/badge/Kotlin-Native-blue.svg)](https://kotlinlang.org/)
[![Build](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Platform](https://img.shields.io/badge/platform-Kotlin%2FNative%20&%20JVM-orange.svg)]()

---

**ZLib.kotlin** is a pure Kotlin Native port of the legendary ZLib compression library, tracing its lineage from the C#/.NET project [zlib.managed](https://github.com/philippelatulippe/ZLIB.NET), itself a fork of the original [zlib.net](http://www.componentace.com/zlib_.NET.htm). This library brings high-performance, cross-platform compression and decompression capabilities to the Kotlin ecosystem, embracing multiplatform development with modern tooling.

---

## Table of Contents

- [About](#about)
- [Features](#features)
- [Installation](#installation)
- [Usage](#usage)
- [Compatibility](#compatibility)
- [Credits & Lineage](#credits--lineage)
- [License](#license)
- [Contributing](#contributing)
- [Contact](#contact)

---

## About

ZLib.kotlin is part of **The Solace Project** special libraries, designed to offer reliable, efficient compression for Kotlin Native, Kotlin/JVM, and other supported targets. It is a complete rewrite in pure Kotlin, ensuring safety, maintainability, and native performance—without JNI, C interop, or external dependencies.

This library was originally ported from the C#/.NET [zlib.managed](https://github.com/philippelatulippe/ZLIB.NET) project (a fork of zlib.net by ComponentAce), which itself is based on the seminal C library written by Jean-loup Gailly and Mark Adler.

---

## Features

- **Pure Kotlin/Native implementation:** No C/C++ code or JNI required.
- **Multiplatform support:** Kotlin Native, JVM, and other Kotlin targets.
- **API compatible:** Designed to be familiar to users of zlib, zlib.net, or zlib.managed.
- **Fast and lightweight:** Efficient, battle-tested algorithms for compression and decompression.
- **Zero dependencies:** No need for external libraries or system zlib installs.
- **Actively maintained:** Open to improvements and contributions.

---

## Installation

Add ZLib.kotlin to your project with Gradle:

```kotlin
dependencies {
    implementation("com.solaceharmony:zlib.kotlin:<latest-version>")
}
```

Or, for multiplatform projects:

```kotlin
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation("com.solaceharmony:zlib.kotlin:<latest-version>")
            }
        }
    }
}
```

> **Note:** Replace `<latest-version>` with the latest published version on Maven Central or your chosen repository.

---

## Usage

Here's a simple example of compressing and decompressing a `ByteArray`:

```kotlin
import com.solaceharmony.zlib.kotlin.ZLib

val data: ByteArray = "Hello, ZLib.kotlin!".encodeToByteArray()

// Compress
val compressed: ByteArray = ZLib.compress(data)

// Decompress
val decompressed: ByteArray = ZLib.decompress(compressed)

println(decompressed.decodeToString()) // Output: Hello, ZLib.kotlin!
```

For more advanced usage, streaming, or custom options, see the [API documentation](docs/API.md) or [examples](examples/).

---

## Compatibility

- **Kotlin/Native:** Windows, Linux, MacOS
- **Kotlin/JVM:** 8+
- **Kotlin Multiplatform:** Experimental support for JS and other targets

Tested with IntelliJ IDEA and Gradle.

---

## Credits & Lineage

- **Original C implementation:** [zlib](http://www.zlib.org) by Jean-loup Gailly and Mark Adler
- **.NET port:** [zlib.net](http://www.componentace.com/zlib_.NET.htm) and [zlib.managed](https://github.com/philippelatulippe/ZLIB.NET)
- **Kotlin port:** This project, as part of [The Solace Project](https://github.com/SolaceHarmony/)

Special thanks to all contributors and maintainers of the original and forked implementations.

---

## License

This project is released under the [zlib License](LICENSE), a permissive open source license.

See [`LICENSE`](LICENSE) for details.

---

## Contributing

Contributions, bug reports, and pull requests are welcome!

- Fork the repo and create your branch.
- Submit a PR with a clear description.
- Review the [CONTRIBUTING](CONTRIBUTING.md) guidelines.

---

## Contact

Maintained by [The Solace Project](https://github.com/SolaceHarmony/).

For questions or support, please [open an issue](https://github.com/SolaceHarmony/ZLib.kotlin/issues) or contact us at [support@solaceharmony.dev](mailto:support@solaceharmony.dev).

---

> **Based on zlib.net and zlib-1.1.3. Credits to Jean-loup Gailly, Mark Adler, and all original contributors, as well as ComponentAce for zlib.net.**
```
