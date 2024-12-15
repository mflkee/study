from fastapi import FastAPI

app = FastAPI()


@app.get("/")
def home() -> list:
    return [1, 2, 3, 4, 5, 6]
