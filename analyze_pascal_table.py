#!/usr/bin/env python3
"""
Create a reverse mapping to understand the Pascal table structure
"""

import re

# Read the Pascal table from the markdown file
with open('src/commonMain/kotlin/ai/solace/zlib/deflate/static_ltree.md', 'r') as f:
    content = f.read()

# Extract all freq values using regex
freq_pattern = r'fc:\(freq:\s*(\d+)\);dl:\(len:\s*(\d+)\)'
matches = re.findall(freq_pattern, content)

print("PASCAL TABLE ANALYSIS:")
print("=" * 50)

# Create a mapping from freq to index
freq_to_index = {}
for i, (freq, length) in enumerate(matches):
    freq_val = int(freq)
    if freq_val <= 255:  # Only look at character codes 0-255
        freq_to_index[freq_val] = i

print(f"Found {len(freq_to_index)} character mappings (0-255)")

# Check our problem characters
problem_chars = {
    'H': 72,
    'e': 101, 
    'l': 108,
    'o': 111,
    ' ': 32,
    'W': 87,
    'd': 100
}

print("\nPROBLEM CHARACTER ANALYSIS:")
print("=" * 50)
for char, ascii_val in problem_chars.items():
    if ascii_val in freq_to_index:
        table_index = freq_to_index[ascii_val]
        table_freq, table_len = matches[table_index]
        print(f"'{char}' (ASCII {ascii_val}):")
        print(f"  Should be at table index: {table_index}")
        print(f"  Table entry: freq={table_freq}, len={table_len}")
        print(f"  ✓ freq matches ASCII value: {int(table_freq) == ascii_val}")
    else:
        print(f"'{char}' (ASCII {ascii_val}): NOT FOUND in freq values")

print("\nCURRENT ISSUE ANALYSIS:")
print("=" * 50)
print("The current issue is that when we look up tempPointer=126:")
print(f"  Table index 126: freq={matches[126][0]}, len={matches[126][1]}")
print(f"  This gives us character {matches[126][0]} which is '{chr(int(matches[126][0]))}'")
print()
print("But we should be getting 'H' (72). This means:")
print("1. Either the tempPointer calculation is wrong")
print("2. Or the table construction is wrong")
print("3. Or the bit stream reading is off")

# Let's see what table index should give us 'H' (72)
if 72 in freq_to_index:
    correct_index = freq_to_index[72]
    print(f"\nTo get 'H' (72), we should look up table index: {correct_index}")
    print(f"But we're looking up index 126 instead.")
    print(f"Difference: {126 - correct_index}")

# Generate the corrected Kotlin table where index = ASCII value
print("\n" + "=" * 50)
print("CORRECTED KOTLIN TABLE (index = ASCII value):")
print("=" * 50)

# Create a corrected table where table[ascii] = [0, len, ascii]
corrected_table = ["0, 0, 0"] * 288  # Initialize with default values

for i, (freq, length) in enumerate(matches):
    freq_val = int(freq)
    if freq_val < len(corrected_table):
        corrected_table[freq_val] = f"0, {length}, {freq_val}"

# Print the corrected table
for i in range(0, len(corrected_table), 8):
    line_entries = corrected_table[i:i+8]
    print("            " + ", ".join(line_entries) + ",")
