import json
import os
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from unittest.mock import patch

from uengine_rpa.UEngineLibrary import UEngineLibrary


class _Handler(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass

    def do_GET(self):
        if self.path == "/page":
            payload = b"""<!doctype html><html><body>
                <h1 id='title'>Ready</h1><input id='name'>
                <select id='country'><option value='KR'>Korea</option></select>
                <button id='submit' onclick=\"document.querySelector('#title').textContent='Done'\">Submit</button>
            </body></html>"""
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(payload)
            return
        payload = json.dumps({"method": "GET"}).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(payload)

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = json.loads(self.rfile.read(length) or b"{}")
        payload = json.dumps({"method": "POST", "body": body}).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(payload)


class UEngineLibraryTest(unittest.TestCase):
    def test_file_and_json_actions_write_process_outputs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "output.json"
            library = UEngineLibrary()
            with patch.dict(os.environ, {"UENGINE_RPA_OUTPUT": str(output)}):
                source = root / "input" / "sample.txt"
                library.write_text_file(source, "hello")
                self.assertEqual("hello", library.read_text_file_as_output(source, "content"))

                copied = root / "copied" / "sample.txt"
                moved = root / "moved" / "sample.txt"
                library.copy_file(source, copied)
                library.move_file(copied, moved)
                files = library.list_folder_as_output(root, "files", "**/*.txt")
                self.assertIn(str(moved), files)

                value = library.json_value_as_output('{"data":{"items":[{"name":"Kim"}]}}', "data.items.0.name", "customer")
                self.assertEqual("Kim", value)
                saved = json.loads(output.read_text(encoding="utf-8"))
                self.assertEqual("hello", saved["content"])
                self.assertEqual("Kim", saved["customer"])

    def test_http_actions_save_status_and_body(self):
        server = ThreadingHTTPServer(("127.0.0.1", 0), _Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with tempfile.TemporaryDirectory() as directory:
                output = Path(directory) / "output.json"
                library = UEngineLibrary()
                base = f"http://127.0.0.1:{server.server_port}"
                with patch.dict(os.environ, {"UENGINE_RPA_OUTPUT": str(output)}):
                    get_value = library.http_get_as_output(base, "getResult")
                    post_value = library.http_post_json_as_output(base, '{"value":42}', "postResult")
                self.assertEqual(200, get_value["status"])
                self.assertEqual("GET", get_value["body"]["method"])
                self.assertEqual(42, post_value["body"]["body"]["value"])
        finally:
            server.shutdown()
            server.server_close()

    def test_browser_actions_use_shared_playwright_session(self):
        server = ThreadingHTTPServer(("127.0.0.1", 0), _Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                output = root / "output.json"
                screenshot = root / "screen.png"
                library = UEngineLibrary()
                with patch.dict(os.environ, {"UENGINE_RPA_OUTPUT": str(output), "UENGINE_RPA_HEADFUL": "0"}, clear=False):
                    library.open_browser(f"http://127.0.0.1:{server.server_port}/page")
                    library.wait_for_element("#title")
                    library.input_text("#name", "Kim")
                    library.select_option("#country", "KR")
                    library.click_element("#submit")
                    self.assertEqual("Done", library.save_element_text_as_output("#title", "title"))
                    library.take_browser_screenshot(screenshot)
                    library.close_browser()
                self.assertTrue(screenshot.exists())
                self.assertEqual("Done", json.loads(output.read_text(encoding="utf-8"))["title"])
        finally:
            server.shutdown()
            server.server_close()

    def test_general_action_keywords_are_exposed(self):
        expected = {
            "open_browser", "go_to_url", "click_element", "input_text", "select_option",
            "wait_for_element", "save_element_text_as_output", "take_browser_screenshot", "close_browser",
            "click_screen", "type_text", "press_key", "press_hotkey", "take_desktop_screenshot",
            "read_text_file_as_output", "write_text_file", "copy_file", "move_file", "list_folder_as_output",
            "http_get_as_output", "http_post_json_as_output", "set_process_output", "json_value_as_output",
        }
        self.assertTrue(expected.issubset(set(dir(UEngineLibrary))))


if __name__ == "__main__":
    unittest.main()
