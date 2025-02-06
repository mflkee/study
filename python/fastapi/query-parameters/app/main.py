from fastapi import FastAPI, HTTPException
import uvicorn

app = FastAPI()

measuring_instruments = [
        {"id": 1, "name": "УДВН", "serial_number": "3261"},
        {"id": 2, "name": "УДВН", "serial_number": "3262"},
        {"id": 3, "name": "FVM", "serial_number": "21184805"},
        {"id": 4, "name": "FVM", "serial_number": "21184811"},
]

@app.get("/mi")
async def get_all(skip:int  = 0, limit:int = 10):
    return measuring_instruments[skip: skip + limit]

@app.get("/mi/{mi_id}")
async def get_mi(mi_id: int):
    for mi in measuring_instruments:
        if mi["id"] == mi_id:
            return mi
    raise HTTPException(status_code = 404, detail="MI not found")




if __name__ == "__main__":
    uvicorn.run("main:app", reload = True)
