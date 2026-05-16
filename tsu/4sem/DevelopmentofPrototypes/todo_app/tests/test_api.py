import pytest
import pytest_asyncio
from httpx import AsyncClient
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from sqlalchemy.pool import NullPool
from app.main import app
from app.database import Base, get_db
from app.models import User, Task, Category
from app.services.auth_service import AuthService
from app.repositories import UserRepository
from app.schemas import UserCreate

# Тестовая БД
TEST_DATABASE_URL = "postgresql+asyncpg://test_user:test_password@test_db:5432/test_db"

engine = create_async_engine(TEST_DATABASE_URL, echo=True, poolclass=NullPool)
TestingSessionLocal = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)


async def override_get_db():
    async with TestingSessionLocal() as session:
        try:
            yield session
        finally:
            await session.close()

app.dependency_overrides[get_db] = override_get_db


@pytest_asyncio.fixture(autouse=True)
async def setup_database():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
        await conn.run_sync(Base.metadata.create_all)
    yield
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)


@pytest_asyncio.fixture
async def db_session():
    async with TestingSessionLocal() as session:
        yield session
        await session.rollback()


@pytest_asyncio.fixture
async def test_user(db_session):
    auth_service = AuthService(UserRepository(db_session))
    user = await auth_service.register(UserCreate(
        first_name="Test",
        last_name="User",
        login="testuser",
        password="password123"
    ))
    return user


@pytest_asyncio.fixture
async def auth_headers(test_user):
    auth_service = AuthService(UserRepository(None))
    token = auth_service.create_access_token(str(test_user.id))
    return {"Authorization": f"Bearer {token}"}


@pytest_asyncio.fixture
async def async_client():
    async with AsyncClient(app=app, base_url="http://test") as client:
        yield client


class TestAuth:
    @pytest.mark.asyncio
    async def test_register(self, async_client):
        response = await async_client.post("/api/v1/auth/register", json={
            "first_name": "John",
            "last_name": "Doe",
            "login": "johndoe",
            "password": "password123"
        })
        assert response.status_code == 201
        data = response.json()
        assert data["login"] == "johndoe"

    @pytest.mark.asyncio
    async def test_register_duplicate(self, async_client):
        await async_client.post("/api/v1/auth/register", json={
            "first_name": "John",
            "last_name": "Doe",
            "login": "johndoe2",
            "password": "password123"
        })
        response = await async_client.post("/api/v1/auth/register", json={
            "first_name": "John",
            "last_name": "Doe",
            "login": "johndoe2",
            "password": "password123"
        })
        assert response.status_code == 400

    @pytest.mark.asyncio
    async def test_register_weak_password(self, async_client):
        response = await async_client.post("/api/v1/auth/register", json={
            "first_name": "John",
            "last_name": "Doe",
            "login": "johndoe3",
            "password": "123"
        })
        assert response.status_code == 400

    @pytest.mark.asyncio
    async def test_login(self, async_client):
        await async_client.post("/api/v1/auth/register", json={
            "first_name": "John",
            "last_name": "Doe",
            "login": "johndoe4",
            "password": "password123"
        })
        response = await async_client.post("/api/v1/auth/login", data={
            "username": "johndoe4",
            "password": "password123"
        })
        assert response.status_code == 200
        data = response.json()
        assert "access_token" in data

    @pytest.mark.asyncio
    async def test_login_invalid(self, async_client):
        response = await async_client.post("/api/v1/auth/login", data={
            "username": "nonexistent",
            "password": "wrong"
        })
        assert response.status_code == 401


class TestTasks:
    @pytest.mark.asyncio
    async def test_create_task(self, async_client, auth_headers):
        response = await async_client.post("/api/v1/tasks/", json={
            "title": "Test Task",
            "description": "Test Description",
            "priority": 2
        }, headers=auth_headers)
        assert response.status_code == 201
        data = response.json()
        assert data["title"] == "Test Task"

    @pytest.mark.asyncio
    async def test_get_tasks(self, async_client, auth_headers):
        await async_client.post("/api/v1/tasks/", json={
            "title": "Task 1",
            "priority": 1
        }, headers=auth_headers)
        response = await async_client.get("/api/v1/tasks/", headers=auth_headers)
        assert response.status_code == 200
        data = response.json()
        assert len(data) > 0

    @pytest.mark.asyncio
    async def test_complete_task(self, async_client, auth_headers):
        create_response = await async_client.post("/api/v1/tasks/", json={
            "title": "Complete Task"
        }, headers=auth_headers)
        task_id = create_response.json()["id"]
        
        response = await async_client.patch(f"/api/v1/tasks/{task_id}/complete", json={
            "actual_duration": 45
        }, headers=auth_headers)
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "completed"
        assert data["actual_duration"] == 45

    @pytest.mark.asyncio
    async def test_delete_task(self, async_client, auth_headers):
        create_response = await async_client.post("/api/v1/tasks/", json={
            "title": "Delete Task"
        }, headers=auth_headers)
        task_id = create_response.json()["id"]
        
        response = await async_client.delete(f"/api/v1/tasks/{task_id}", headers=auth_headers)
        assert response.status_code == 200
        
        get_response = await async_client.get(f"/api/v1/tasks/{task_id}", headers=auth_headers)
        assert get_response.status_code == 404


class TestValidation:
    @pytest.mark.asyncio
    async def test_invalid_task_data(self, async_client, auth_headers):
        response = await async_client.post("/api/v1/tasks/", json={
            "title": "",  # empty title
            "priority": 10  # invalid priority
        }, headers=auth_headers)
        assert response.status_code == 422

    @pytest.mark.asyncio
    async def test_unauthorized_access(self, async_client):
        response = await async_client.get("/api/v1/tasks/")
        assert response.status_code == 401