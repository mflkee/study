from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from jose import JWTError
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import get_db
from app.repositories import UserRepository, TaskRepository, CategoryRepository
from app.services.auth_service import AuthService
from app.services.task_service import TaskService
from app.services.prediction_service import PredictionService
from app.schemas import UserResponse
from uuid import UUID

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="api/v1/auth/login")


async def get_auth_service(db: AsyncSession = Depends(get_db)):
    user_repo = UserRepository(db)
    return AuthService(user_repo)


async def get_task_service(db: AsyncSession = Depends(get_db)):
    task_repo = TaskRepository(db)
    category_repo = CategoryRepository(db)
    return TaskService(task_repo, category_repo)


async def get_prediction_service(db: AsyncSession = Depends(get_db)):
    task_repo = TaskRepository(db)
    return PredictionService(task_repo)


async def get_current_user(
    token: str = Depends(oauth2_scheme),
    auth_service: AuthService = Depends(get_auth_service)
) -> UserResponse:
    payload = auth_service.decode_token(token)
    if not payload or payload.get("type") != "access":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid authentication credentials"
        )

    user_id = payload.get("sub")
    user = await auth_service.user_repo.get_by_id(UUID(user_id))
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="User not found"
        )

    return UserResponse(
        id=str(user.id),
        first_name=user.first_name,
        last_name=user.last_name,
        login=user.login,
        created_at=user.created_at
    )
