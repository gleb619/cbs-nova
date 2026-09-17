import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from cbs_cli.openapi.classify import classify, load_spec  # noqa: E402


# ---------------------------------------------------------------------------
# Helpers – minimal OpenAPI-shaped dicts (only fields classify() reads)
# ---------------------------------------------------------------------------

def _path_with_request_schema(schema):
    """Build a paths dict with a GET operation carrying a requestBody JSON schema."""
    return {
        "/x": {
            "get": {
                "requestBody": {
                    "content": {
                        "application/json": {"schema": schema},
                    },
                },
            },
        },
    }


def _path_with_response_schema(status, schema):
    """Build a paths dict with a GET operation carrying a response JSON schema."""
    return {
        "/x": {
            "get": {
                "responses": {
                    status: {
                        "content": {
                            "application/json": {"schema": schema},
                        },
                    },
                },
            },
        },
    }


# ---------------------------------------------------------------------------
# Path-level rules
# ---------------------------------------------------------------------------

class TestPathRules:
    def test_path_added(self):
        old = {"paths": {"/a": {}}}
        new = {"paths": {"/a": {}, "/b": {}}}
        breaking, additive = classify(old, new)
        assert any("path added: /b" in m for m in additive)
        assert not breaking

    def test_path_removed(self):
        old = {"paths": {"/a": {}, "/b": {}}}
        new = {"paths": {"/a": {}}}
        breaking, additive = classify(old, new)
        assert any("path removed: /b" in m for m in breaking)
        assert not additive

    def test_no_paths(self):
        assert classify({}, {}) == ([], [])


# ---------------------------------------------------------------------------
# Operation-level rules
# ---------------------------------------------------------------------------

class TestOperationRules:
    def test_operation_added(self):
        old = {"paths": {"/x": {"get": {}}}}
        new = {"paths": {"/x": {"get": {}, "post": {}}}}
        breaking, additive = classify(old, new)
        assert any("operation added: POST /x" in m for m in additive)
        assert not breaking

    def test_operation_removed(self):
        old = {"paths": {"/x": {"get": {}, "post": {}}}}
        new = {"paths": {"/x": {"get": {}}}}
        breaking, additive = classify(old, new)
        assert any("operation removed: POST /x" in m for m in breaking)
        assert not additive

    def test_non_http_methods_ignored(self):
        """Keys like 'parameters' or 'summary' are not treated as operations."""
        old = {"paths": {"/x": {"get": {}, "parameters": []}}}
        new = {"paths": {"/x": {"get": {}}}}
        breaking, additive = classify(old, new)
        assert not breaking
        assert not additive


# ---------------------------------------------------------------------------
# Schema property rules
# ---------------------------------------------------------------------------

class TestSchemaPropertyRules:
    def test_property_added(self):
        old = {"paths": _path_with_request_schema({"type": "object", "properties": {"a": {"type": "string"}}})}
        new = {"paths": _path_with_request_schema({"type": "object", "properties": {"a": {"type": "string"}, "b": {"type": "integer"}}})}
        breaking, additive = classify(old, new)
        assert any("property added" in m and "b" in m for m in additive)
        assert not breaking

    def test_property_removed(self):
        old = {"paths": _path_with_request_schema({"type": "object", "properties": {"a": {"type": "string"}, "b": {"type": "integer"}}})}
        new = {"paths": _path_with_request_schema({"type": "object", "properties": {"a": {"type": "string"}}})}
        breaking, additive = classify(old, new)
        assert any("property removed" in m and "b" in m for m in breaking)
        assert not additive

    def test_property_type_changed(self):
        old = {"paths": _path_with_request_schema({"type": "object", "properties": {"a": {"type": "string"}}})}
        new = {"paths": _path_with_request_schema({"type": "object", "properties": {"a": {"type": "integer"}}})}
        breaking, additive = classify(old, new)
        assert any("property type changed" in m and "string" in m and "integer" in m for m in breaking)
        assert not additive


# ---------------------------------------------------------------------------
# Required-property rules
# ---------------------------------------------------------------------------

class TestRequiredRules:
    def test_required_added(self):
        old = {"paths": _path_with_request_schema({"type": "object", "properties": {"a": {}, "b": {}}})}
        new = {"paths": _path_with_request_schema({"type": "object", "properties": {"a": {}, "b": {}}, "required": ["a"]})}
        breaking, additive = classify(old, new)
        assert any("required property added" in m and "a" in m for m in additive)
        assert not breaking

    def test_required_removed(self):
        old = {"paths": _path_with_request_schema({"type": "object", "properties": {"a": {}, "b": {}}, "required": ["a"]})}
        new = {"paths": _path_with_request_schema({"type": "object", "properties": {"a": {}, "b": {}}})}
        breaking, additive = classify(old, new)
        assert any("required property removed" in m and "a" in m for m in breaking)
        assert not additive


# ---------------------------------------------------------------------------
# Enum rules
# ---------------------------------------------------------------------------

class TestEnumRules:
    def test_enum_value_added(self):
        old = {"paths": _path_with_response_schema("200", {"type": "string", "enum": ["x"]})}
        new = {"paths": _path_with_response_schema("200", {"type": "string", "enum": ["x", "y"]})}
        breaking, additive = classify(old, new)
        assert any("enum value added" in m and "y" in m for m in additive)
        assert not breaking

    def test_enum_value_removed(self):
        old = {"paths": _path_with_response_schema("200", {"type": "string", "enum": ["x", "y"]})}
        new = {"paths": _path_with_response_schema("200", {"type": "string", "enum": ["x"]})}
        breaking, additive = classify(old, new)
        assert any("enum value removed" in m and "y" in m for m in breaking)
        assert not additive


# ---------------------------------------------------------------------------
# Schema removed entirely
# ---------------------------------------------------------------------------

class TestSchemaRemoved:
    def test_response_schema_removed(self):
        old = {"paths": {"/x": {"get": {"responses": {"200": {"content": {"application/json": {"schema": {"type": "object"}}}}}}}}}
        new = {"paths": {"/x": {"get": {"responses": {"200": {"content": {"application/json": {}}}}}}}}
        breaking, additive = classify(old, new)
        assert any("schema removed" in m for m in breaking)


# ---------------------------------------------------------------------------
# _schema_props defensive branch (non-dict input)
# ---------------------------------------------------------------------------

class TestSchemaPropsEdgeCases:
    def test_non_dict_schema_does_not_crash(self):
        """Passing a non-dict schema to _diff_schema should not crash."""
        from cbs_cli.openapi.classify import _diff_schema
        breaking: list[str] = []
        additive: list[str] = []
        _diff_schema("not-a-dict", {"type": "object", "properties": {"a": {}}}, "label", breaking, additive)
        # Non-dict old => empty props/required, enum=None
        # New has property 'a' => additive
        assert any("property added" in m and "a" in m for m in additive)
        assert not breaking

    def test_both_non_dict_no_crash(self):
        from cbs_cli.openapi.classify import _diff_schema
        breaking: list[str] = []
        additive: list[str] = []
        _diff_schema("old", "new", "label", breaking, additive)
        assert breaking == []
        assert additive == []


# ---------------------------------------------------------------------------
# load_spec
# ---------------------------------------------------------------------------

class TestLoadSpec:
    def test_valid_json(self, tmp_path):
        p = tmp_path / "spec.json"
        p.write_text('{"openapi": "3.0.0", "paths": {}}')
        result = load_spec(str(p))
        assert result == {"openapi": "3.0.0", "paths": {}}

    def test_invalid_json_exits(self, tmp_path):
        p = tmp_path / "bad.json"
        p.write_text("{not valid json")
        with pytest.raises(SystemExit) as exc_info:
            load_spec(str(p))
        assert exc_info.value.code == 2

    def test_missing_file_exits(self):
        with pytest.raises(SystemExit) as exc_info:
            load_spec("/nonexistent/path/spec.json")
        assert exc_info.value.code == 2
