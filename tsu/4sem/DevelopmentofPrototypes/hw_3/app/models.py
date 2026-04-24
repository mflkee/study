from __future__ import annotations

from sqlalchemy import CheckConstraint, ForeignKey, Integer, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class Faculty(Base):
    __tablename__ = "faculties"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(100), unique=True, index=True)

    students: Mapped[list["Student"]] = relationship(back_populates="faculty")


class Subject(Base):
    __tablename__ = "subjects"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(100), unique=True, index=True)

    grades: Mapped[list["Grade"]] = relationship(back_populates="subject")


class Student(Base):
    __tablename__ = "students"
    __table_args__ = (
        UniqueConstraint("last_name", "first_name", "faculty_id", name="uq_students_identity"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    last_name: Mapped[str] = mapped_column(String(100), index=True)
    first_name: Mapped[str] = mapped_column(String(100), index=True)
    faculty_id: Mapped[int] = mapped_column(ForeignKey("faculties.id"), nullable=False)

    faculty: Mapped["Faculty"] = relationship(back_populates="students")
    grades: Mapped[list["Grade"]] = relationship(back_populates="student")


class Grade(Base):
    __tablename__ = "grades"
    __table_args__ = (
        CheckConstraint("score >= 0 AND score <= 100", name="ck_grades_score_range"),
        UniqueConstraint("source_row_number", name="uq_grades_source_row_number"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    source_row_number: Mapped[int] = mapped_column(Integer, nullable=False)
    student_id: Mapped[int] = mapped_column(ForeignKey("students.id"), nullable=False)
    subject_id: Mapped[int] = mapped_column(ForeignKey("subjects.id"), nullable=False)
    score: Mapped[int] = mapped_column(Integer, nullable=False)

    student: Mapped["Student"] = relationship(back_populates="grades")
    subject: Mapped["Subject"] = relationship(back_populates="grades")
