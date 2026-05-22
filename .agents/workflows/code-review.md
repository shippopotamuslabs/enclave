---
description: "Perform a high-signal code review of current changes using the Code Review Plugin."
---

# Code Review

This workflow orchestrates a comprehensive code review of your current changes, following the repository's architectural and style contracts.

## Steps

1.  **Analyze Diff**: Review all staged and unstaged changes.
2.  **Contract Audit**: Verify compliance with `AGENTS.md` and `/contracts/*.md`.
3.  **Run Review**: `/code-review`

## Context

The review should prioritize:
- Clean Architecture (Layer separation)
- Android Best Practices (AndroJack Level 3)
- Test Coverage Mandates
