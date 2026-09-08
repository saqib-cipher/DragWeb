import re

with open('app/src/main/java/sketchweb/gl/LogicBlockActivity.java', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Strip class LogicBlockActivity$XX { ... }
# We match top-level inner classes "class LogicBlockActivity$..." up to matching braces
def remove_synth_classes(code):
    pattern = r'class\s+LogicBlockActivity\$\w+[\s\S]*?\n}'
    # Remove lines matching class LogicBlockActivity$
    lines = code.split('\n')
    new_lines = []
    in_synth = False
    brace_count = 0
    for line in lines:
        if not in_synth and ('class LogicBlockActivity$' in line or 'access$' in line):
            in_synth = True
            brace_count = line.count('{') - line.count('}')
            if brace_count <= 0:
                in_synth = False
            continue
        if in_synth:
            brace_count += line.count('{') - line.count('}')
            if brace_count <= 0:
                in_synth = False
            continue
        new_lines.append(line)
    return '\n'.join(new_lines)

cleaned = remove_synth_classes(text)

with open('app/src/main/java/sketchweb/gl/LogicBlockActivity.java', 'w', encoding='utf-8') as f:
    f.write(cleaned)

print("Removed all synthetic decompiled classes!")
