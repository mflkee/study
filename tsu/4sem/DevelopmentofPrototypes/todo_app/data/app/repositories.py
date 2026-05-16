from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, and_
from sqlalchemy.orm import selectinload
from typing import Optional, List
from uuid import UUID
from app.models import User, Task, Category, TaskStatus


class UserRepository:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def create(self, user: User) -> User:
        self.session.add(user)
        await self.session.commit()
        await self.session.refresh(user)
        return user

    async def get_by_login(self, login: str) -> Optional[User]:
        result = await self.session.execute(select(User).where(User.login == login))
        return result.scalar_one_or_none()

    async def get_by_id(self, user_id: UUID) -> Optional[User]:
        result = await self.session.execute(select(User).where(User.id == user_id))
        return result.scalar_one_or_none()


class CategoryRepository:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def create(self, category: Category) -> Category:
        self.session.add(category)
        await self.session.commit()
        await self.session.refresh(category)
        return category

    async def get_by_id(self, category_id: int) -> Optional[Category]:
        result = await self.session.execute(select(Category).where(Category.id == category_id))
        return result.scalar_one_or_none()

    async def get_by_user(self, user_id: UUID) -> List[Category]:
        result = await self.session.execute(
            select(Category).where(
                and_(Category.user_id == user_id)
            )
        )
        return result.scalars().all()


class TaskRepository:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def create(self, task: Task) -> Task:
        self.session.add(task)
        await self.session.commit()
        await self.session.refresh(task)
        return task

    async def get_by_id(self, task_id: UUID) -> Optional[Task]:
        result = await self.session.execute(select(Task).where(Task.id == task_id))
        return result.scalar_one_or_none()

    async def get_by_user(
        self,
        user_id: UUID,
        status: Optional[str] = None,
        category_id: Optional[int] = None,
        priority: Optional[int] = None,
        sort_by: str = "created_at",
        page: int = 1,
        page_size: int = 10
    ):
        query = select(Task).where(Task.user_id == user_id)

        if status:
            query = query.where(Task.status == TaskStatus(status))
        if category_id:
            query = query.where(Task.category_id == category_id)
        if priority:
            query = query.where(Task.priority == priority)

        if sort_by == "due_date":
            query = query.order_by(Task.due_date)
        elif sort_by == "priority":
            query = query.order_by(Task.priority.desc())
        else:
            query = query.order_by(Task.created_at.desc())

        query = query.offset((page - 1) * page_size).limit(page_size)
        result = await self.session.execute(query)
        return result.scalars().all()

    async def update(self, task: Task) -> Task:
        await self.session.commit()
        await self.session.refresh(task)
        return task

    async def delete(self, task: Task):
        await self.session.delete(task)
        await self.session.commit()

    async def get_completed_by_user(self, user_id: UUID) -> List[Task]:
        result = await self.session.execute(
            select(Task).where(
                and_(Task.user_id == user_id, Task.status == TaskStatus.COMPLETED)
            )
        )
        return result.scalars().all()
