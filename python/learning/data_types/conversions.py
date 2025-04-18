def datatype(func):
    def wraper(value):
        converted = func(value)
        print(f'variable = {converted}')
        print(f'type = {type(converted)}')
        return converted
    return wraper

@datatype
def convert_to_str(x):
    return str(x)

@datatype
def convert_to_int(x):
    return int(x)

@datatype
def convert_to_float(x):
    return float(x)

@datatype
def convert_to_complex(x):
    return complex(x)

convert_to_int(13.5)
convert_to_float(5)
convert_to_complex(4)
convert_to_str(24.712)
