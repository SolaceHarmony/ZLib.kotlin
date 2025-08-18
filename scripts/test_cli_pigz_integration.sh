#!/bin/bash

# ZLib.kotlin CLI and pigz Integration Test Script
# This script provides comprehensive testing of the ZLib.kotlin CLI tool
# with pigz -z option to verify correct compression/decompression behavior.

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test configuration
TEST_DIR="/tmp/zlib_cli_pigz_tests"
CLI_EXECUTABLE="./build/bin/linux/debugExecutable/zlib-cli.kexe"
PIGZ_EXECUTABLE="pigz"

# Test counters
TESTS_TOTAL=0
TESTS_PASSED=0
TESTS_FAILED=0

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
    TESTS_PASSED=$((TESTS_PASSED + 1))
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
    TESTS_FAILED=$((TESTS_FAILED + 1))
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_test() {
    echo -e "${BLUE}[TEST $((TESTS_TOTAL + 1))]${NC} $1"
    TESTS_TOTAL=$((TESTS_TOTAL + 1))
}

# Function to create test files with different characteristics
create_test_files() {
    log_info "Creating test files..."
    
    # Small text file
    echo "Hello World! This is a small test file for ZLib compression." > "$TEST_DIR/small.txt"
    
    # Medium text file with repetitive content (good for compression)
    {
        for i in {1..100}; do
            echo "Line $i: This is repetitive content that should compress well with zlib format."
        done
    } > "$TEST_DIR/medium_repetitive.txt"
    
    # Medium text file with diverse content
    {
        echo "ZLib.kotlin CLI Testing"
        echo "======================="
        echo ""
        echo "This file contains diverse text content to test compression efficiency."
        echo "It includes special characters: !@#$%^&*()_+-=[]{}|;:,.<>?"
        echo "And some numbers: 1234567890"
        echo ""
        for i in {1..50}; do
            echo "Diverse line $i with random content $(date +%N)"
        done
    } > "$TEST_DIR/medium_diverse.txt"
    
    # Large file with mixed content
    {
        echo "Large Test File for ZLib.kotlin"
        echo "==============================="
        echo ""
        for i in {1..1000}; do
            if [ $((i % 3)) -eq 0 ]; then
                echo "Repetitive content line $i: AAAAAABBBBBBCCCCCCDDDDDD"
            elif [ $((i % 3)) -eq 1 ]; then
                echo "Mixed content line $i: $(head -c 32 /dev/urandom | xxd -p | tr -d '\n')"
            else
                echo "Standard line $i: The quick brown fox jumps over the lazy dog."
            fi
        done
    } > "$TEST_DIR/large_mixed.txt"
    
    # Binary-like content (less compressible)
    head -c 1024 /dev/urandom > "$TEST_DIR/binary.bin"
    if [ $? -ne 0 ] || [ ! -s "$TEST_DIR/binary.bin" ]; then
        log_error "Failed to create binary file with /dev/urandom. Skipping binary.bin test file."
        # Optionally, remove the empty file if it exists
        [ -f "$TEST_DIR/binary.bin" ] && rm -f "$TEST_DIR/binary.bin"
    fi
    
    log_info "Test files created:"
    ls -la "$TEST_DIR"/*.txt "$TEST_DIR"/*.bin
}

# Function to verify pigz and CLI executables exist
check_prerequisites() {
    log_info "Checking prerequisites..."
    
    # Check pigz
    if ! command -v "$PIGZ_EXECUTABLE" &> /dev/null; then
        log_error "pigz is not installed or not in PATH"
        return 1
    else
        log_success "pigz found: $(which $PIGZ_EXECUTABLE)"
        log_info "pigz version: $($PIGZ_EXECUTABLE --version 2>&1 | head -1)"
    fi
    
    # Check CLI executable
    if [ ! -f "$CLI_EXECUTABLE" ]; then
        log_warning "CLI executable not found at $CLI_EXECUTABLE"
        log_warning "You need to build it first with: ./gradlew linkDebugExecutableLinuxX64"
        log_warning "Continuing with pigz-only tests..."
        return 2
    else
        log_success "CLI executable found: $CLI_EXECUTABLE"
    fi
    
    return 0
}

# Test 1: Basic pigz -z compression and verification
test_pigz_basic() {
    log_test "Basic pigz -z compression"
    
    local input="$TEST_DIR/small.txt"
    local compressed="$TEST_DIR/small.txt.zz"
    local decompressed="$TEST_DIR/small_decompressed.txt"
    
    # Compress with pigz -z
    if $PIGZ_EXECUTABLE -z -c "$input" > "$compressed"; then
        log_info "Compressed $input with pigz -z"
        
        # Verify it's zlib format (should start with 78)
        local header=$(xxd -p -l 1 "$compressed")
        if [[ $header == "78" ]]; then
            log_info "Verified zlib header (78xx)"
            
            # Decompress with pigz -d
            if $PIGZ_EXECUTABLE -d -c "$compressed" > "$decompressed" 2>/dev/null; then
                # Compare files
                if cmp -s "$input" "$decompressed"; then
                    log_success "pigz -z compression/decompression cycle successful"
                else
                    log_error "Decompressed file doesn't match original"
                fi
            else
                log_error "Failed to decompress with pigz -d"
            fi
        else
            log_error "Compressed file doesn't have zlib header"
        fi
    else
        log_error "Failed to compress with pigz -z"
    fi
}

# Test 2: CLI decompression of pigz -z compressed files
test_cli_decompress_pigz() {
    log_test "CLI decompression of pigz -z compressed files"
    
    if [ ! -f "$CLI_EXECUTABLE" ]; then
        log_warning "Skipping CLI test - executable not available"
        return
    fi
    
    local input="$TEST_DIR/medium_repetitive.txt"
    local compressed="$TEST_DIR/medium_repetitive.txt.zz"
    local cli_decompressed="$TEST_DIR/medium_repetitive_cli.txt"
    
    # Compress with pigz -z
    if $PIGZ_EXECUTABLE -z -c "$input" > "$compressed"; then
        log_info "Compressed with pigz -z"
        
        # Decompress with CLI
        if "$CLI_EXECUTABLE" decompress "$compressed" "$cli_decompressed" 2>/dev/null; then
            # Compare with original
            if cmp -s "$input" "$cli_decompressed"; then
                log_success "CLI successfully decompressed pigz -z file"
            else
                log_error "CLI decompressed file doesn't match original"
            fi
        else
            log_error "CLI failed to decompress pigz -z file"
        fi
    else
        log_error "Failed to compress with pigz -z"
    fi
}

# Test 3: CLI compression and pigz -d verification
test_cli_compress_pigz_verify() {
    log_test "CLI compression verified with pigz -d"
    
    if [ ! -f "$CLI_EXECUTABLE" ]; then
        log_warning "Skipping CLI test - executable not available"
        return
    fi
    
    local input="$TEST_DIR/medium_diverse.txt"
    local cli_compressed="$TEST_DIR/medium_diverse_cli.zz"
    local pigz_decompressed="$TEST_DIR/medium_diverse_pigz.txt"
    
    # Compress with CLI
    if "$CLI_EXECUTABLE" compress "$input" "$cli_compressed" 2>/dev/null; then
        log_info "Compressed with CLI"
        
        # Verify zlib header
        local header=$(hexdump -C "$cli_compressed" | head -1 | awk '{print $2}')
        if [[ $header == "78" ]]; then
            log_info "CLI output has correct zlib header"
            
            # Decompress with pigz
            if $PIGZ_EXECUTABLE -d -c "$cli_compressed" > "$pigz_decompressed" 2>/dev/null; then
                # Compare with original
                if cmp -s "$input" "$pigz_decompressed"; then
                    log_success "CLI compression verified with pigz -d"
                else
                    log_error "pigz decompressed file doesn't match original"
                fi
            else
                log_error "pigz failed to decompress CLI compressed file"
            fi
        else
            log_error "CLI output doesn't have correct zlib header"
        fi
    else
        log_error "CLI failed to compress file"
    fi
}

# Test 4: Cross-compatibility matrix test
test_cross_compatibility() {
    log_test "Cross-compatibility matrix (all combinations)"
    
    if [ ! -f "$CLI_EXECUTABLE" ]; then
        log_warning "Skipping full compatibility test - CLI executable not available"
        return
    fi
    
    local input="$TEST_DIR/large_mixed.txt"
    local temp_base="$TEST_DIR/cross_test"
    
    # Test matrix: CLI compress -> pigz decompress, pigz compress -> CLI decompress
    local tests=("cli_pigz" "pigz_cli")
    local success_count=0
    
    for test_type in "${tests[@]}"; do
        local compressed="${temp_base}_${test_type}.zz"
        local decompressed="${temp_base}_${test_type}_result.txt"
        
        case $test_type in
            "cli_pigz")
                log_info "Testing: CLI compress -> pigz decompress"
                if "$CLI_EXECUTABLE" compress "$input" "$compressed" 2>/dev/null; then
                    if $PIGZ_EXECUTABLE -d -c "$compressed" > "$decompressed" 2>/dev/null; then
                        if cmp -s "$input" "$decompressed"; then
                            log_info "✓ CLI -> pigz: SUCCESS"
                            success_count=$((success_count + 1))
                        else
                            log_error "✗ CLI -> pigz: File mismatch"
                        fi
                    else
                        log_error "✗ CLI -> pigz: Decompression failed"
                    fi
                else
                    log_error "✗ CLI -> pigz: Compression failed"
                fi
                ;;
            "pigz_cli")
                log_info "Testing: pigz compress -> CLI decompress"
                if $PIGZ_EXECUTABLE -z -c "$input" > "$compressed"; then
                    if "$CLI_EXECUTABLE" decompress "$compressed" "$decompressed" 2>/dev/null; then
                        if cmp -s "$input" "$decompressed"; then
                            log_info "✓ pigz -> CLI: SUCCESS"
                            success_count=$((success_count + 1))
                        else
                            log_error "✗ pigz -> CLI: File mismatch"
                        fi
                    else
                        log_error "✗ pigz -> CLI: Decompression failed"
                    fi
                else
                    log_error "✗ pigz -> CLI: Compression failed"
                fi
                ;;
        esac
    done
    
    if [ $success_count -eq 2 ]; then
        log_success "All cross-compatibility tests passed"
    elif [ $success_count -eq 1 ]; then
        log_warning "Partial cross-compatibility (1/2 tests passed)"
    else
        log_error "Cross-compatibility tests failed"
    fi
}

# Test 5: Different compression levels
test_compression_levels() {
    log_test "Testing different compression levels"
    
    if [ ! -f "$CLI_EXECUTABLE" ]; then
        log_warning "Skipping compression level test - CLI executable not available"
        return
    fi
    
    local input="$TEST_DIR/large_mixed.txt"
    local original_size=$(stat -c%s "$input")
    log_info "Original file size: $original_size bytes"
    
    local levels=(1 6 9)
    local success_count=0
    
    for level in "${levels[@]}"; do
        local compressed="$TEST_DIR/level_${level}.zz"
        local decompressed="$TEST_DIR/level_${level}_result.txt"
        
        if "$CLI_EXECUTABLE" compress "$input" "$compressed" "$level" 2>/dev/null; then
            local compressed_size=$(stat -c%s "$compressed")
            local ratio=$(awk "BEGIN {printf \"%.2f\", $compressed_size * 100 / $original_size}")
            
            # Verify by decompressing
            if $PIGZ_EXECUTABLE -d -c "$compressed" > "$decompressed" 2>/dev/null; then
                if cmp -s "$input" "$decompressed"; then
                    log_info "Level $level: ${compressed_size} bytes (${ratio}% of original)"
                    success_count=$((success_count + 1))
                else
                    log_error "Level $level: Decompressed file doesn't match"
                fi
            else
                log_error "Level $level: pigz failed to decompress"
            fi
        else
            log_error "Level $level: CLI compression failed"
        fi
    done
    
    if [ $success_count -eq 3 ]; then
        log_success "All compression levels work correctly"
    else
        log_error "Some compression levels failed ($success_count/3 passed)"
    fi
}

# Test 6: Binary data handling
test_binary_data() {
    log_test "Binary data compression/decompression"
    
    local input="$TEST_DIR/binary.bin"
    local compressed="$TEST_DIR/binary.bin.zz"
    local decompressed="$TEST_DIR/binary_result.bin"
    
    # Test with pigz only (since it should work)
    if $PIGZ_EXECUTABLE -z -c "$input" > "$compressed"; then
        if $PIGZ_EXECUTABLE -d -c "$compressed" > "$decompressed" 2>/dev/null; then
            if cmp -s "$input" "$decompressed"; then
                log_success "Binary data: pigz round-trip successful"
            else
                log_error "Binary data: pigz round-trip failed"
            fi
        else
            log_error "Binary data: pigz decompression failed"
        fi
    else
        log_error "Binary data: pigz compression failed"
    fi
    
    # Test with CLI if available
    if [ -f "$CLI_EXECUTABLE" ]; then
        local cli_compressed="$TEST_DIR/binary_cli.bin.zz"
        local cli_decompressed="$TEST_DIR/binary_cli_result.bin"
        
        if "$CLI_EXECUTABLE" compress "$input" "$cli_compressed" 2>/dev/null; then
            if "$CLI_EXECUTABLE" decompress "$cli_compressed" "$cli_decompressed" 2>/dev/null; then
                if cmp -s "$input" "$cli_decompressed"; then
                    log_success "Binary data: CLI round-trip successful"
                else
                    log_error "Binary data: CLI round-trip failed"
                fi
            else
                log_error "Binary data: CLI decompression failed"
            fi
        else
            log_error "Binary data: CLI compression failed"
        fi
    fi
}

# Main test runner
run_all_tests() {
    echo ""
    echo "========================================"
    echo "ZLib.kotlin CLI and pigz Integration Tests"
    echo "========================================"
    echo ""
    
    # Setup
    log_info "Setting up test environment..."
    rm -rf "$TEST_DIR"
    mkdir -p "$TEST_DIR"
    
    set +e  # Temporarily disable exit on error
    check_prerequisites
    local prereq_status=$?
    set -e  # Re-enable exit on error
    
    create_test_files
    
    echo ""
    echo "Running Tests..."
    echo "----------------"
    
    # Run all tests
    test_pigz_basic
    test_cli_decompress_pigz
    test_cli_compress_pigz_verify
    test_cross_compatibility
    test_compression_levels
    test_binary_data
    
    # Summary
    echo ""
    echo "========================================"
    echo "Test Summary"
    echo "========================================"
    echo "Total Tests: $TESTS_TOTAL"
    echo "Passed:     $TESTS_PASSED"
    echo "Failed:     $TESTS_FAILED"
    
    if [ $TESTS_FAILED -eq 0 ]; then
        log_success "All tests passed!"
        return 0
    else
        log_error "Some tests failed!"
        return 1
    fi
}

# Show usage information
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "ZLib.kotlin CLI and pigz Integration Test Script"
    echo ""
    echo "This script tests the integration between ZLib.kotlin CLI tool and pigz -z option."
    echo "It verifies that:"
    echo "  1. pigz -z creates valid zlib format files"
    echo "  2. ZLib.kotlin CLI can decompress pigz -z files"
    echo "  3. ZLib.kotlin CLI creates files that pigz -d can decompress"
    echo "  4. Cross-compatibility works in both directions"
    echo ""
    echo "Options:"
    echo "  -h, --help     Show this help message"
    echo "  -v, --verbose  Enable verbose output"
    echo ""
    echo "Prerequisites:"
    echo "  - pigz must be installed"
    echo "  - ZLib.kotlin CLI executable should be built (optional for partial tests)"
    echo "    Build with: ./gradlew linkDebugExecutableLinuxX64"
    echo ""
    echo "Note: Some tests will be skipped if CLI executable is not available,"
    echo "      but pigz-only tests will still run to validate the format."
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_usage
            exit 0
            ;;
        -v|--verbose)
            set -x
            shift
            ;;
        *)
            echo "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Run the tests
if [ "${BASH_SOURCE[0]}" == "${0}" ]; then
    run_all_tests
    exit $?
fi