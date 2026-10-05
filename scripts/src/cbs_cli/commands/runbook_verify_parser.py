"""Parser / classifier for fenced curl examples in docs/runbook.md."""

import os
import re
import shlex
from dataclasses import dataclass
from typing import Iterator, List, Optional
from urllib.parse import urlparse, urlunparse


@dataclass
class CurlExample:
    command: str
    url: Optional[str] = None
    method: str = "GET"
    skip_reason: Optional[str] = None
    line_number: int = 0


# Short/long curl flags that consume the next token as an argument.
FLAGS_WITH_ARGS = frozenset({
    "-X", "--request",
    "-H", "--header",
    "-d", "--data", "--data-binary", "--data-raw", "--data-urlencode",
    "--form", "-F",
    "-T", "--upload-file",
    "-o", "--output",
    "-w", "--write-out",
    "-u", "--user",
    "-K", "--config",
    "--url",
})

WRITE_METHODS = frozenset({"POST", "PUT", "PATCH", "DELETE"})

DEFAULT_BACKEND_NETLOC = "localhost:8090"
DEFAULT_BFF_NETLOC = "localhost:3000"

FENCE_START = re.compile(r"^[ \t]*```bash\s*$", re.MULTILINE)
FENCE_END = re.compile(r"^[ \t]*```\s*$", re.MULTILINE)

SHELL_METAS = frozenset({"|", ">", "<", "&&", "||", ";"})


def extract_curl_examples(text: str, include_writes: bool = False) -> Iterator[CurlExample]:
    lines = text.splitlines()
    i = 0
    while i < len(lines):
        if FENCE_START.match(lines[i]):
            start = i
            i += 1
            while i < len(lines) and not FENCE_END.match(lines[i]):
                i += 1
            content = lines[start + 1:i]
            yield from _parse_block(content, start + 2, include_writes=include_writes)
            i += 1
            continue
        i += 1


def _parse_block(
    lines: List[str], first_line: int, include_writes: bool = False
) -> Iterator[CurlExample]:
    if not lines:
        return

    indents = [len(line) - len(line.lstrip()) for line in lines if line.strip()]
    if not indents:
        return

    min_indent = min(indents)
    dedented = [line[min_indent:] for line in lines]

    buffer: List[str] = []
    command_start = first_line
    for idx, raw in enumerate(dedented):
        stripped = raw.strip()
        if not stripped or stripped.startswith("#"):
            if buffer:
                yield _parse_command(
                    " ".join(buffer), command_start, include_writes=include_writes
                )
                buffer = []
            continue

        if not buffer:
            command_start = first_line + idx

        buffer.append(raw.rstrip("\\").rstrip())
        if raw.rstrip().endswith("\\") or _has_open_quote(" ".join(buffer)):
            continue

        yield _parse_command(
            " ".join(buffer), command_start, include_writes=include_writes
        )
        buffer = []

    if buffer:
        yield _parse_command(
            " ".join(buffer), command_start, include_writes=include_writes
        )


def _has_open_quote(text: str) -> bool:
    """True when ``text`` ends inside an unterminated quote (multi-line -d body)."""
    try:
        shlex.split(text)
    except ValueError:
        return True
    return False


def _parse_command(
    text: str, line_number: int, include_writes: bool = False
) -> CurlExample:
    # Stop tokenising at shell metacharacters (pipes, redirects, etc.).
    tokens: List[str] = []
    try:
        split = shlex.split(text)
    except ValueError:
        return CurlExample(
            command=text.strip(),
            skip_reason="unparseable shell quoting",
            line_number=line_number,
        )
    for token in split:
        if token in SHELL_METAS:
            break
        tokens.append(token)

    if not tokens or tokens[0] != "curl":
        return CurlExample(
            command=text.strip(),
            skip_reason="not a curl command",
            line_number=line_number,
        )

    return _classify(tokens, text.strip(), line_number, include_writes=include_writes)


def _classify(
    tokens: List[str],
    command: str,
    line_number: int,
    include_writes: bool = False,
) -> CurlExample:
    method, url, has_data = _parse_curl_tokens(tokens)

    placeholder = _find_unresolved_placeholder(command)
    if placeholder:
        return CurlExample(
            command=command,
            url=url,
            method=method,
            skip_reason=f"unresolved placeholder {placeholder}",
            line_number=line_number,
        )

    if not include_writes:
        if method in WRITE_METHODS:
            return CurlExample(
                command=command,
                url=url,
                method=method,
                skip_reason=f"write method {method}",
                line_number=line_number,
            )

        if has_data:
            return CurlExample(
                command=command,
                url=url,
                method=method,
                skip_reason="request body",
                line_number=line_number,
            )

        if method != "GET":
            return CurlExample(
                command=command,
                url=url,
                method=method,
                skip_reason=f"method {method} not read-only",
                line_number=line_number,
            )

    return CurlExample(command=command, url=url, method=method, line_number=line_number)


def _parse_curl_tokens(tokens: List[str]) -> tuple:
    method = "GET"
    url: Optional[str] = None
    has_data = False
    i = 1
    n = len(tokens)

    while i < n:
        token = tokens[i]

        if token == "-X" or token == "--request":
            if i + 1 < n:
                method = tokens[i + 1].upper()
            i += 2
            continue

        if token == "--url":
            if i + 1 < n:
                url = tokens[i + 1]
            i += 2
            continue

        if token in ("-H", "--header"):
            i += 2
            continue

        if token.startswith("-H") and len(token) > 2:
            i += 1
            continue

        if (
            token.startswith("-d")
            or token.startswith("--data")
            or token == "--form"
            or token.startswith("-F")
            or token == "-T"
            or token.startswith("--upload-file")
        ):
            has_data = True
            if "=" in token or (len(token) > 2 and token[:2] in ("-d", "-F", "-T")):
                i += 1
            else:
                i += 2
            continue

        if token in FLAGS_WITH_ARGS:
            i += 2
            continue

        if token.startswith("-"):
            i += 1
            continue

        if url is None:
            url = token
        i += 1

    return method, url, has_data


def _find_unresolved_placeholder(command: str) -> Optional[str]:
    angle = re.search(r"<[^>]+>", command)
    if angle:
        return angle.group(0)

    for match in re.finditer(
        r"\$\{([A-Za-z_][A-Za-z0-9_]*)\}|\$([A-Za-z_][A-Za-z0-9_]*)", command
    ):
        name = match.group(1) or match.group(2)
        if name not in os.environ:
            return f"${name}"
    return None


def rewrite_url(url: str, backend_url: str, bff_url: str) -> str:
    parsed = urlparse(url)
    if parsed.netloc == DEFAULT_BACKEND_NETLOC:
        base = urlparse(backend_url)
        parsed = parsed._replace(scheme=base.scheme or parsed.scheme, netloc=base.netloc)
    elif parsed.netloc == DEFAULT_BFF_NETLOC:
        base = urlparse(bff_url)
        parsed = parsed._replace(scheme=base.scheme or parsed.scheme, netloc=base.netloc)
    return urlunparse(parsed)
