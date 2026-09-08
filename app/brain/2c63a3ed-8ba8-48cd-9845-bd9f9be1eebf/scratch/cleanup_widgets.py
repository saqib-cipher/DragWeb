import json

input_file = r'c:\Users\sddrk\AndroidStudioProjects\DragWeb\app\src\main\assets\widgets.json'

with open(input_file, 'r', encoding='utf-8') as f:
    widgets = json.load(f)

for widget in widgets:
    # Remove 'icon' key if it exists
    if 'icon' in widget:
        del widget['icon']
    
    # Special handling for "Icon" widget
    if widget.get('name') == 'Icon':
        widget['name'] = 'Icon (Library)'
        widget['tag'] = 'i'
        widget['function'] = {
            'class': 'ti ti-pointer-2',
            'style': {
                'fontSize': '24px',
                'color': '#333333',
                'display': 'inline-block'
            }
        }

with open(input_file, 'w', encoding='utf-8') as f:
    json.dump(widgets, f, indent=2)

print("Cleanup complete.")
