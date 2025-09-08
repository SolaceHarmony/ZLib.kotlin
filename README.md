# ZLib.kotlin

[![License: Apache-2.0](https://img.shields.io/badge/license-Apache_2.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-Native-blue.svg)](https://kotlinlang.org/)
[![Build](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Platform](https://img.shields.io/badge/platform-Kotlin%2FNative-orange.svg)]()

---

**ZLib.kotlin** is a clean-room, RFC-based implementation of the zlib format (RFC 1950) and DEFLATE (RFC 1951) in pure Kotlin Multiplatform. It is not a direct port of zlib or any C# project. The original zlib and certain .NET ports served as useful references; see Credits.

---

## Table of Contents

- [About](#about)
- [Features](#features)
- [Installation](#installation)
- [Usage](#usage)
- [Documentation](#documentation)
- [Compatibility](#compatibility)
- [Testing](#testing)
- [Credits & References](#credits--references)
- [License](#license)
- [Contributing](#contributing)
- [Contact](#contact)

---

## About

ZLib.kotlin is part of **The Solace Project** special libraries, designed to offer reliable, efficient compression for Kotlin Native targets. It is a complete rewrite in pure Kotlin, ensuring safety, maintainability, and native performance—without JNI, C interop, or external dependencies.

This library is a clean-room rewrite from the RFCs (RFC 1950 and RFC 1951). It is not a direct port of zlib or any C# project. The .NET implementations (zlib.net and zlib.managed) served as useful references.

---

## Features

- **Pure Kotlin/Native implementation:** No C/C++ code or JNI required.
- **Multiplatform support:** Kotlin Native for Linux and macOS targets.
- **Familiar API:** Designed to be comfortable for users familiar with zlib; .NET ports (zlib.net, zlib.managed) served as useful references.
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

val data: ByteArray = "Hello, ZLib.kotlin!".encodeToByteArray()

// Compress
val compressed: ByteArray = ZLib.compress(data)

// Decompress
val decompressed: ByteArray = ZLib.decompress(compressed)

println(decompressed.decodeToString()) // Output: Hello, ZLib.kotlin!
```

For more advanced usage, streaming, or custom options, see the detailed [API documentation](./docs/API.md) and practical [examples](./examples/).

---

## Documentation

### 📚 [API Reference](docs/API.md)
Comprehensive API documentation covering:
- Core classes (`ZStream`, `ZInputStream`, `ZStreamException`)
- Compression and decompression methods
- Configuration options and constants
- Advanced usage patterns
- Error handling and performance tips

### 💡 Examples
Practical examples demonstrating:
- **Basic compression/decompression** - Simple operations with error handling
- **Advanced techniques** - Performance comparison, streaming, custom parameters
- **Best practices** - Memory management, buffer sizing, optimization tips

---

## Compatibility

- **Kotlin/Native:** Linux, macOS

Tested with IntelliJ IDEA and Gradle.

---

## Testing

Run the multiplatform tests using Gradle. For example, to execute the Linux x64
tests run:

```bash
./gradlew linuxX64Test
```

Native targets have their own `<target>Test` task (e.g., `./gradlew macosArm64Test`). 
macOS target tasks may be disabled when running on a non-macOS host.

---

## Credits & References

- zlib (C) Jean-loup Gailly and Mark Adler — Original C implementation inspiring the format and algorithms
- .NET implementations used as references: [zlib.net](http://www.componentace.com/zlib_.NET.htm), [zlib.managed](https://github.com/philippelatulippe/ZLIB.NET)
- This project: Kotlin Multiplatform implementation by Sydney Bach / The Solace Project

Special thanks to all contributors and maintainers of the original and derivative implementations.

---

## License

This project is licensed under the [Apache License 2.0](LICENSE). See [NOTICE](NOTICE) for attribution and third-party notices.

### Attribution

This library is an original Kotlin Multiplatform implementation maintained by Sydney Bach / The Solace Project.
It is inspired by and acknowledges the original zlib work by Jean‑loup Gailly and Mark Adler. Any remaining
references to legacy code paths were removed during the rewrite; the streaming inflate presented here is a
clean, Kotlin‑native implementation that adheres to the zlib format (RFC 1950) and DEFLATE specification (RFC 1951).

---

## Contributing

Contributions, bug reports, and pull requests are welcome!

- Fork the repo and create your branch.
- Submit a PR with a clear description.
---

## Contact

Maintained by [The Solace Project](https://github.com/SolaceHarmony/).

For questions or support, please [open an issue](https://github.com/SolaceHarmony/ZLib.kotlin/issues) or contact us at [support@solaceharmony.dev](mailto:support@solaceharmony.dev).

---

> Acknowledgment: We recognize the original zlib by Jean‑loup Gailly and Mark Adler, and note that .NET implementations (zlib.net, zlib.managed) were consulted as references during development.
