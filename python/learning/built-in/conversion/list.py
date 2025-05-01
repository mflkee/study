pl = ["cpp", "python", "js", "rust", "c", "ts", "java", "ruby"]

print(pl[0:3])
print(pl[1:4])
print(pl[:4])
print(pl[-3:])


pl_copy = pl[:]
print(pl_copy)
pl.append("bash")
print(pl)
print(pl_copy)
