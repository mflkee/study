import httpx
import pytest

from calculator_api.main import app, store


@pytest.fixture(autouse=True)
def reset_store() -> None:
    store.current_expression = None


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


@pytest.mark.anyio
async def test_add_operation(client: httpx.AsyncClient) -> None:
    response = await client.get("/operations/add", params={"a": 2, "b": 3})

    assert response.status_code == 200
    assert response.json()["result"] == 5


@pytest.mark.anyio
async def test_divide_by_zero_returns_error(client: httpx.AsyncClient) -> None:
    response = await client.get("/operations/divide", params={"a": 10, "b": 0})

    assert response.status_code == 400
    assert response.json()["detail"] == "Division by zero is not allowed."


@pytest.mark.anyio
async def test_set_and_get_current_expression(client: httpx.AsyncClient) -> None:
    set_response = await client.put("/expression", json={"expression": "(2 + 3) * 4"})
    get_response = await client.get("/expression")

    assert set_response.status_code == 200
    assert set_response.json() == {"expression": "(2 + 3) * 4"}
    assert get_response.status_code == 200
    assert get_response.json() == {"expression": "(2 + 3) * 4"}


@pytest.mark.anyio
async def test_parse_and_evaluate_complex_expression(client: httpx.AsyncClient) -> None:
    response = await client.post(
        "/expression/parse-and-evaluate",
        json={"expression": "(2 + 3) * 4 + (10 - 6) / 2"},
    )

    assert response.status_code == 200
    assert response.json() == {
        "expression": "(2 + 3) * 4 + (10 - 6) / 2",
        "result": 22,
    }


@pytest.mark.anyio
async def test_compose_append_and_evaluate_expression(
    client: httpx.AsyncClient,
) -> None:
    compose_response = await client.post(
        "/expression/compose",
        json={"left": "2 + 3", "op": "*", "right": 4},
    )
    append_response = await client.post(
        "/expression/append",
        json={"op": "+", "operand": "(10 - 6) / 2"},
    )
    evaluate_response = await client.post("/expression/evaluate")

    assert compose_response.status_code == 200
    assert append_response.status_code == 200
    assert evaluate_response.status_code == 200
    assert evaluate_response.json()["result"] == 22
