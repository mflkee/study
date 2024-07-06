import os


def disk_space_analysis():
    total, used, free = map(
        int, os.popen("df / | sed '1d' | awk '{print $2,$3,$4}'").readline().split()
    )
    print(f"Total space: {total} KB")
    print(f"Used space: {used} KB")
    print(f"Free space: {free} KB")


disk_space_analysis()
