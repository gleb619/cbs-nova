#!/usr/bin/env python3
"""T419 — coarse OpenAPI additive vs breaking-change classifier.

Compares two OpenAPI 3 JSON documents (positional arguments: <old.json>
<new.json>) and prints a categorized summary:

  BREAKING: a path removed | an operation (HTTP method) removed | a
            previously-`required` property removed | an enum value
            removed | a property `type` changed.
  ADDITIVE: a new path | a new operation | a new property | a new enum
            value.

Exit codes:
  0  only additive changes (or no changes)
  0  breaking changes present AND new.info.version != old.info.version
     (printed as `[warn] breaking changes but info.version bumped`)
  2  breaking changes present AND info.version unchanged

Phase-1 scope (intentionally coarse):
  - No `$ref` graph walk (components/schemas refs are treated as opaque
    schema names — if a referenced schema changes in place, it is NOT
    reported unless the inlined operation-level schema changes too).
  - No `oneOf`/`allOf`/`anyOf` deep semantics.
  - No request/response body parameter schema comparison beyond the
    `properties`/`required`/`enum`/`type` surface.
  - Operation removal is reported regardless of HTTP status code; the
    classifier does not attempt to detect "soft" deletions (deprecation,
    410 Gone, etc.).

Follow-ups: walk $ref graph, deep oneOf/allOf discrimination, request body
schema diffs, response header changes, security scheme drift. See
docs/plans/T419-openapi-drift-check.md.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

HTTP_METHODS = {"get", "put", "post", "delete", "options", "head", "patch", "trace"}


def _load(path: str) -> dict[str, Any]:
    try:
        return json.loads(Path(path).read_text())
    except Exception as e:
        print(f"[fail] could not parse {path}: {e}", file=sys.stderr)
        sys.exit(2)


def _schema_props(schema: Any) -> tuple[dict[str, Any], set[str], list[Any] | None]:
    """Return (properties, required-set, enum-or-None) for a JSON-schema-ish dict.

    Best-effort: missing keys produce empty defaults. Arrays (oneOf items)
    and `$ref` placeholders return empty property maps — that is the
    phase-1 limit, documented in the module docstring.
    """
    if not isinstance(schema, dict):
        return {}, set(), None
    props = schema.get("properties") or {}
    if not isinstance(props, dict):
        props = {}
    required = set(schema.get("required") or [])
    enum = schema.get("enum") if isinstance(schema.get("enum"), list) else None
    return props, required, enum


def _collect_schemas(op: dict[str, Any]) -> dict[str, Any]:
    """Flatten all JSON-schema-ish payloads under an operation into one map.

    Keys are descriptive labels (requestBody, responses.200, responses.404, ...)
    so the classifier output can pinpoint where a change happened. Phase-1
    limitation: only the FIRST media type per content block is inspected
    (the runner-fetched spec uses application/json exclusively, so this is
    not lossy in practice).
    """
    out: dict[str, Any] = {}
    rb = op.get("requestBody")
    if isinstance(rb, dict):
        content = rb.get("content") or {}
        for media, m in content.items():
            if isinstance(m, dict) and isinstance(m.get("schema"), dict):
                out[f"requestBody:{media}"] = m["schema"]
    responses = op.get("responses") or {}
    if isinstance(responses, dict):
        for status, resp in responses.items():
            if not isinstance(resp, dict):
                continue
            content = resp.get("content") or {}
            for media, m in content.items():
                if isinstance(m, dict) and isinstance(m.get("schema"), dict):
                    out[f"responses.{status}:{media}"] = m["schema"]
    return out


def _diff_schema(
    old: Any,
    new: Any,
    label: str,
    breaking: list[str],
    additive: list[str],
) -> None:
    old_props, old_req, old_enum = _schema_props(old)
    new_props, new_req, new_enum = _schema_props(new)

    for name in sorted(old_req - new_req):
        breaking.append(f"required property removed: {label}.{name}")
    for name in sorted(new_req - old_req):
        additive.append(f"required property added: {label}.{name}")

    for name in sorted(old_props.keys() - new_props.keys()):
        breaking.append(f"property removed: {label}.{name}")
    for name in sorted(new_props.keys() - old_props.keys()):
        additive.append(f"property added: {label}.{name}")
    for name in sorted(old_props.keys() & new_props.keys()):
        old_t = (old_props[name] or {}).get("type") if isinstance(old_props[name], dict) else None
        new_t = (new_props[name] or {}).get("type") if isinstance(new_props[name], dict) else None
        if old_t != new_t:
            breaking.append(
                f"property type changed: {label}.{name} {old_t!r} -> {new_t!r}"
            )

    if old_enum is not None or new_enum is not None:
        old_set = set(map(json.dumps, old_enum or []))
        new_set = set(map(json.dumps, new_enum or []))
        for v in sorted(old_set - new_set):
            breaking.append(f"enum value removed: {label}={v}")
        for v in sorted(new_set - old_set):
            additive.append(f"enum value added: {label}={v}")


def classify(old: dict[str, Any], new: dict[str, Any]) -> tuple[list[str], list[str]]:
    breaking: list[str] = []
    additive: list[str] = []

    old_paths = old.get("paths") or {}
    new_paths = new.get("paths") or {}

    for path in sorted(old_paths.keys() - new_paths.keys()):
        breaking.append(f"path removed: {path}")
    for path in sorted(new_paths.keys() - old_paths.keys()):
        additive.append(f"path added: {path}")

    for path in sorted(old_paths.keys() & new_paths.keys()):
        old_ops = old_paths[path] or {}
        new_ops = new_paths[path] or {}
        old_methods = {m for m in old_ops.keys() if m.lower() in HTTP_METHODS}
        new_methods = {m for m in new_ops.keys() if m.lower() in HTTP_METHODS}

        for method in sorted(old_methods - new_methods):
            breaking.append(f"operation removed: {method.upper()} {path}")
        for method in sorted(new_methods - old_methods):
            additive.append(f"operation added: {method.upper()} {path}")

        for method in sorted(old_methods & new_methods):
            old_op = old_ops.get(method) or {}
            new_op = new_ops.get(method) or {}

            schemas_old = _collect_schemas(old_op)
            schemas_new = _collect_schemas(new_op)

            for label, sob in schemas_old.items():
                snew = schemas_new.get(label)
                if snew is None:
                    breaking.append(f"schema removed: {method.upper()} {path} [{label}]")
                    continue
                _diff_schema(sob, snew, f"{method.upper()} {path} [{label}]", breaking, additive)

    return breaking, additive


def main() -> int:
    if len(sys.argv) != 3:
        print(f"usage: {sys.argv[0]} <old.json> <new.json>", file=sys.stderr)
        return 2

    old = _load(sys.argv[1])
    new = _load(sys.argv[2])

    breaking, additive = classify(old, new)

    old_v = (old.get("info") or {}).get("version")
    new_v = (new.get("info") or {}).get("version")

    print(f"info.version: {old_v!r} -> {new_v!r}")
    if breaking:
        print(f"BREAKING ({len(breaking)}):")
        for line in breaking:
            print(f"  - {line}")
    if additive:
        print(f"ADDITIVE ({len(additive)}):")
        for line in additive:
            print(f"  - {line}")
    if not breaking and not additive:
        print("[ok] no schema-shape changes")

    if not breaking:
        return 0
    if old_v != new_v:
        print("[warn] breaking changes but info.version bumped")
        return 0
    return 2


if __name__ == "__main__":
    sys.exit(main())
