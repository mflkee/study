from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, status, Query
from uuid import UUID
from app.dependencies import get_task_service, get_prediction_service, get_current_user
from app.schemas import (
    TaskCreate, TaskUpdate, TaskResponse, TaskComplete,
    CategoryCreate, CategoryResponse, TaskFilter
)
from app.services.task_service import TaskService
from app.services.prediction_service import PredictionService
from app.schemas import UserResponse

router = APIRouter(prefix="/tasks", tags=["tasks"])


@router.post("/categories", response_model=CategoryResponse, status_code=status.HTTP_201_CREATED)
async def create_category(
    data: CategoryCreate,
    current_user: UserResponse = Depends(get_current_user),
    task_service: TaskService = Depends(get_task_service)
):
    category = await task_service.create_category(
        UUID(current_user.id), data.name
    )
    return CategoryResponse(
        id=category.id,
        name=category.name,
        user_id=str(category.user_id) if category.user_id else None
    )


@router.get("/categories", response_model=List[CategoryResponse])
async def get_categories(
    current_user: UserResponse = Depends(get_current_user),
    task_service: TaskService = Depends(get_task_service)
):
    categories = await task_service.get_categories(UUID(current_user.id))
    return [
        CategoryResponse(
            id=c.id,
            name=c.name,
            user_id=str(c.user_id) if c.user_id else None
        )
        for c in categories
    ]


@router.post("/", response_model=TaskResponse, status_code=status.HTTP_201_CREATED)
async def create_task(
    task_data: TaskCreate,
    current_user: UserResponse = Depends(get_current_user),
    task_service: TaskService = Depends(get_task_service)
):
    task = await task_service.create_task(UUID(current_user.id), task_data)
    return TaskResponse(
        id=str(task.id),
        title=task.title,
        description=task.description,
        category_id=task.category_id,
        user_id=str(task.user_id),
        status=task.status.value,
        priority=task.priority,
        due_date=task.due_date,
        actual_duration=task.actual_duration,
        created_at=task.created_at
    )


@router.get("/", response_model=List[TaskResponse])
async def get_tasks(
    status: Optional[str] = Query(None),
    category_id: Optional[int] = Query(None),
    priority: Optional[int] = Query(None),
    sort_by: Optional[str] = Query("created_at"),
    page: int = Query(1, ge=1),
    page_size: int = Query(10, ge=1, le=100),
    current_user: UserResponse = Depends(get_current_user),
    task_service: TaskService = Depends(get_task_service)
):
    tasks = await task_service.get_tasks(
        UUID(current_user.id),
        status=status,
        category_id=category_id,
        priority=priority,
        sort_by=sort_by,
        page=page,
        page_size=page_size
    )
    return [
        TaskResponse(
            id=str(t.id),
            title=t.title,
            description=t.description,
            category_id=t.category_id,
            user_id=str(t.user_id),
            status=t.status.value,
            priority=t.priority,
            due_date=t.due_date,
            actual_duration=t.actual_duration,
            created_at=t.created_at
        )
        for t in tasks
    ]


@router.get("/{task_id}", response_model=TaskResponse)
async def get_task(
    task_id: str,
    current_user: UserResponse = Depends(get_current_user),
    task_service: TaskService = Depends(get_task_service)
):
    task = await task_service.get_task(UUID(current_user.id), UUID(task_id))
    if not task:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Task not found")
    return TaskResponse(
        id=str(task.id),
        title=task.title,
        description=task.description,
        category_id=task.category_id,
        user_id=str(task.user_id),
        status=task.status.value,
        priority=task.priority,
        due_date=task.due_date,
        actual_duration=task.actual_duration,
        created_at=task.created_at
    )


@router.put("/{task_id}", response_model=TaskResponse)
async def update_task(
    task_id: str,
    task_data: TaskUpdate,
    current_user: UserResponse = Depends(get_current_user),
    task_service: TaskService = Depends(get_task_service)
):
    task = await task_service.update_task(
        UUID(current_user.id), UUID(task_id), task_data
    )
    if not task:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Task not found")
    return TaskResponse(
        id=str(task.id),
        title=task.title,
        description=task.description,
        category_id=task.category_id,
        user_id=str(task.user_id),
        status=task.status.value,
        priority=task.priority,
        due_date=task.due_date,
        actual_duration=task.actual_duration,
        created_at=task.created_at
    )


@router.delete("/{task_id}")
async def delete_task(
    task_id: str,
    current_user: UserResponse = Depends(get_current_user),
    task_service: TaskService = Depends(get_task_service)
):
    success = await task_service.delete_task(UUID(current_user.id), UUID(task_id))
    if not success:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Task not found")
    return {"message": "Task deleted successfully"}


@router.patch("/{task_id}/complete", response_model=TaskResponse)
async def complete_task(
    task_id: str,
    data: TaskComplete,
    current_user: UserResponse = Depends(get_current_user),
    task_service: TaskService = Depends(get_task_service)
):
    task = await task_service.complete_task(
        UUID(current_user.id), UUID(task_id), data
    )
    if not task:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Task not found")
    return TaskResponse(
        id=str(task.id),
        title=task.title,
        description=task.description,
        category_id=task.category_id,
        user_id=str(task.user_id),
        status=task.status.value,
        priority=task.priority,
        due_date=task.due_date,
        actual_duration=task.actual_duration,
        created_at=task.created_at
    )


@router.get("/predict/{task_id}")
async def predict_duration(
    task_id: str,
    current_user: UserResponse = Depends(get_current_user),
    task_service: TaskService = Depends(get_task_service),
    prediction_service: PredictionService = Depends(get_prediction_service)
):
    task = await task_service.get_task(UUID(current_user.id), UUID(task_id))
    if not task:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Task not found")

    predicted = await prediction_service.predict_duration(
        UUID(current_user.id),
        task.category_id,
        task.priority,
        task.description or ""
    )
    return {"predicted_duration_minutes": predicted}
