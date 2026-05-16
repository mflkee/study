from fastapi import FastAPI
from app.api import auth, tasks

app = FastAPI(title="To-Do App API", version="1.0.0")

app.include_router(auth.router, prefix="/api/v1")
app.include_router(tasks.router, prefix="/api/v1")


@app.get("/health")
async def health_check():
    return {"status": "ok"}
