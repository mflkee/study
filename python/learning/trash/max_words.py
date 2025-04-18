words = ["Python", "SQL", "cpp", "JavaScript", "Rust"]
def long_word(list):
    longest = max(list, key=len)
    print(f'{longest}')

def min_word(list):
    shortest = min(list, key=len)
    print(f'{shortest}')


long_word(words)
min_word(words)
    
