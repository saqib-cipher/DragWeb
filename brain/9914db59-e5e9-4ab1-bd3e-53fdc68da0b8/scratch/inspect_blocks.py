import json, sys
sys.stdout.reconfigure(encoding='utf-8')
with open('app/src/main/assets/blocks.json', 'r', encoding='utf-8') as f:
    data = json.load(f)
for b in data:
    id_val = b.get('id', '')
    spec = b.get('spec', '')
    if 'load' in id_val.lower() or 'page' in id_val.lower() or 'load' in spec.lower() or 'page' in spec.lower():
        print(f"id='{id_val}' spec='{spec}' code='{b.get('code')}'")
