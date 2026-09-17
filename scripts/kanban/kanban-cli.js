#!/usr/bin/env node
import fs from 'node:fs/promises';
import pathlib from 'node:path';
import { unified } from 'unified';
import remarkParse from 'remark-parse';
import remarkGfm from 'remark-gfm';
import remarkStringify from 'remark-stringify';
import { visit } from 'unist-util-visit';

const USAGE = `Usage: node kanban-cli.js <command> [file] [options]

Commands:
  list                          List all tasks
  next [--start]                Show next executable task; --start marks it In Progress
  show --id <id> [--with-plan]  Show task details and optional plan preview
  add [--id <id>] --title <title> [options]
                                Add a new task or update fields of an existing task
  status --id <id> --status <s> Change task status
  remove --id <id>              Remove a task by ID
  clean                         Remove all Done tasks

Options:
  --id <id>                     Task ID (omit to auto-generate on add)
  --title <title>               Task title
  --priority <High|Medium|Low>  Default: Low
  --owner <owner>               Default: loop
  --blocks <ids>                Comma-separated blocked task IDs
  --blockedBy <ids>             Comma-separated blocker task IDs
  --plan <path>                 Path to plan file
  --description <text>          Short description (max 128 chars)
  --help, -h                    Show this help
`;

const DEFAULT_FILE = 'docs/kanban.md';
const STATUSES = ['Backlog', 'In Progress', 'Blocked', 'Done'];
const PRIORITY_ORDER = { High: 0, Medium: 1, Low: 2 };

function parseArgs(argv) {
  const args = argv.slice(2);
  const opts = {};
  const positional = [];
  const boolFlags = new Set(['withPlan', 'with-plan', 'start']);
  for (let i = 0; i < args.length; i++) {
    const a = args[i];
    if (a.startsWith('--')) {
      const key = a.slice(2);
      let val = 'true';
      if (i + 1 < args.length && !args[i + 1].startsWith('--') && !boolFlags.has(key)) {
        val = args[i + 1];
        i++;
      }
      opts[key] = val;
    } else {
      positional.push(a);
    }
  }
  const cmd = positional[0] || 'list';
  let file = DEFAULT_FILE;
  if (opts.file) {
    file = opts.file;
    delete opts.file;
  } else if (positional.length > 1) {
    file = positional[positional.length - 1];
  }
  return { cmd, file, opts };
}

function cellText(cell) {
  const out = [];
  function walk(nodes) {
    for (const n of nodes || []) {
      if (n.type === 'text' || n.type === 'inlineCode') out.push(n.value);
      else if (n.children) walk(n.children);
    }
  }
  walk(cell.children);
  return out.join('').trim();
}

function setCellText(cell, text) {
  const escaped = String(text).replace(/(?<!\\)\|/g, '\\|');
  cell.children = [{ type: 'text', value: escaped }];
}

function makeCell(text) {
  // Escape only unescaped pipe characters so the Markdown table remains valid.
  // We do NOT escape backslashes themselves; callers already passed the intended
  // string value via the shell/CLI. Escaping is idempotent: a pipe preceded by a
  // backslash is left untouched.
  const escaped = String(text).replace(/(?<!\\)\|/g, '\\|');
  return { type: 'tableCell', children: [{ type: 'text', value: escaped }] };
}

function parseKanban(fileContent) {
  const processor = unified().use(remarkParse).use(remarkGfm).use(remarkStringify);
  const tree = processor.parse(fileContent);
  let tableNode = null;
  let headers = null;
  visit(tree, 'table', (node) => {
    if (!node.children || node.children.length === 0) return;
    const headerCells = node.children[0].children.map(cellText);
    if (headerCells.includes('ID') && headerCells.includes('Status')) {
      tableNode = node;
      headers = headerCells;
    }
  });
  if (!tableNode) throw new Error('No kanban task table found (expected ID + Status columns)');
  return { processor, tree, tableNode, headers };
}

function toObjects(tableNode, headers) {
  const rows = [];
  for (let i = 1; i < tableNode.children.length; i++) {
    const cells = tableNode.children[i].children;
    const obj = {};
    headers.forEach((h, idx) => (obj[h] = cellText(cells[idx])));
    rows.push(obj);
  }
  return rows;
}

function numericId(id) {
  const m = String(id).match(/^T(\d+)$/i);
  return m ? Number(m[1]) : 0;
}

function findHeaderIndex(headers, name) {
  return headers.indexOf(name);
}

function findRowById(tableNode, headers, id) {
  const idIdx = findHeaderIndex(headers, 'ID');
  for (let i = 1; i < tableNode.children.length; i++) {
    if (cellText(tableNode.children[i].children[idIdx]) === id) return i;
  }
  return -1;
}

function isUnblocked(task, all) {
  const raw = task['Blocked By'] || '';
  if (!raw || raw === '-') return true;
  const blockers = raw.split(/[,\s]+/).filter(Boolean);
  for (const b of blockers) {
    const dep = all.find(t => t.ID === b);
    if (!dep) continue;
    if (dep.Status !== 'Done') return false;
  }
  return true;
}

function nextExecutable(tasks) {
  const inProgress = tasks.find(t => t.Status === 'In Progress');
  if (inProgress) return { type: 'current', task: inProgress };
  const candidates = tasks.filter(t => t.Status === 'Backlog' && isUnblocked(t, tasks));
  if (candidates.length === 0) return null;
  candidates.sort((a, b) => {
    const pa = PRIORITY_ORDER[a.Priority] ?? 999;
    const pb = PRIORITY_ORDER[b.Priority] ?? 999;
    if (pa !== pb) return pa - pb;
    return numericId(a.ID) - numericId(b.ID);
  });
  return { type: 'next', task: candidates[0] };
}

async function read(file) {
  return await fs.readFile(file, 'utf-8');
}

async function save(file, processor, tree) {
  const md = processor.stringify(tree);
  await fs.writeFile(file, md, 'utf-8');
}

async function cmdList(file) {
  const content = await read(file);
  const { tableNode, headers } = parseKanban(content);
  const rows = toObjects(tableNode, headers);
  rows.forEach(r => {
    console.log(`${r.ID}\t${r.Status.padEnd(12)}\t${r.Priority.padEnd(6)}\t${r.Title} (${r['Blocked By'] || '-'})`);
  });
}

async function cmdNext(file, opts) {
  const content = await read(file);
  const { processor, tree, tableNode, headers } = parseKanban(content);
  const tasks = toObjects(tableNode, headers);
  const result = nextExecutable(tasks);
  if (!result) {
    console.log('No executable task found.');
    return;
  }
  const { type, task } = result;
  console.log(`${type === 'current' ? 'Current' : 'Next'}: ${task.ID} - ${task.Title} [${task.Priority}]`);
  if (opts.start && type === 'next') {
    const rowIdx = findRowById(tableNode, headers, task.ID);
    if (rowIdx === -1) throw new Error('Task row disappeared');
    const statusIdx = findHeaderIndex(headers, 'Status');
    setCellText(tableNode.children[rowIdx].children[statusIdx], 'In Progress');
    await save(file, processor, tree);
    console.log(`Marked ${task.ID} as In Progress.`);
  }
}

async function cmdStatus(file, opts) {
  const id = opts.id;
  const status = opts.status;
  if (!id || !status) throw new Error('Usage: status --id <id> --status <status>');
  if (!STATUSES.includes(status)) throw new Error(`Invalid status. Allowed: ${STATUSES.join(', ')}`);
  const content = await read(file);
  const { processor, tree, tableNode, headers } = parseKanban(content);
  const rowIdx = findRowById(tableNode, headers, id);
  if (rowIdx === -1) throw new Error(`Task ${id} not found`);
  const statusIdx = findHeaderIndex(headers, 'Status');
  setCellText(tableNode.children[rowIdx].children[statusIdx], status);
  await save(file, processor, tree);
  console.log(`Updated ${id} -> ${status}`);
}

async function cmdAdd(file, opts) {
  const content = await read(file);
  const { processor, tree, tableNode, headers } = parseKanban(content);
  const id = opts.id || (() => {
    let max = 0;
    const idIdx = findHeaderIndex(headers, 'ID');
    for (let i = 1; i < tableNode.children.length; i++) {
      const n = numericId(cellText(tableNode.children[i].children[idIdx]));
      if (n > max) max = n;
    }
    return `T${max + 1}`;
  })();
  const existingRowIdx = findRowById(tableNode, headers, id);
  const isUpdate = existingRowIdx !== -1;
  const values = {};
  const existing = isUpdate ? toObjects(tableNode, headers).find(t => t.ID === id) : null;
  values['ID'] = id;
  values['Status'] = existing?.Status || 'Backlog';
  values['Title'] = opts.title || existing?.Title || 'New task';
  const MAX_DESC = 128;
  const DESC_SUFFIX = '...';
  const descChars = opts.description !== undefined ? [...opts.description] : undefined;
  let description = existing?.Description || '-';
  if (descChars !== undefined) {
    description = descChars.join('');
    if (descChars.length > MAX_DESC) {
      description = descChars.slice(0, MAX_DESC - DESC_SUFFIX.length).join('') + DESC_SUFFIX;
      console.error(`[kanban-add] DESCRIPTION exceeds ${MAX_DESC} characters; trimmed and appended '${DESC_SUFFIX}'`);
    }
  }
  values['Description'] = description;
  values['Priority'] = opts.priority || existing?.Priority || 'Low';
  values['Owner'] = opts.owner || existing?.Owner || 'loop';
  values['Blocks'] = opts.blocks || existing?.Blocks || '-';
  values['Blocked By'] = opts.blockedBy || existing?.['Blocked By'] || '-';
  values['Plan File'] = opts.plan || existing?.['Plan File'] || '-';
  const newCells = headers.map(h => makeCell(values[h]));
  const newRow = { type: 'tableRow', children: newCells };
  const idIdx = findHeaderIndex(headers, 'ID');
  if (isUpdate) {
    tableNode.children[existingRowIdx] = newRow;
    await save(file, processor, tree);
    console.log(`Updated ${id}: ${values['Title']}`);
    return;
  }
  const newNum = numericId(id);
  let inserted = false;
  for (let i = 1; i < tableNode.children.length; i++) {
    if (numericId(cellText(tableNode.children[i].children[idIdx])) > newNum) {
      tableNode.children.splice(i, 0, newRow);
      inserted = true;
      break;
    }
  }
  if (!inserted) tableNode.children.push(newRow);
  await save(file, processor, tree);
  console.log(`Added ${id}: ${values['Title']}`);
}


async function cmdShow(file, opts) {
  const id = opts.id;
  if (!id) throw new Error('Usage: show --id <id> [file]');
  const content = await read(file);
  const { tableNode, headers } = parseKanban(content);
  const tasks = toObjects(tableNode, headers);
  const task = tasks.find(t => t.ID && t.ID.trim() === id.trim());
  if (!task) throw new Error(`Task ${id} not found`);

  const print = (label, value) => {
    const v = value !== undefined && value !== '' ? value : '-';
    console.log(`${label.padEnd(12)} ${v}`);
  };

  console.log('==> Task ' + id);
  print('ID:', task.ID);
  print('Status:', task.Status);
  print('Priority:', task.Priority);
  print('Title:', task.Title);
  print('Description:', task.Description);
  print('Owner:', task.Owner);
  print('Blocks:', task.Blocks);
  print('Blocked By:', task['Blocked By']);
  print('Plan File:', task['Plan File']);

  const withPlan = opts.withPlan || opts['with-plan'];
  if (withPlan && withPlan !== 'false' && task['Plan File'] && task['Plan File'] !== '-') {
    const planPath = task['Plan File'];
    let abs;
    if (pathlib.isAbsolute(planPath)) {
      abs = planPath;
    } else if (planPath.startsWith('./') || planPath.startsWith('../')) {
      abs = pathlib.resolve(planPath);
    } else {
      abs = pathlib.resolve(pathlib.dirname(pathlib.resolve(file)), planPath);
    }
    let exists = false;
    try {
      await fs.access(abs);
      exists = true;
    } catch {
      exists = false;
    }
    if (!exists) {
      console.log(`\n[warn] Plan file not found: ${abs}`);
      return;
    }
    const lines = (await fs.readFile(abs, 'utf-8')).split('\n');
    const preview = lines.slice(0, 100);
    console.log(`\n==> Plan preview (first 100 lines) — ${abs}`);
    console.log(preview.join('\n'));
    if (lines.length > 100) {
      console.log('...');
    }
    console.log(`\n[note] For full details, read the complete plan file: ${abs}`);
  }
}

async function cmdRemove(file, opts) {
  const id = opts.id;
  if (!id) throw new Error('Usage: remove --id <id> [file]');
  const content = await read(file);
  const { processor, tree, tableNode, headers } = parseKanban(content);
  const rowIdx = findRowById(tableNode, headers, id);
  if (rowIdx === -1) throw new Error(`Task ${id} not found`);
  tableNode.children.splice(rowIdx, 1);
  await save(file, processor, tree);
  console.log(`Removed ${id}`);
}

async function cmdClean(file) {
  const content = await read(file);
  const { processor, tree, tableNode, headers } = parseKanban(content);
  const statusIdx = findHeaderIndex(headers, 'Status');
  const idIdx = findHeaderIndex(headers, 'ID');
  const removed = [];
  for (let i = tableNode.children.length - 1; i >= 1; i--) {
    if (cellText(tableNode.children[i].children[statusIdx]) === 'Done') {
      removed.push(cellText(tableNode.children[i].children[idIdx]));
      tableNode.children.splice(i, 1);
    }
  }
  await save(file, processor, tree);
  if (removed.length) console.log(`Removed done tasks: ${removed.join(', ')}`);
  else console.log('No done tasks to remove.');
}

async function main() {
  const { cmd, file, opts } = parseArgs(process.argv);
  if (opts.help || opts.h) {
    console.log(USAGE);
    process.exit(0);
  }
  switch (cmd) {
    case 'list': await cmdList(file); break;
    case 'next': await cmdNext(file, opts); break;
    case 'status': await cmdStatus(file, opts); break;
    case 'add': await cmdAdd(file, opts); break;
    case 'remove': await cmdRemove(file, opts); break;
    case 'clean': await cmdClean(file); break;
    case 'show': await cmdShow(file, opts); break;
    default: throw new Error(`Unknown command: ${cmd}`);
  }
}



main().catch(err => {
  console.error(err.message);
  process.exit(1);
});
