#!/usr/bin/env bash
set -euo pipefail

MAIN_REPO="/media/boris/data_nvme1n1/WORKSPACES/WORKSPACE/cbs-nova"
WORKTREE="/media/boris/data_nvme1n1/WORKSPACES/WORKSPACE/cbs-nova-T291"
BRANCH="feat/T291"

echo "Rebasing ${BRANCH} onto main inside worktree..."
cd "${WORKTREE}"
git rebase main

echo "Fast-forward merging ${BRANCH} into main..."
cd "${MAIN_REPO}"
git checkout main
git merge --ff-only "${BRANCH}"

echo "Removing worktree and branch..."
git worktree remove "${WORKTREE}"
git worktree prune
git branch -d "${BRANCH}"

echo "Done. ${BRANCH} merged and removed."
