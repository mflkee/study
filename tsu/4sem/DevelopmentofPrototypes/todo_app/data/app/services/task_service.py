from datetime import datetime
from typing import List, Optional
from uuid import UUID
from app.models import Task, Category, TaskStatus
from app.repositories import TaskRepository, CategoryRepository
from app.schemas import TaskCreate, TaskUpdate, TaskComplete


class TaskService:
    def __init__(self, task_repo: TaskRepository, category_repo: CategoryRepository):
        self.task_repo = task_repo
        self.category_repo = category_repo

    async def create_task(self, user_id: UUID, task_data: TaskCreate) -> Task:
        if task_data.category_id:
            category = await self.category_repo.get_by_id(task_data.category_id)
            if not category:
                raise ValueError("Category not found")

        task = Task(
            title=task_data.title,
            description=task_data.description,
            category_id=task_data.category_id,
            user_id=user_id,
            priority=task_data.priority,
            due_date=task_data.due_date
        )
        return await self.task_repo.create(task)

    async def get_task(self, user_id: UUID, task_id: UUID) -> Optional[Task]:
        task = await self.task_repo.get_by_id(task_id)
        if task and task.user_id != user_id:
            return None
        return task

    async def get_tasks(
        self,
        user_id: UUID,
        status: Optional[str] = None,
        category_id: Optional[int] = None,
        priority: Optional[int] = None,
        sort_by: str = "created_at",
        page: int = 1,
        page_size: int = 10
    ) -> List[Task]:
        return await self.task_repo.get_by_user(
            user_id, status, category_id, priority, sort_by, page, page_size
        )

    async def update_task(self, user_id: UUID, task_id: UUID, task_data: TaskUpdate) -> Optional[Task]:
        task = await self.get_task(user_id, task_id)
        if not task:
            return None

        if task_data.title is not None:
            task.title = task_data.title
        if task_data.description is not None:
            task.description = task_data.description
        if task_data.category_id is not None:
            task.category_id = task_data.category_id
        if task_data.priority is not None:
            task.priority = task_data.priority
        if task_data.due_date is not None:
            task.due_date = task_data.due_date

        return await self.task_repo.update(task)

    async def delete_task(self, user_id: UUID, task_id: UUID) -> bool:
        task = await self.get_task(user_id, task_id)
        if not task:
            return False
        await self.task_repo.delete(task)
        return True

    async def complete_task(self, user_id: UUID, task_id: UUID, data: TaskComplete) -> Optional[Task]:
        task = await self.get_task(user_id, task_id)
        if not task:
            return None
        task.status = TaskStatus.COMPLETED
        task.actual_duration = data.actual_duration
        return await self.task_repo.update(task)

    async def create_category(self, user_id: UUID, name: str) -> Category:
        category = Category(name=name, user_id=user_id)
        return await self.category_repo.create(category)

    async def get_categories(self, user_id: UUID) -> List[Category]:
        return await self.category_repo.get_by_user(user_id)
