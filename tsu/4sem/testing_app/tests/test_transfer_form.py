from __future__ import annotations

from urllib.parse import urlencode

from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait


def open_transfer_form(driver, base_url: str, *, balance: int, reserved: int) -> None:
    query = urlencode({"balance": balance, "reserved": reserved})
    driver.get(f"{base_url}/?{query}")
    WebDriverWait(driver, 10).until(
        EC.element_to_be_clickable((By.XPATH, "//h2[normalize-space()='Рубли']"))
    ).click()
    WebDriverWait(driver, 10).until(
        EC.visibility_of_element_located(
            (By.CSS_SELECTOR, "input[placeholder='0000 0000 0000 0000']")
        )
    )


def enter_card_number(driver, number: str):
    field = driver.find_element(By.CSS_SELECTOR, "input[placeholder='0000 0000 0000 0000']")
    field.clear()
    field.send_keys(number)
    return field


def enter_amount(driver, amount: str):
    field = WebDriverWait(driver, 10).until(
        EC.visibility_of_element_located((By.CSS_SELECTOR, "input[placeholder='1000']"))
    )
    field.clear()
    field.send_keys(amount)
    return field


def transfer_button(driver):
    buttons = driver.find_elements(By.XPATH, "//button[normalize-space()='Перевести']")
    return buttons[0] if buttons else None


def insufficient_funds_messages(driver):
    return driver.find_elements(
        By.XPATH, "//*[contains(text(),'Недостаточно средств на счете')]"
    )


def test_valid_transfer_below_limit_is_available(driver, base_url: str) -> None:
    open_transfer_form(driver, base_url, balance=30000, reserved=20001)
    enter_card_number(driver, "1234567890123456")
    enter_amount(driver, "1000")

    button = transfer_button(driver)
    assert button is not None
    assert not insufficient_funds_messages(driver)


def test_card_number_requires_exactly_16_digits(driver, base_url: str) -> None:
    open_transfer_form(driver, base_url, balance=30000, reserved=20001)
    card_field = enter_card_number(driver, "12345678901234567")

    digits_only = card_field.get_attribute("value").replace(" ", "")
    amount_fields = driver.find_elements(By.CSS_SELECTOR, "input[placeholder='1000']")

    assert digits_only == "1234567890123456"
    assert not amount_fields


def test_commission_is_rounded_down_to_full_rubles(driver, base_url: str) -> None:
    open_transfer_form(driver, base_url, balance=30000, reserved=20001)
    enter_card_number(driver, "1234567890123456")
    enter_amount(driver, "1099")

    commission = driver.find_element(By.ID, "comission").text
    assert commission == "109"


def test_transfer_is_allowed_when_total_equals_available_balance(
    driver, base_url: str
) -> None:
    open_transfer_form(driver, base_url, balance=5000, reserved=3900)
    enter_card_number(driver, "1234567890123456")
    enter_amount(driver, "1000")

    button = transfer_button(driver)
    assert button is not None
    assert not insufficient_funds_messages(driver)
