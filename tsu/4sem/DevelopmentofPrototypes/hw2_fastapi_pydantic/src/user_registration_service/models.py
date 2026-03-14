from __future__ import annotations

import re
from datetime import datetime, timezone
from typing import Any

from pydantic import (
    BaseModel,
    ConfigDict,
    EmailStr,
    Field,
    ValidationInfo,
    field_validator,
)

USERNAME_PATTERN = re.compile(r"^[A-Za-z0-9_]+$")
PHONE_PATTERN = re.compile(r"^\+\d-\d{3}-\d{2}-\d{2}$")


class UserRegistration(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)

    username: str
    email: EmailStr
    password: str = Field(repr=False)
    password_confirm: str = Field(exclude=True, repr=False)
    age: int
    registration_date: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
    )
    real_name: str
    phone: str

    @field_validator("username")
    @classmethod
    def validate_username(cls, value: str) -> str:
        if not 3 <= len(value) <= 20:
            raise ValueError(
                "Имя пользователя должно содержать от 3 до 20 символов.",
            )
        if not USERNAME_PATTERN.fullmatch(value):
            raise ValueError(
                "Имя пользователя может содержать только латинские буквы, цифры и _.",
            )
        return value

    @field_validator("password")
    @classmethod
    def validate_password(cls, value: str) -> str:
        if len(value) < 8:
            raise ValueError("Пароль должен содержать минимум 8 символов.")
        if not any(char.isdigit() for char in value):
            raise ValueError("Пароль должен содержать хотя бы одну цифру.")
        if not any(char.islower() for char in value):
            raise ValueError("Пароль должен содержать хотя бы одну строчную букву.")
        if not any(char.isupper() for char in value):
            raise ValueError("Пароль должен содержать хотя бы одну заглавную букву.")
        return value

    @field_validator("password_confirm")
    @classmethod
    def validate_password_confirm(
        cls,
        value: str,
        info: ValidationInfo,
    ) -> str:
        password = info.data.get("password")
        if password is not None and value != password:
            raise ValueError("Поля password и password_confirm должны совпадать.")
        return value

    @field_validator("age")
    @classmethod
    def validate_age(cls, value: int) -> int:
        if not 18 <= value <= 120:
            raise ValueError("Возраст должен быть в диапазоне от 18 до 120 лет.")
        return value

    @field_validator("real_name")
    @classmethod
    def validate_real_name(cls, value: str) -> str:
        if len(value) < 2:
            raise ValueError("Реальное имя должно содержать минимум 2 символа.")
        if not value[0].isupper():
            raise ValueError("Реальное имя должно начинаться с заглавной буквы.")
        return value

    @field_validator("phone")
    @classmethod
    def validate_phone(cls, value: str) -> str:
        if not PHONE_PATTERN.fullmatch(value):
            raise ValueError("Телефон должен быть в формате +X-XXX-XX-XX.")
        return value


class UserRegistrationPublic(BaseModel):
    username: str
    email: EmailStr
    age: int
    registration_date: datetime
    real_name: str
    phone: str

    @classmethod
    def from_registration(
        cls,
        registration: UserRegistration,
    ) -> UserRegistrationPublic:
        return cls.model_validate(registration.model_dump(exclude={"password"}))


def create_recursive_data_model() -> type[BaseModel]:
    class RecursiveDataModel(BaseModel):
        data: Any
        child: RecursiveDataModel | None = None

    RecursiveDataModel.model_rebuild(
        _types_namespace={"RecursiveDataModel": RecursiveDataModel},
    )
    return RecursiveDataModel
