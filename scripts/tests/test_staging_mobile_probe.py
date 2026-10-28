"""Offline staging-probe regression coverage; never contacts a real backend."""
import io
from pathlib import Path
import sys
import unittest
import urllib.error

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import staging_mobile_readonly_probe as staging


class FakeResponse(io.BytesIO):
    def __init__(self, payload: bytes, status: int=200):
        super().__init__(payload)
        self.status=status


class FakeOpener:
    def __init__(self, body: bytes=b"{}", status: int=200, error: int|None=None):
        self.body=body
        self.status=status
        self.error=error
        self.calls=0
        self.method=None
        self.authorization=None

    def open(self, request, timeout=0):
        self.calls+=1
        self.method=request.get_method()
        self.authorization=request.get_header("Authorization")
        if self.error:
            raise urllib.error.HTTPError(request.full_url,self.error,"Rejected",{},None)
        return FakeResponse(self.body,self.status)


class StagingProbeTest(unittest.TestCase):
    token="ab"*32
    workspace=(
        b'{"revision":13,"user":{"email":"private@example.test"},'
        b'"data":{"budgets":{},"categoryBudgets":{},"expenses":[]}}'
    )

    def test_origin_validation_rejects_insecure_and_embedded_credentials(self):
        self.assertEqual(staging.validated_origin("https://staging.example.test/"),
                         "https://staging.example.test")
        for value in ("http://staging.example.test","https://person:secret@staging.example.test",
                      "https://staging.example.test/api/mobile/workspace",
                      "https://staging.example.test/?token=leaked",
                      "file:///tmp/backup.json"):
            with self.subTest(origin=value),self.assertRaises(ValueError):
                staging.validated_origin(value)

    def test_unauthorized_workspace_is_expected_without_token(self):
        opener=FakeOpener(error=401)
        result=staging.probe("https://staging.example.test",None,opener)
        self.assertEqual(result["status"],401)
        self.assertEqual(opener.method,"GET")
        self.assertIsNone(opener.authorization)
        self.assertTrue(result["readOnly"])

    def test_read_only_authenticated_workspace_never_returns_sensitive_fields(self):
        opener=FakeOpener(self.workspace)
        result=staging.probe("https://staging.example.test",self.token,opener)
        self.assertTrue(result["revisionPresent"])
        self.assertEqual(opener.authorization,"Bearer "+self.token)
        self.assertEqual(opener.method,"GET")
        self.assertNotIn("email",str(result))
        self.assertNotIn("budgets",str(result))
        self.assertEqual(opener.calls,1)

    def test_publicly_accessible_workspace_is_a_hard_failure(self):
        with self.assertRaisesRegex(ValueError,"Unauthenticated"):
            staging.probe("https://staging.example.test",None,FakeOpener(self.workspace))

    def test_invalid_token_fails_before_network_request(self):
        opener=FakeOpener(self.workspace)
        with self.assertRaisesRegex(ValueError,"invalid format"):
            staging.probe("https://staging.example.test","wrong",opener)
        self.assertEqual(opener.calls,0)

    def test_missing_required_module_is_rejected(self):
        opener=FakeOpener(b'{"revision":1,"user":{"email":"x@example.test"},'
                          b'"data":{"budgets":{},"expenses":[]}}')
        with self.assertRaisesRegex(ValueError,"categoryBudgets"):
            staging.probe("https://staging.example.test",self.token,opener)

    def test_html_and_oversized_server_responses_cannot_be_parsed(self):
        for body in (b"<html>Not an API</html>",b"x"*(staging.MAX_RESPONSE_BYTES+1)):
            with self.subTest(length=len(body)),self.assertRaises(ValueError):
                staging.probe("https://staging.example.test",self.token,FakeOpener(body))

    def test_conflicts_or_unauthorized_test_token_fail_instead_of_retrying(self):
        for status in (401,409,429,500,503):
            with self.subTest(status=status),self.assertRaisesRegex(ValueError,"HTTP"):
                staging.probe("https://staging.example.test",self.token,
                              FakeOpener(error=status))


if __name__=="__main__":
    unittest.main()
