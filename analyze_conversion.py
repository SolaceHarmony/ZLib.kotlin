#!/usr/bin/env python3

# Read the current Kotlin static_ltree and check position 126*3+2 = 380

# This is a simplified version - in reality I'd need to parse the full StaticTree.kt file
# But for now, let me check what value should be at position 380

# From our analysis:
# Position 126 in C table has freq=117, len=8
# This should translate to triplet [?, 8, 117] in Kotlin
# So position 126*3+2 = 380 should contain 117

# But our debug shows it contains 78 ('N')

print("Expected vs Actual at position 126:")
print(f"C table position 126: freq=117, len=8")
print(f"Kotlin position 126*3+2 = {126*3+2}: should be 117, but actual is 78")
print()
print("This confirms the table conversion is wrong!")
print()

# Let's figure out the correct conversion
# The C table should be converted as:
# C: {{freq, len}} -> Kotlin: [operation, len, freq]
# Where operation is typically 0 for literal characters

print("Correct Kotlin triplet format for position 126:")
print(f"C: {{117, 8}} -> Kotlin: [0, 8, 117]")
print()

# Generate the correct static_ltree conversion
print("Generating correct static_ltree conversion from C header...")

c_entries = [
    (12, 8), (140, 8), (76, 8), (204, 8), (44, 8), (172, 8), (108, 8), (236, 8),
    (28, 8), (156, 8), (92, 8), (220, 8), (60, 8), (188, 8), (124, 8), (252, 8),
    # ... (truncated for brevity, but this would be the full 288 entries)
]

# For now, let's just check a few key positions
test_positions = [66, 126, 265]  # Where 'N', 'u', 'H' should be

print("Key positions analysis:")
for pos in test_positions:
    if pos == 66:
        print(f"Position {pos}: Should contain freq=78 ('N') based on C table")
    elif pos == 126:
        print(f"Position {pos}: Should contain freq=117 ('u') based on C table")  
    elif pos == 265:
        print(f"Position {pos}: Should contain freq=72 ('H') based on C table")
