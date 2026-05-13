import json

with open('/home/mflkee/study/DS/tsu/lab12/МакеевГБ.ipynb', 'r') as f:
    nb = json.load(f)

for cell in nb['cells']:
    if isinstance(cell['source'], list):
        # Add \n to all but the last line
        lines = cell['source']
        if len(lines) > 1:
            cell['source'] = [line + '\n' for line in lines[:-1]] + [lines[-1]]

with open('/home/mflkee/study/DS/tsu/lab12/МакеевГБ.ipynb', 'w') as f:
    json.dump(nb, f, ensure_ascii=False, indent=1)

print('Fixed newlines in МакеевГБ.ipynb')
