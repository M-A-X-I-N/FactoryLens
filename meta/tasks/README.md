# Task storage

This directory contains durable task specifications and tracked working context behind the scheduling index at [`../tasks.md`](../tasks.md).

## Task specifications

Use stable task IDs. A non-terminal task should have a linked specification containing enough information to execute and validate the work without depending on chat history.

A practical specification normally includes:

- Description
- Requirements
- Constraints / non-goals
- Acceptance criteria
- Validation

Add blocker or notes sections only when useful.

## Tracked temporary workspaces

Workspaces hold intermediate knowledge that must survive task or agent-context boundaries but is not necessarily permanent project memory.

Use the narrowest convenient scope and do not create empty workspace taxonomy speculatively. Agents should not ingest an entire workspace merely because it exists.

Before temporary material leaves active use, promote durable human-facing architecture into normal documentation and expensive-to-rediscover agent-specific knowledge into `../../.agents/memory/`.

## Archival

Define a repository-local archival convention if completed task history becomes large enough to need one. Preserve stable task identity and useful execution evidence; do not invent archive machinery merely for symmetry.
