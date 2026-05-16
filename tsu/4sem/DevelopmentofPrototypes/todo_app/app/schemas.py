from datetime import datetime
from typing import Optional
from pydantic import BaseModel, ConfigDict, field_validator


class UserBase(BaseModel):
    first_name: str
    last_name: str
    login: str


class UserCreate(UserBase):
    password: str


class UserResponse(UserBase):
    model_config = ConfigDict(from_attributes=True)
    id: str
    created_at: datetime


class Token(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"


class ChangePassword(BaseModel):
    old_password: str
    new_password: str


class CategoryBase(BaseModel):
    name: str


class CategoryCreate(CategoryBase):
    pass


class CategoryResponse(CategoryBase):
    model_config = ConfigDict(from_attributes=True)
    id: int
    user_id: Optional[str] = None


class TaskBase(BaseModel):
    title: str
    description: Optional[str] = None
    category_id: Optional[int] = None
    priority: int = 1
    due_date: Optional[datetime] = None

    @field_validator('title')
    @classmethod
    def title_not_empty(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError('Title cannot be empty')
        return v

    @field_validator('priority')
    @classmethod
    def priority_range(cls, v: int) -> int:
        if v < 1 or v > 5:
            raise ValueError('Priority must be between 1 and 5')
        return v


class TaskCreate(TaskBase):
    pass


class TaskUpdate(TaskBase):
    title: Optional[str] = None
    priority: Optional[int] = None


class TaskResponse(TaskBase):
    model_config = ConfigDict(from_attributes=True)
    id: str
    user_id: str
    status: str
    actual_duration: Optional[int] = None
    created_at: datetime


class TaskComplete(BaseModel):
    actual_duration: int


class TaskFilter(BaseModel):
    status: Optional[str] = None
    category_id: Optional[int] = None
    priority: Optional[int] = None
    sort_by: Optional[str] = "created_at"
    page: int = 1
    page_size: int = 10
