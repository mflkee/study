from datetime import datetime, timedelta
from typing import Optional
from uuid import UUID
from jose import JWTError, jwt
import bcrypt
from app.config import settings
from app.models import User
from app.repositories import UserRepository
from app.schemas import UserCreate, ChangePassword


class AuthService:
    def __init__(self, user_repo: UserRepository):
        self.user_repo = user_repo

    def verify_password(self, plain_password: str, hashed_password: str) -> bool:
        return bcrypt.checkpw(plain_password.encode('utf-8'), hashed_password.encode('utf-8'))

    def get_password_hash(self, password: str) -> str:
        return bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')

    def create_access_token(self, user_id: str) -> str:
        expire = datetime.utcnow() + timedelta(minutes=settings.access_token_expire_minutes)
        to_encode = {"sub": user_id, "exp": expire, "type": "access"}
        return jwt.encode(to_encode, settings.secret_key, algorithm=settings.algorithm)

    def create_refresh_token(self, user_id: str) -> str:
        expire = datetime.utcnow() + timedelta(days=settings.refresh_token_expire_days)
        to_encode = {"sub": user_id, "exp": expire, "type": "refresh"}
        return jwt.encode(to_encode, settings.secret_key, algorithm=settings.algorithm)

    def decode_token(self, token: str) -> Optional[dict]:
        try:
            payload = jwt.decode(token, settings.secret_key, algorithms=[settings.algorithm])
            return payload
        except JWTError:
            return None

    async def register(self, user_data: UserCreate) -> User:
        existing = await self.user_repo.get_by_login(user_data.login)
        if existing:
            raise ValueError("Login already registered")

        if len(user_data.password) < 8:
            raise ValueError("Password must be at least 8 characters")

        user = User(
            first_name=user_data.first_name,
            last_name=user_data.last_name,
            login=user_data.login,
            password_hash=self.get_password_hash(user_data.password)
        )
        return await self.user_repo.create(user)

    async def authenticate(self, login: str, password: str) -> Optional[User]:
        user = await self.user_repo.get_by_login(login)
        if not user:
            return None
        if not self.verify_password(password, user.password_hash):
            return None
        return user

    async def change_password(self, user_id: UUID, data: ChangePassword) -> bool:
        user = await self.user_repo.get_by_id(user_id)
        if not user:
            return False
        if not self.verify_password(data.old_password, user.password_hash):
            return False
        user.password_hash = self.get_password_hash(data.new_password)
        await self.user_repo.create(user)
        return True

    async def refresh_token(self, refresh_token: str) -> Optional[dict]:
        payload = self.decode_token(refresh_token)
        if not payload or payload.get("type") != "refresh":
            return None
        user_id = payload.get("sub")
        user = await self.user_repo.get_by_id(UUID(user_id))
        if not user:
            return None
        return {
            "access_token": self.create_access_token(str(user.id)),
            "refresh_token": self.create_refresh_token(str(user.id))
        }
