from fastapi import FastAPI
import uvicorn

app = FastAPI()

measuring_instruments = [
        {"id": 1, "name": "УДВН", "serial_number": "3261"},
        {"id": 2, "name": "УДВН", "serial_number": "3262"},
        {"id": 3, "name": "FVM", "serial_number": "21184805"},
        {"id": 4, "name": "FVM", "serial_number": "21184811"},
]

@app.get("/mi")
async def get_mi(skip:int  = 0, limit:int = 10):
    return measuring_instruments[skip: skip + limit]

# @app.get("/mi")

if __name__ == "__main__":
    uvicorn.run("main:app", reload = True)
