from __future__ import annotations

import os
import shutil
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Thread

import pytest
from selenium import webdriver


PROJECT_ROOT = Path(__file__).resolve().parents[1]
DIST_DIR = PROJECT_ROOT / "dist"


class QuietStaticFileHandler(SimpleHTTPRequestHandler):
    def log_message(self, format: str, *args: object) -> None:
        return


@pytest.fixture(scope="session")
def base_url() -> str:
    handler = partial(QuietStaticFileHandler, directory=str(DIST_DIR))
    server = ThreadingHTTPServer(("127.0.0.1", 0), handler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()

    try:
        port = server.server_address[1]
        yield f"http://127.0.0.1:{port}"
    finally:
        server.shutdown()
        thread.join(timeout=5)
        server.server_close()


def build_chrome_options() -> webdriver.ChromeOptions:
    options = webdriver.ChromeOptions()
    browser_path = (
        os.getenv("CHROME_BIN")
        or shutil.which("chromium")
        or shutil.which("google-chrome")
        or shutil.which("chrome")
    )

    if browser_path:
        options.binary_location = browser_path

    options.add_argument("--headless=new")
    options.add_argument("--window-size=1440,900")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")
    return options


@pytest.fixture
def driver() -> webdriver.Chrome:
    browser = webdriver.Chrome(options=build_chrome_options())
    browser.implicitly_wait(1)

    try:
        yield browser
    finally:
        browser.quit()
