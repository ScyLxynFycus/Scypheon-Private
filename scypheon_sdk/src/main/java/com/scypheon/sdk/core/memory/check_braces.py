import sys

file_path = 'D:/AuraLink/scypheon_sdk/src/main/java/com/scypheon/sdk/core/memory/DualMemoryManager.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

stack = 0
found_class = False
for i, line in enumerate(lines):
    # Ignore comments to avoid false positives
    clean_line = line.split('//')[0]
    for char in clean_line:
        if char == '{':
            stack += 1
            found_class = True
        elif char == '}':
            stack -= 1
            if found_class and stack == 0:
                print(f"CLASS CLOSED AT LINE {i + 1}")
                sys.exit(0)
print(f"END OF FILE REACHED. FINAL STACK: {stack}")
