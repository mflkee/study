from fastapi import FastAPI

app = FastAPI()


@app.get("/")
def home() -> :
    return [1, 2, 3, 4, 5, 6]
