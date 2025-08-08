# NEXT_STEPS Checklist

Generated on: 2025-08-07 17:05 (local)

This checklist tracks remaining work after initial cleanup. Check items off as you complete them.

## 1) Repository cleanup
- [x] Move in-source docs to docs/WIP
  - [x] src/commonMain/kotlin/ai/solace/zlib/deflate/ALGORITHM_ANNOTATIONS.md → docs/WIP/
  - [x] src/commonMain/kotlin/ai/solace/zlib/deflate/Analysis of zlib deflate.md → docs/WIP/
  - [x] src/commonMain/kotlin/ai/solace/zlib/README.md → docs/WIP/README.ai.solace.zlib.md (renamed to avoid collision)
- [x] Archive outdated handover notes
  - [x] HANDOVER_NOTES.md → docs/ARCHIVE/
- [x] Move root reference blob
  - [x] static_ltree_csharp.md → docs/REFERENCE/
- [ ] Review remaining stray markdowns and decide whether to move
  - [ ] src/macosArm64Main/README.md (platform-specific readme) → consider docs/PLATFORM (optional)
  - [ ] examples/README.md (keep under examples/ or mirror under docs/examples)

## 2) Testing
- [ ] Run full unit test suite
  - [ ] ./gradlew test (or platform-specific: ./gradlew macosArm64Test)
  - [ ] Capture failing tests and error messages
- [ ] Core inflate tests
  - [ ] ai.solace.zlib.test.InflateTest.basicInflationTest
  - [ ] ai.solace.zlib.test.InflateTest.inflateNoCompressionDataTest
  - [ ] ai.solace.zlib.test.InflateTest.minimalInputDataTest
  - [ ] ai.solace.zlib.test.InflateTest.referenceInflationCompatibilityTest
- [ ] Real-world tests
  - [ ] ai.solace.zlib.test.PigzRealWorldTest.testPigzRealWorldDecompression

## 3) Fixing outstanding issues
- Inflate path correctness
  - [ ] Verify/finish InfCodes.inflateFast distance and copy logic parity with reference
  - [ ] Ensure wrap-around copies cannot go out-of-bounds; maintain invariants on pointers
  - [ ] Eliminate Z_BUF_ERROR on valid streams (ensure buffer sizing and finish handling)
- Huffman trees
  - [ ] Confirm InfTree.huftBuild correctness for all cases (fixed and dynamic)
- Checksum
  - [ ] Re-validate Adler32 consistency across utils and constants
- Compression side (de-prioritized unless tests demand)
  - [ ] Complete pending arithmetic conversions in Deflate.kt (see HANDOVER notes) if needed by tests
- Logging
  - [ ] Gate DEBUG_LOG noise behind a flag or reduce verbosity once tests pass

## 4) Documentation updates
- [ ] Update README.md with current status and cross-platform notes
- [ ] Consolidate docs/WIP/* into clearer guides (Fix Summary, Architecture) as work completes
- [ ] Add short migration note for moved docs (from src/ → docs/)

## Notes
- Primary focus: ensure Inflate tests pass end-to-end using arithmetic-only bitwise ops.
- Use the WIP docs under docs/WIP for deeper background (Inflation bug analysis and fix summary).
