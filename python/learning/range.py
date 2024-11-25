x = range(6)
for n in x:
    print(n)

print("---")

value = range(0, 10, 2)
for i in value:
    print(i)

print("___")

complexity_PL = {"python": 6, "c++": 8, "rust": 10}
for language, complexity in complexity_PL.items():
    print(f"{language}: {complexity}")

print("---")

discipline = [
    {"name": "history", "complexity": 7},
    {"name": "programming", "complexity": 9},
    {"name": "math", "complexity": 10},
]


def get_complexity(d):
    return d.get("complexity")


discipline.sort(key=get_complexity, reverse=True)
for disciplines in discipline:
    print(f"{disciplines['name']}: {disciplines['complexity']}")
