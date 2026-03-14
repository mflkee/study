from __future__ import annotations

from datetime import datetime, timezone

import httpx
import pytest

from user_registration_service.main import app
from user_registration_service.models import (
    UserRegistration,
    UserRegistrationPublic,
    create_recursive_data_model,
)
from user_registration_service.service import register_user


def valid_payload() -> dict[str, object]:
    return {
        "username": "student_01",
        "email": "student@example.com",
        "password": "SecurePass1",
        "password_confirm": "SecurePass1",
        "age": 21,
        "real_name": "Alex",
        "phone": "+7-999-12-34",
    }


@pytest.fixture
async def client() -> httpx.AsyncClient:
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(
        transport=transport,
        base_url="http://testserver",
    ) as async_client:
        yield async_client


@pytest.fixture
def anyio_backend() -> str:
    return "asyncio"


def test_register_user_returns_model_and_hides_password_confirm() -> None:
    before = datetime.now(timezone.utc)
    result = register_user(valid_payload())
    after = datetime.now(timezone.utc)

    assert isinstance(result, UserRegistration)
    assert before <= result.registration_date <= after
    assert result.model_dump()["password"] == "SecurePass1"
    assert "password_confirm" not in result.model_dump()


def test_register_user_returns_friendly_errors() -> None:
    payload = valid_payload()
    payload["password"] = "weakpass"

    result = register_user(payload)

    assert isinstance(result, list)
    assert "password: Пароль должен содержать хотя бы одну цифру." in result


def test_register_user_validates_new_requirements() -> None:
    payload = valid_payload()
    payload["real_name"] = "alex"
    payload["phone"] = "89991234"

    result = register_user(payload)

    assert isinstance(result, list)
    assert "real_name: Реальное имя должно начинаться с заглавной буквы." in result
    assert "phone: Телефон должен быть в формате +X-XXX-XX-XX." in result


@pytest.mark.anyio
async def test_register_endpoint_returns_public_user(
    client: httpx.AsyncClient,
) -> None:
    response = await client.post("/register", json=valid_payload())

    assert response.status_code == 201
    body = response.json()
    assert body["username"] == "student_01"
    assert body["email"] == "student@example.com"
    assert "password" not in body
    assert "password_confirm" not in body
    validated = UserRegistrationPublic.model_validate(body)
    assert validated.phone == "+7-999-12-34"


@pytest.mark.anyio
async def test_register_endpoint_returns_validation_errors(
    client: httpx.AsyncClient,
) -> None:
    payload = valid_payload()
    payload["password_confirm"] = "OtherPass1"

    response = await client.post("/register", json=payload)

    assert response.status_code == 422
    assert response.json()["detail"] == [
        "password_confirm: Поля password и password_confirm должны совпадать.",
    ]


def test_recursive_model_supports_arbitrary_nesting() -> None:
    RecursiveDataModel = create_recursive_data_model()
    payload = {
        "data": "root",
        "child": {
            "data": "middle",
            "child": {
                "data": "leaf",
                "child": None,
            },
        },
    }

    result = RecursiveDataModel.model_validate(payload)

    assert result.model_dump() == payload
