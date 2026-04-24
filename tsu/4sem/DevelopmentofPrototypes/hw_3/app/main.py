from __future__ import annotations

from fastapi import Depends, FastAPI
from sqlalchemy import func, select
from sqlalchemy.orm import Session, selectinload

from app.database import SessionLocal
from app.models import Faculty, Grade, Student, Subject
from app.schemas import StudentRead

app = FastAPI(title="Students Database Homework")


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


@app.get("/health")
def healthcheck() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/students", response_model=list[StudentRead])
def list_students(db: Session = Depends(get_db)) -> list[Student]:
    statement = (
        select(Student)
        .options(
            selectinload(Student.faculty),
            selectinload(Student.grades).selectinload(Grade.subject),
        )
        .order_by(Student.last_name, Student.first_name, Student.id)
    )
    return list(db.scalars(statement).unique())


@app.get("/stats")
def stats(db: Session = Depends(get_db)) -> dict[str, int]:
    return {
        "faculties": db.scalar(select(func.count(Faculty.id))) or 0,
        "students": db.scalar(select(func.count(Student.id))) or 0,
        "subjects": db.scalar(select(func.count(Subject.id))) or 0,
        "grades": db.scalar(select(func.count(Grade.id))) or 0,
    }
