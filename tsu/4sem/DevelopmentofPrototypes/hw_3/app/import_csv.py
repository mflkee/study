from __future__ import annotations

import csv
from pathlib import Path

from sqlalchemy import select

from app.database import BASE_DIR, SessionLocal
from app.models import Faculty, Grade, Student, Subject

CSV_PATH = BASE_DIR / "students.csv"


def _clean(value: str) -> str:
    return value.strip()


def import_students(csv_path: Path = CSV_PATH) -> None:
    with SessionLocal() as session:
        faculties = {faculty.name: faculty for faculty in session.scalars(select(Faculty))}
        subjects = {subject.name: subject for subject in session.scalars(select(Subject))}
        students = {
            (student.last_name, student.first_name, student.faculty_id): student
            for student in session.scalars(select(Student))
        }
        imported_rows = set(session.scalars(select(Grade.source_row_number)))

        with csv_path.open(encoding="utf-8", newline="") as csv_file:
            reader = csv.DictReader(csv_file)
            for row_number, row in enumerate(reader, start=2):
                if row_number in imported_rows:
                    continue

                last_name = _clean(row["Фамилия"])
                first_name = _clean(row["Имя"])
                faculty_name = _clean(row["Факультет"])
                subject_name = _clean(row["Курс"])
                score = int(_clean(row["Оценка"]))

                faculty = faculties.get(faculty_name)
                if faculty is None:
                    faculty = Faculty(name=faculty_name)
                    session.add(faculty)
                    session.flush()
                    faculties[faculty_name] = faculty

                subject = subjects.get(subject_name)
                if subject is None:
                    subject = Subject(name=subject_name)
                    session.add(subject)
                    session.flush()
                    subjects[subject_name] = subject

                student_key = (last_name, first_name, faculty.id)
                student = students.get(student_key)
                if student is None:
                    student = Student(
                        last_name=last_name,
                        first_name=first_name,
                        faculty_id=faculty.id,
                    )
                    session.add(student)
                    session.flush()
                    students[student_key] = student

                session.add(
                    Grade(
                        source_row_number=row_number,
                        student_id=student.id,
                        subject_id=subject.id,
                        score=score,
                    )
                )
                imported_rows.add(row_number)

        session.commit()


def main() -> None:
    import_students()
    print(f"Data imported from {CSV_PATH.name}")


if __name__ == "__main__":
    main()
