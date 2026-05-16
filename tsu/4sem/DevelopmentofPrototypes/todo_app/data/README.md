# To-Do App API

FastAPI-based backend for task management application with AI-powered duration prediction.

## Features

- User authentication (JWT tokens)
- Task CRUD operations
- Category management
- Task filtering and pagination
- AI prediction of task completion time (scikit-learn)
- Docker Compose deployment

## Tech Stack

- Python 3.11
- FastAPI
- SQLAlchemy 2.0 (async)
- PostgreSQL
- Alembic (migrations)
- Pytest (testing)
- scikit-learn (ML)
- Docker & Docker Compose

## Quick Start

### Using Docker Compose

1. Clone the repository:
```bash
git clone <repo-url>
cd todo_app
```

2. Start the services:
```bash
docker-compose up -d
```

3. Run migrations:
```bash
docker-compose exec app alembic upgrade head
```

4. API will be available at: http://localhost:8000
5. API documentation: http://localhost:8000/docs

### Local Development

1. Create virtual environment:
```bash
python -m venv venv
source venv/bin/activate
```

2. Install dependencies:
```bash
pip install -r requirements.txt
```

3. Set up PostgreSQL and update `.env` file

4. Run migrations:
```bash
alembic upgrade head
```

5. Start the server:
```bash
uvicorn app.main:app --reload
```

## Running Tests

```bash
docker-compose up test_db
pytest
```

## API Endpoints

### Authentication
- `POST /api/v1/auth/register` - Register new user
- `POST /api/v1/auth/login` - Login (returns JWT)
- `POST /api/v1/auth/refresh` - Refresh token
- `POST /api/v1/auth/change-password` - Change password

### Tasks
- `POST /api/v1/tasks/` - Create task
- `GET /api/v1/tasks/` - List tasks (with filters)
- `GET /api/v1/tasks/{id}` - Get task
- `PUT /api/v1/tasks/{id}` - Update task
- `DELETE /api/v1/tasks/{id}` - Delete task
- `PATCH /api/v1/tasks/{id}/complete` - Complete task
- `GET /api/v1/tasks/predict/{id}` - Predict duration

### Categories
- `POST /api/v1/tasks/categories` - Create category
- `GET /api/v1/tasks/categories` - List categories

## Environment Variables

| Variable | Description |
|----------|-------------|
| DATABASE_URL | PostgreSQL connection string |
| SECRET_KEY | JWT secret key |
| ALGORITHM | JWT algorithm (default: HS256) |
| ACCESS_TOKEN_EXPIRE_MINUTES | Access token TTL |
| REFRESH_TOKEN_EXPIRE_DAYS | Refresh token TTL |
