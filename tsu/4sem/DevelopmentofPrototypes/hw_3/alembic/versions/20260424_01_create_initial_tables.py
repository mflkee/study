"""Create initial tables for the homework project."""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "20260424_01"
down_revision: Union[str, Sequence[str], None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "faculties",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("name", sa.String(length=100), nullable=False),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("name"),
    )
    op.create_index("ix_faculties_name", "faculties", ["name"], unique=True)

    op.create_table(
        "subjects",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("name", sa.String(length=100), nullable=False),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("name"),
    )
    op.create_index("ix_subjects_name", "subjects", ["name"], unique=True)

    op.create_table(
        "students",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("last_name", sa.String(length=100), nullable=False),
        sa.Column("first_name", sa.String(length=100), nullable=False),
        sa.Column("faculty_id", sa.Integer(), nullable=False),
        sa.ForeignKeyConstraint(["faculty_id"], ["faculties.id"]),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("last_name", "first_name", "faculty_id", name="uq_students_identity"),
    )
    op.create_index("ix_students_first_name", "students", ["first_name"], unique=False)
    op.create_index("ix_students_last_name", "students", ["last_name"], unique=False)

    op.create_table(
        "grades",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("source_row_number", sa.Integer(), nullable=False),
        sa.Column("student_id", sa.Integer(), nullable=False),
        sa.Column("subject_id", sa.Integer(), nullable=False),
        sa.Column("score", sa.Integer(), nullable=False),
        sa.CheckConstraint("score >= 0 AND score <= 100", name="ck_grades_score_range"),
        sa.ForeignKeyConstraint(["student_id"], ["students.id"]),
        sa.ForeignKeyConstraint(["subject_id"], ["subjects.id"]),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("source_row_number", name="uq_grades_source_row_number"),
    )


def downgrade() -> None:
    op.drop_table("grades")
    op.drop_index("ix_students_last_name", table_name="students")
    op.drop_index("ix_students_first_name", table_name="students")
    op.drop_table("students")
    op.drop_index("ix_subjects_name", table_name="subjects")
    op.drop_table("subjects")
    op.drop_index("ix_faculties_name", table_name="faculties")
    op.drop_table("faculties")
