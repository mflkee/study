from __future__ import annotations

from pydantic import BaseModel, ConfigDict


class FacultyRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str


class SubjectRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str


class GradeRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    source_row_number: int
    score: int
    subject: SubjectRead


class StudentRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    last_name: str
    first_name: str
    faculty: FacultyRead
    grades: list[GradeRead]
