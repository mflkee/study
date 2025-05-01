class Animal:
    def __init__(self, name):
        self.name = name

    def make_sound(self):
        pass


class Dog(Animal):
    def wag_tail(self):
        print("my name is", self.name, "and i can wag tail")


dog = Dog("Baddy")
dog.make_sound()
dog.wag_tail()
