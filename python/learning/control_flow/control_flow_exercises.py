lst_1 = ["bash", "zsh", "fish"]
default = 'bash'

if default not in lst_1:
    print("error: upd list for default")
else:
    print("ok")
