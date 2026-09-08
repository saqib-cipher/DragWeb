import re

with open('app/src/main/java/sketchweb/gl/LogicBlockActivity.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Let's inspect where synthetic classes start and end
# Line 943 to 1800: synthetic decompiled classes
start_synth = content.find('class LogicBlockActivity$17')
end_synth = content.find('private void showAddListPopup()')

print(f"Synthetic classes start index: {start_synth}, end index: {end_synth}")
