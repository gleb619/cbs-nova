---
name: chrome-debugging
description: >
  Debug web applications using Chrome DevTools MCP. Use when analyzing browser
  performance, network requests, console errors, or automating browser interactions.
  Keywords: chrome, browser, debugging, performance, network, screenshot, lighthouse.
---

# Chrome DevTools Debugging Skill

## When to Use This Skill

- Performance analysis of web pages
- Network request inspection
- Console error debugging
- Browser automation tasks
- Lighthouse audits
- Memory snapshot analysis

## Available Tools (via Chrome DevTools MCP)

This skill leverages the Chrome DevTools MCP server which provides:

### Input Automation

- `click` - Click on a page element by UID
- `fill` - Fill input, textarea, or select elements
- `type_text` - Type text via keyboard into a focused input
- `hover` - Hover over a page element
- `drag` - Drag an element onto another element
- `handle_dialog` - Accept or dismiss browser alerts/prompts
- `upload_file` - Upload a file through a file input element
- `press_key` - Press a key or key combination (e.g., Enter, Ctrl+A)

### Navigation

- `navigate_page` - Go to a URL, or back, forward, or reload
- `new_page` - Open a new tab and load a URL
- `list_pages` - Get a list of pages open in the browser
- `select_page` - Select a page as a context for future tool calls
- `close_page` - Close a page by its index
- `wait_for` - Wait for specified text to appear on the page
- `resize_page` - Resize the page viewport

### Debugging & Analysis

- `take_screenshot` - Capture page state (PNG/JPEG/WebP)
- `take_snapshot` - Get a text snapshot of the page based on the a11y tree (includes element UIDs)
- `list_console_messages` - List all console messages for the selected page
- `get_console_message` - Get a specific console message by its ID
- `evaluate_script` - Execute JavaScript in the page context
- `lighthouse_audit` - Run Lighthouse audits for accessibility, SEO, and best practices

### Performance

- `performance_start_trace` - Start a performance trace recording
- `performance_stop_trace` - Stop the active performance trace recording
- `performance_analyze_insight` - Get detailed info on a specific Performance Insight

### Memory

- `take_memory_snapshot` - Capture a heap snapshot for memory analysis

### Network

- `list_network_requests` - List all network requests for the selected page
- `get_network_request` - Get details of a specific network request

### Emulation

- `emulate` - Emulate device features (viewport, CPU throttling, network conditions, geolocation, color scheme, user
  agent)

## Usage Guidelines

1. **Always start with navigation**: Use `navigate_page(url)` to load the target URL first
2. **Take snapshots before actions**: Use `take_snapshot()` to capture state and obtain element UIDs
3. **Use element UIDs from snapshots**: All interaction tools (`click`, `fill`, etc.) require element `uid` from
   snapshots
4. **Handle async operations**: Use `wait_for(text=["..."])` after actions that trigger dynamic content loading
5. **Clean up**: Use `close_page` when finished with a tab
6. **Prefer batch form fills**: Use `fill_form` for efficient multi-field input instead of multiple `fill` calls
7. **Include snapshots for references**: Always pass `includeSnapshot=true` when taking screenshots if you need to
   reference element UIDs

## Example Workflow

```
User: "Why is my login page loading slowly?"

1. Navigate to the login page:
   navigate_page(url="https://example.com/login")

2. Start performance trace:
   performance_start_trace()

3. Capture initial state:
   take_snapshot()

4. Perform login actions:
   fill_form(elements=[
     {uid: "email-input-uid", value: "test@example.com"},
     {uid: "password-input-uid", value: "secret"}
   ])
   click(uid: "submit-button-uid")

5. Wait for redirect:
   wait_for(text=["Dashboard", "Welcome"])

6. Stop trace and analyze:
   performance_stop_trace()
   performance_analyze_insight(insightSetId="...", insightName="LCPBreakdown")

7. Return insights with recommendations
```

## Troubleshooting Tips

| Issue                 | Solution                                                                                          |
|-----------------------|---------------------------------------------------------------------------------------------------|
| Chrome doesn't launch | Ensure Chrome is installed and in PATH                                                            |
| Tools fail            | Check MCP connection via `qwen mcp list`                                                          |
| Headless issues       | Try `--headless=false` to see the browser UI                                                      |
| Element UID errors    | Always use `includeSnapshot=true` and reference UIDs from latest snapshot                         |
| Authentication fails  | Use `--autoConnect` to connect to pre-authenticated Chrome session                                |
| MCP disconnected      | Reconnect with `qwen mcp add chrome-devtools --transport stdio npx -y chrome-devtools-mcp@latest` |

## Security Notes

⚠️ Chrome DevTools MCP has access to all browser content. Never use with sensitive
credentials or private data unless running in isolated mode (`--isolated=true`).
