#!/usr/bin/env python3
"""Одноразовый CORS-сервер: отдаёт файлы папки в браузер (для загрузки в LMS из JS).

Запуск:  python3 tools/cors_serve.py <папка> <порт>
Слушает только 127.0.0.1.
"""
import http.server
import pathlib
import socketserver
import sys
import urllib.parse


class Handler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def do_OPTIONS(self):  # noqa: N802
        self.send_response(204)
        self.end_headers()

    def log_message(self, fmt, *args):  # тише в логах
        sys.stderr.write("%s\n" % (fmt % args))


def main() -> None:
    root = pathlib.Path(sys.argv[1]).resolve()
    port = int(sys.argv[2])
    handler = lambda *a, **kw: Handler(*a, directory=str(root), **kw)  # noqa: E731
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("127.0.0.1", port), handler) as httpd:
        print(f"serving {root} on http://127.0.0.1:{port}", flush=True)
        httpd.serve_forever()


if __name__ == "__main__":
    main()
