name = "    GleB     "

print(name)

print(name.title())
print(name.upper())
print(name.lower())

print(f"use variable in sting: {name}")
print(f"Переменная:\n\t{name}")
print(f"TEXT {name.strip()} TEXT")
print(f"TEXT {name.rstrip()} TEXT")
print(f"TEXT {name.lstrip()} TEXT")

url = "https//:google.com"


def shortening(url):
    return url.removeprefix("https//:").removesuffix(".com")


print(shortening(url))

a = 0.1 + 0.2
print(a)

b = a / 3
print(b)

c = 13_13
print(c)

a, b, c = 1, 1, 1
print(f'\n{a}\n{b}\n{c}')

PI = 13.4
r = 13
s = PI * r ** 2 
print(s)
PI = 2
print(PI)
