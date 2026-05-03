#!/usr/bin/env node
/**
 * CBS-Nova Agent Tools MCP Server
 *
 * Exposes qwen_run and kiro_run as MCP tools via stdio transport.
 */

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { spawn } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import * as z from "zod";

// ─── Helpers ────────────────────────────────────────────────────────────────

const LOG_DIR = path.join(os.tmpdir(), "logs");
const KIRO_PATH = path.join(os.homedir(), ".local", "bin", "kiro-cli");
const OPENCODE_PATH = path.join(os.homedir(), ".opencode", "bin", "opencode");

function ensureLogDir() {
  if (!fs.existsSync(LOG_DIR)) {
    fs.mkdirSync(LOG_DIR, { recursive: true });
  }
}

function timestamp() {
  return new Date().toISOString();
}

function logPath(taskName) {
  return path.join(LOG_DIR, `${taskName}.log`);
}

function pidPath(taskName) {
  return path.join(LOG_DIR, `${taskName}.pid`);
}

/**
 * Spawn a command with NVM environment loaded.
 * Wraps the command in a bash login shell that sources nvm first.
 */
function spawnWithNvm(cmd, args, timeoutSec) {
  return new Promise((resolve, reject) => {
    const escapedArgs = args.map((a) => `'${a.replace(/'/g, "'\\''")}'`).join(" ");
    const bashCmd = `source ~/.nvm/nvm.sh && nvm use v22.20.0 --silent && ${cmd} ${escapedArgs}`;

    const child = spawn("bash", ["-c", bashCmd], {
      stdio: ["ignore", "pipe", "pipe"],
      timeout: timeoutSec * 1000,
    });

    let stdout = "";
    let stderr = "";

    child.stdout.on("data", (chunk) => {
      const text = chunk.toString();
      stdout += text;
    });

    child.stderr.on("data", (chunk) => {
      const text = chunk.toString();
      stderr += text;
      process.stderr.write(text);
    });

    child.on("error", reject);

    child.on("close", (code) => {
      resolve({ code, stdout, stderr });
    });

    child.on("timeout", () => {
      child.kill("SIGTERM");
      reject(new Error(`Process timed out after ${timeoutSec}s`));
    });
  });
}

/**
 * Spawn a command directly (no NVM wrapper).
 * Used for binaries like kiro-cli that don't need Node version switching.
 */
function spawnDirect(cmd, args, timeoutSec) {
  return new Promise((resolve, reject) => {
    const spawnOpts = { stdio: ["ignore", "pipe", "pipe"] };
    if (timeoutSec) spawnOpts.timeout = timeoutSec * 1000;

    const child = spawn(cmd, args, spawnOpts);

    let stdout = "";
    let stderr = "";

    child.stdout.on("data", (chunk) => {
      const text = chunk.toString();
      stdout += text;
    });

    child.stderr.on("data", (chunk) => {
      const text = chunk.toString();
      stderr += text;
      process.stderr.write(text);
    });

    child.on("error", reject);

    child.on("close", (code) => {
      resolve({ code, stdout, stderr });
    });

    child.on("timeout", () => {
      child.kill("SIGTERM");
      reject(new Error(`Process timed out after ${timeoutSec}s`));
    });
  });
}

/**
 * Write PID file while a task is running, clean up after.
 */
async function withPidFile(taskName, fn) {
  const pidFile = pidPath(taskName);
  fs.writeFileSync(pidFile, String(process.pid));
  try {
    return await fn();
  } finally {
    try {
      fs.unlinkSync(pidFile);
    } catch {
      // PID file may already be gone
    }
  }
}

// ─── MCP Server ─────────────────────────────────────────────────────────────

const server = new McpServer({
  name: "cbs-nova-agent-tools",
  version: "1.0.0",
});

// ── Tool: qwen_run ──────────────────────────────────────────────────────────

server.registerTool(
  "qwen_run",
  {
    description:
      "Execute a task by invoking the Qwen CLI executor. Reads a task file from docs/tasks/{task_name}.md and runs Qwen to implement it.",
    inputSchema: {
      task_name: z.string().describe(
        "Name of the task (without .md extension). The task file must exist at docs/tasks/{task_name}.md"
      ),
      timeout_sec: z.number().optional().describe("Timeout in seconds. Defaults to 300."),
      args: z.array(z.string()).optional().describe(
        "Additional CLI arguments to pass to qwen (e.g. ['-y', '--output-format', 'text'])"
      ),
      prompt: z.string().optional().describe(
        "Optional prompt to pass to qwen. If provided, overrides the default task execution prompt."
      ),
    },
  },
  async ({ task_name, timeout_sec, args, prompt }) => {
    ensureLogDir();
    const timeout = timeout_sec ?? 300;
    const extraArgs = args ?? [];
    const logFile = logPath(task_name);

    const startMsg = `[${timestamp()}] START task=${task_name} timeout=${timeout}s`;
    process.stderr.write(startMsg + "\n");

    const logStream = fs.createWriteStream(logFile, { flags: "a" });
    logStream.write(startMsg + "\n");

    let output;
    try {
      output = await withPidFile(task_name, async () => {
        // Build qwen command args
        const defaultPrompt = `Read the file docs/tasks/${task_name}.md carefully and follow all instructions inside it exactly. After completing all work, write your result summary to docs/results/${task_name}.result.md as instructed.`;
        const qwenArgs = [
          "-y",
          prompt || defaultPrompt,
          "--output-format",
          "text",
          ...extraArgs,
        ];

        const result = await spawnWithNvm("qwen", qwenArgs, timeout);

        const endMsg = `[${timestamp()}] END task=${task_name} exit_code=${result.code}`;
        logStream.write(endMsg + "\n");
        process.stderr.write(endMsg + "\n");

        if (result.code !== 0) {
          logStream.write(`STDERR: ${result.stderr}\n`);
          throw new Error(
            `Qwen exited with code ${result.code}\n\nOutput:\n${result.stdout}\n\nErrors:\n${result.stderr}`
          );
        }

        return result.stdout;
      });
    } catch (err) {
      const errMsg = err.message || String(err);
      const failMsg = `[${timestamp()}] FAILED task=${task_name} error=${errMsg}`;
      logStream.write(failMsg + "\n");
      process.stderr.write(failMsg + "\n");

      return {
        content: [
          {
            type: "text",
            text: `## Qwen Execution Failed: ${task_name}\n\n**Error:** ${errMsg}`,
          },
        ],
        isError: true,
      };
    } finally {
      logStream.end();
    }

    const truncatedOutput = output.length > 3000 ? output.substring(0, 3000) + "\n... (truncated)" : output;
    return {
      content: [
        {
          type: "text",
          text: `## Qwen Execution: ${task_name}\n\nCompleted successfully. Check logs at ${logFile} for full output.\n\n**Output:**\n${truncatedOutput}`,
        },
      ],
    };
  }
);

// ── Tool: opencode_run ─────────────────────────────────────────────────────

server.registerTool(
  "opencode_run",
  {
    description:
      "Execute a task by invoking the OpenCode CLI executor. Reads a task file from docs/tasks/{task_name}.md and runs OpenCode to implement it.",
    inputSchema: {
      task_name: z.string().describe(
        "Name of the task (without .md extension). The task file must exist at docs/tasks/{task_name}.md"
      ),
      timeout_sec: z.number().optional().describe("Timeout in seconds. Defaults to 300."),
      args: z.array(z.string()).optional().describe(
        "Additional CLI arguments to pass to opencode (e.g. ['-c'] to continue last session)"
      ),
      prompt: z.string().optional().describe(
        "Optional prompt override. Defaults to instructing OpenCode to read and execute the task file."
      ),
    },
  },
  async ({ task_name, timeout_sec, args, prompt }) => {
    ensureLogDir();
    const timeout = timeout_sec ?? 300;
    const extraArgs = args ?? [];
    const logFile = logPath(task_name);

    const startMsg = `[${timestamp()}] START opencode task=${task_name} timeout=${timeout}s`;
    process.stderr.write(startMsg + "\n");

    const logStream = fs.createWriteStream(logFile, { flags: "a" });
    logStream.write(startMsg + "\n");

    let output;
    try {
      output = await withPidFile(task_name, async () => {
        const defaultPrompt = `Read the file docs/tasks/${task_name}.md carefully and follow all instructions inside it exactly. After completing all work, write your result summary to docs/results/${task_name}.result.md as instructed.`;
        const resolvedPrompt = prompt || defaultPrompt;

        const opencodeArgs = ["run", "--dangerously-skip-permissions", ...extraArgs, resolvedPrompt];

        // Check opencode path exists
        if (!fs.existsSync(OPENCODE_PATH)) {
          throw new Error(
            `opencode binary not found at ${OPENCODE_PATH}. Please ensure it is installed.`
          );
        }

        const result = await spawnDirect(OPENCODE_PATH, opencodeArgs, timeout);

        const endMsg = `[${timestamp()}] END opencode task=${task_name} exit_code=${result.code}`;
        logStream.write(endMsg + "\n");
        process.stderr.write(endMsg + "\n");

        if (result.code !== 0) {
          logStream.write(`STDERR: ${result.stderr}\n`);
          throw new Error(
            `OpenCode exited with code ${result.code}\n\nOutput:\n${result.stdout}\n\nErrors:\n${result.stderr}`
          );
        }

        return result.stdout;
      });
    } catch (err) {
      const errMsg = err.message || String(err);
      const failMsg = `[${timestamp()}] FAILED opencode task=${task_name} error=${errMsg}`;
      logStream.write(failMsg + "\n");
      process.stderr.write(failMsg + "\n");

      return {
        content: [
          {
            type: "text",
            text: `## OpenCode Execution Failed: ${task_name}\n\n**Error:** ${errMsg}`,
          },
        ],
        isError: true,
      };
    } finally {
      logStream.end();
    }

    const truncatedOutput = output.length > 3000 ? output.substring(0, 3000) + "\n... (truncated)" : output;
    return {
      content: [
        {
          type: "text",
          text: `## OpenCode Execution: ${task_name}\n\nCompleted successfully. Check logs at ${logFile} for full output.\n\n**Output:**\n${truncatedOutput}`,
        },
      ],
    };
  }
);

// ── Tool: kiro_run ──────────────────────────────────────────────────────────

server.registerTool(
  "kiro_run",
  {
    description:
      "Invoke the Kiro CLI to scan the codebase and generate a structured task specification.",
    inputSchema: {
      args: z.array(z.string()).describe("Arguments to pass to kiro-cli"),
      task_id: z.string().optional().describe("Optional task ID for tracking"),
    },
  },
  async ({ args, task_id }) => {
    ensureLogDir();
    const kiroArgs = args ?? [];
    const taskLabel = task_id ?? "kiro";
    const logFile = logPath(taskLabel);

    const startMsg = `[${timestamp()}] START kiro_run task_id=${taskLabel}`;
    process.stderr.write(startMsg + "\n");

    const logStream = fs.createWriteStream(logFile, { flags: "a" });
    logStream.write(startMsg + "\n");

    let output;
    try {
      // Check kiro-cli exists
      if (!fs.existsSync(KIRO_PATH)) {
        throw new Error(
          `kiro-cli not found at ${KIRO_PATH}. Please ensure it is installed.`
        );
      }

      output = await withPidFile(taskLabel, async () => {
        const result = await spawnDirect(KIRO_PATH, kiroArgs);

        const endMsg = `[${timestamp()}] END kiro_run task_id=${taskLabel} exit_code=${result.code}`;
        logStream.write(endMsg + "\n");
        process.stderr.write(endMsg + "\n");

        if (result.code !== 0) {
          logStream.write(`STDERR: ${result.stderr}\n`);
          throw new Error(
            `Kiro exited with code ${result.code}\n\nOutput:\n${result.stdout}\n\nErrors:\n${result.stderr}`
          );
        }

        return result.stdout;
      });
    } catch (err) {
      const errMsg = err.message || String(err);
      const failMsg = `[${timestamp()}] FAILED kiro_run task_id=${taskLabel} error=${errMsg}`;
      logStream.write(failMsg + "\n");
      process.stderr.write(failMsg + "\n");

      return {
        content: [
          {
            type: "text",
            text: `## Kiro Execution Failed\n\n**Error:** ${errMsg}`,
          },
        ],
        isError: true,
      };
    } finally {
      logStream.end();
    }

    const truncatedOutput = (output || "").length > 3000 ? output.substring(0, 3000) + "\n... (truncated)" : output;
    return {
      content: [
        {
          type: "text",
          text: `## Kiro Execution\n\nCompleted successfully. Check logs at ${logFile} for full output.\n\n**Output:**\n${truncatedOutput}`,
        },
      ],
    };
  }
);

// ─── Start Server ───────────────────────────────────────────────────────────

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  process.stderr.write("[mcp-server] Agent tools MCP server running on stdio\n");
}

main().catch((err) => {
  process.stderr.write(`[mcp-server] Fatal error: ${err.message}\n`);
  process.exit(1);
});
