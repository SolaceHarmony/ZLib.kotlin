# ZLib.kotlin ZIP CLI Tool

This is a simple command-line tool for compressing and decompressing files using the ZLib.kotlin library.

## Building the Tool

The ZIP CLI tool is built automatically when you build the project. It will be built for all supported platforms:

- macOS (ARM64)
- Linux (x64)
- Linux (ARM64)

To build the project, run:

```bash
./gradlew build
```

The executables will be located in the following directories:

- macOS: `build/bin/macos/releaseExecutable/zlib-cli.kexe`
- Linux: `build/bin/linux/releaseExecutable/zlib-cli.kexe`
- Linux ARM: `build/bin/linuxArm/releaseExecutable/zlib-cli.kexe`

## Usage

### Compressing a File

```bash
./zlib-cli compress <input-file> <output-file> [compression-level]
```

or using the short command:

```bash
./zlib-cli c <input-file> <output-file> [compression-level]
```

Where:
- `<input-file>` is the path to the file you want to compress
- `<output-file>` is the path where the compressed file will be saved
- `[compression-level]` is an optional compression level (0-9):
  - 0: No compression
  - 1: Best speed
  - 6: Default compression
  - 9: Best compression

### Decompressing a File

```bash
./zlib-cli decompress <input-file> <output-file>
```

or using the short command:

```bash
./zlib-cli d <input-file> <output-file>
```

Where:
- `<input-file>` is the path to the compressed file
- `<output-file>` is the path where the decompressed file will be saved

## Examples

### Compress a File with Default Compression

```bash
./zlib-cli compress myfile.txt myfile.txt.z
```

### Compress a File with Maximum Compression

```bash
./zlib-cli compress myfile.txt myfile.txt.z 9
```

### Decompress a File

```bash
./zlib-cli decompress myfile.txt.z myfile.txt
```

## Notes

- This tool uses the ZLib compression algorithm, which is a widely used data compression library.
- The compressed files are not in ZIP format, but in the raw ZLib format.
- For large files, the tool may require significant memory.