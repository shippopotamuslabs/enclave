---
name: review-singularity
description: >-
  Runs a two-stage review (architect brief, then artisan audit after human steer)
  with evidence-backed findings. Use for PRs and branch diffs, agent-written or
  refactored code, major features or task checkpoints, and before merge. When
  spawning an isolated reviewer, provide git range plus requirements — not raw
  session history.
---

# Review Singularity

## Overview

Code review is cognitively harder than writing code, because you reverse-engineer intent from someone else's code and rebuild the mental model backwards. This skill pushes that grind onto you (the AI) and reserves the human's attention for the judgments only they can make: product correctness, architecture fit, and tradeoff ownership.

The loop is two stages: an **architect brief** that compresses the diff into a decision surface for the human, then — only after the human has steered — an **artisan audit** that turns provisional smells into verified findings. You do diff archaeology, path tracing, and behavior reconstruction. The human decides whether the reconstructed behavior is right.

The point is to **transfer understanding**, not to score points: stay evidence-backed and blunt where risk is real; keep remarks anchored on behavior and contracts, not the author's persona.

**Complementary habit (authors):** Review early and review often — especially after discrete tasks in multi-step or subagent-driven work, before risky refactors, and before merging — so wrong assumptions do not compound.

**Delegated reviewer:** If you are reviewing in a fresh thread or subagent, you usually lack the author's conversational history. Treat **`BASE_SHA`/`HEAD_SHA` (or merge-base range), PR/ticket text, and an explicit build/requirements summary** as primary intent signals; mark gaps as `NEEDS-VERIFICATION` rather than inventing narrative from missing context.

## Core Rules

1. **Separate inferred intent from verified behavior.** Use "appears to", "from the diff", "from my reading" when deriving intent from code. Only state intent as fact when backed by ticket text, PR description, commit messages, or explicit user confirmation. Distinguish the **letter** of the stated intent (did the diff do what the ticket said?) from the **spirit** (did it accomplish what the ticket was trying to accomplish?). A diff can deliver the literal ask and still miss the purpose — flag that as an intent mismatch even if the checkboxes are checked.

2. **Evidence before assertions.** Every finding cites `file:line`. If you can't point to it, don't write it. If intent is unclear from code or docs, mark `NEEDS-VERIFICATION` — never fabricate certainty.

3. **Read call sites before flagging.** Before declaring something broken, read the surrounding code and at least one real caller. Many "bugs" in changed code are explained by upstream normalization, caller contracts, or conventions you haven't yet seen. This is not optional — flagging without reading callers is how false findings enter the review.

4. **Investigate before writing.** Do all investigation for a gate before you begin writing that gate's required output. Do not interleave tool calls and output within a single gate — the output is written in one continuous pass after investigation is complete. Interleaving produces narrated exploration instead of verified findings.

5. **Triage hard. No gold-plating.** If code works, isn't on the path to a real problem, and doesn't violate the review lens for this diff, leave it alone. Do not impose stylistic preferences. Do not flag things because they're unfamiliar. A review with five real findings beats a review with twenty plausible ones. Generic architecture advice untethered from specific changed seams is noise — judge the actual diff, not the codebase's general shape. Skip mechanical formatting and import-order nits when tooling already enforces them — unless they obscure correctness or bypass project standards intentionally.

6. **The skill is the contract.** User instructions override it; convenience does not. Do not collapse the brief → pause → audit loop into a single response unless the user explicitly asks for a one-pass full review in this turn.

7. **Respect existing conventions.** The project's patterns are the standard unless they actively cause bugs or block scaling.

8. **Feedback tone.** Critique the diff, not the author. When behavior is uncertain, prefer a concrete question ("What happens if X is empty / fails / retries?") over a flat verdict — then still cite `file:line` once you verify. Non-blocking ideas can use collaborative wording; **severity tags** carry merge urgency.

## Taxonomies

Use these labels verbatim. They are referenced throughout the skill and in the Architect Brief template.

**Diff shape** (your reviewer judgment on the overall patch):
- `direct_and_cohesive` — dominant code movement matches the requested outcome
- `mostly_mechanical` — move/rename/extract with little new logic
- `suspiciously_noisy` — support machinery outweighs the behavior it enables
- `intent_mismatch` — code does not support the apparent intent

**Provisional read** (your confidence-scoped opinion on implementation shape):
- `likely_correct` | `coherent` | `suspicious` | `overbuilt` | `underbuilt`

**Behavior delta classification** (per meaningful change):
- `behavior_preserved` — old observable behavior unchanged
- `behavior_intentionally_changed` — change is the point
- `behavior_uncertain` — needs human or product confirmation

**Primary review lens** (the dominant question for this diff):
- `behavior_equivalence` | `ownership_boundary_fit` | `contract_compatibility` | `diff_coherence` | `feature_completeness`

## Operating Modes

- **Reviewer Mode** — default. You are reviewing another engineer's PR, or a branch the human did not ask you to author. Do not judge whether the PR is "ready for review"; it already is.
- **Author Mode** — the user is asking whether work you or another agent produced in this session is ready for handoff. Run everything in Reviewer Mode, then run the Author-Mode Readiness Gate before claiming ready.

## When to invoke this skill

**Strong signals (do not skip casually):**

- After completing a substantial task or milestone in agent/subagent-driven development (catch integration mistakes before the next task).
- After a major feature slice lands (behavior + boundaries + tests).
- Before merging to the main integration branch.

**Often worth it:**

- When stuck (fresh pass over committed diff and contracts).
- Before a broad refactor (baseline the current behavioral seams).
- After a complex bug fix (failure paths and regressions).

Skipping review because the change "looks small" is a common failure mode — brief-first review is cheap relative to revert cost.

## Workflow

### 0. Select mode

- Author Mode if the user is asking whether your own or another agent's recent work is ready to hand off.
- Reviewer Mode otherwise.
- If uncertain, use Reviewer Mode and note the ambiguity in the brief.

### 1. Establish review scope

- Prefer PR metadata and committed diffs over ambient working-tree changes.
- Identify base branch, head branch, merge-base, changed files, and any local dirty files.
- Read the ticket and PR description if they exist; otherwise infer from commit messages and diff structure.
- Load repo review rules (`CLAUDE.md`, `AGENTS.md`, architecture contracts, layer docs) before judging architecture.
- Use CI / check status when visible: green does not prove behavioral coverage; red narrows what validation you trust until explained.
- If the patch is very large, note a **process** concern (harder to review safely, split suggestion). Do not substitute that for running the technical gates on what is in scope.

### 1a. Handoff when an isolated reviewer does the pass

Authors requesting review should supply **work-product context**, not chat transcripts. Minimum package:

- **`DESCRIPTION`** — what shipped in plain language (user-visible + technical).
- **`PLAN_OR_REQUIREMENTS`** — what "done" means (ticket excerpt, acceptance criteria, plan section, or checklist).
- **Git range** — reproducible diff boundaries, e.g.:

```bash
BASE_SHA=$(git merge-base HEAD origin/main)   # or task-start commit, or HEAD~N
HEAD_SHA=$(git rev-parse HEAD)
```

Optional: link or paste PR title/body. The reviewer uses `BASE_SHA..HEAD_SHA`, repo rules, and code — **not** the author's exploratory narration — unless the user explicitly overrides.

### 2. Derive the architect brief

Read changed filenames first, then representative patches, then surrounding code for changed seams.

Before judging, do this much investigation:
- Trace at least one concrete caller/callee or request/handler/mapper path for each major behavior area.
- For every removed helper, extension, or mapper call, find what behavior it used to perform. Treat removal as suspicious until proven otherwise.
- For each changed seam, write a one-line purpose before judging whether the new indirection or ownership belongs there.
- Compare the requested outcome to the dominant shape of the diff. If supporting machinery outweighs the behavior it enables, mark `suspiciously_noisy`.
- For new features, trace the happy path from entry point to durable side effect, response, event, or external call.
- Inventory old observable behavior before judging new shape: helper calls, fallback lookups, formatting, normalization, ordering, filtering, null/default behavior, exception behavior, culture/casing, side effects.

Then fill the Architect Brief template (below). Surface design smells as hypotheses with language like "possible smell" or "design question" — the human's context determines whether the shape is acceptable.

### 3. Present the architect brief and STOP

In the first response, present **only** the architect brief plus any immediate stop-condition concerns.

Do not run or present the artisan audit, findings, test gaps, validation, or residual-risk sections in the same response.

End by asking the human to confirm, correct, or redirect. Ask only about high-level intent, architecture, or product behavior — do not ask the human to inspect low-level code unless product judgment is needed.

See **Red Flags — STOP and hand control back** below for rationalizations to reject.

### 4. Run the artisan audit (only after human responds)

Treat the human response as audit direction: confirm, correct the implementation model, narrow the scope, or change the primary review lens before continuing. If the human corrects intent or architecture, restate the current decision in one sentence before auditing.

If the human asks a follow-up question instead of confirming, answer it and continue to pause unless they also ask for the audit.

Consult the **Gate Dispatch** table to decide which gates fire for this diff. Run them. Run Adversarial Self-Review last.

### 5. Verify where reasonable

- Run targeted tests/builds when available and safe.
- For exception, model, enum, serialization, or boundary ownership changes, run the full affected test project when feasible — focused tests may not exercise existing contracts.
- Before declaring validation sufficient, grep for stale references to old types, exceptions, contracts, serializers, or DTOs in tests and nearby callers.
- If CI fails after local green, inspect failing check annotations or logs before recommending a rerun.
- Distinguish environment failure from product failure.
- Never claim tests pass unless they were run in the current review.
- For PR descriptions, copy verification commands from actual command history, not memory.
- After the final commit/push, compare PR title/body against `HEAD` and the final diff.

### 6. In Author Mode only, run the readiness gate

See **Author-Mode Readiness Gate** below. Do not run it in Reviewer Mode unless explicitly asked.

## Architect Brief Format

Fill this template. Every field is required. Mark unknowns as `[inferred]` or `NEEDS-VERIFICATION` — do not omit.

```markdown
**Architect Brief**
- Apparent intent: [one sentence; mark [from ticket/PR] or [inferred from diff]]
- Implementation model: [what the code now does — not what it claims to do]
- Key deltas and boundaries: [what moved, what contract changed, what stayed intentionally legacy]
- Diff shape: [direct_and_cohesive | mostly_mechanical | suspiciously_noisy | intent_mismatch]
- Claude's provisional read: [likely_correct | coherent | suspicious | overbuilt | underbuilt]
- Confidence: [high | medium | low] — [one sentence on what would raise or lower it]
- Primary review lens: [behavior_equivalence | ownership_boundary_fit | contract_compatibility | diff_coherence | feature_completeness]
- Smell hypotheses: [0–3 bullets, each labeled "possible smell to verify"]
- Human judgment needed: [the specific question only the human can answer]
- Audit focus (pending your steer): [which gates I plan to run and why]
```

Keep it tight. One tight paragraph or 2-3 bullets per field. This is an abstraction, not a changelog.

### Worked Example

```markdown
**Architect Brief**
- Apparent intent: Extract `ResolveTenantId` from two call sites into a shared helper on `TenantContext`. [inferred from diff; no ticket referenced]
- Implementation model: A new static method `TenantContext.Resolve(ctx)` reads the tenant id from `HttpContext.Items["tenant"]`, falling back to a claim lookup. Both former call sites now delegate to it. The old helper in `BillingService` was deleted; the one in `ReportingService` was inlined then removed.
- Key deltas and boundaries: Lookup logic centralized into `TenantContext`. `BillingService` loses a dependency on `IClaimsReader`. The fallback branch in `ReportingService` (culture-insensitive key match) was rewritten to use `StringComparer.OrdinalIgnoreCase`.
- Diff shape: mostly_mechanical
- Claude's provisional read: suspicious
- Confidence: medium — the casing change in the fallback branch looks intentional but is not called out in the commit message; I could not find a test exercising the old case-insensitive path.
- Primary review lens: behavior_equivalence
- Smell hypotheses:
  - possible smell to verify: the old `ReportingService` fallback accepted keys via `CultureInfo.InvariantCulture.CompareInfo.IndexOf`; the new `OrdinalIgnoreCase` path may differ on Turkish-locale inputs.
  - possible smell to verify: deleting `BillingService`'s `IClaimsReader` dependency removes a seam used by one test fixture (`BillingServiceTests.ResolvesFromClaims_WhenHeaderMissing`).
- Human judgment needed: Is the casing behavior change intentional? If not, we need a regression test on the Turkish-locale path before merging.
- Audit focus (pending your steer): Regression Hunt (old fallback semantics), Behavioral Equivalence (casing), Test Quality (does the remaining `BillingServiceTests` suite still cover the claims path?).
```

## Gate Dispatch

Run these gates based on diff shape. **Adversarial Self-Review always runs last, regardless of diff shape.**

| Diff shape | Required gates |
|---|---|
| Refactor / move / extract / generalize | Regression Hunt, Behavioral Equivalence, Boundary/Ownership, Test Quality |
| New feature | Critical Path Tracing (happy + failure), Boundary/Ownership, Targeted Sweep, Test Quality |
| Boundary or contract change | Boundary/Ownership, Behavioral Equivalence, Test Quality, Targeted Sweep |
| Bug fix | Critical Path Tracing (failure path), Regression Hunt (if touches shared code), Test Quality |
| Config / infra / build | Targeted Sweep, Boundary/Ownership |
| Any diff | Adversarial Self-Review (always, last) |

If a diff spans categories (feature + refactor), run the union. If you are unsure which category applies, state the ambiguity and run the superset.

## Gates

Each gate follows the same shape: when it fires, what steps to run, what smells to watch for, the stance to take, and the required output format.

### Regression Hunt Gate

**Trigger**: any diff that moves, extracts, replaces, generalizes, or "cleans up" existing behavior. Mandatory for refactors, ownership moves, mapper/value-object moves, and helper-to-inline rewrites.

**Steps**:
1. Locate the old behavior, not just the new file. Use the diff, deleted code, moved call sites, and `git show`/base-branch reads.
2. List the old path's observable semantics: accepted inputs, fallback behavior, value conversions, formatting/culture, casing, null/empty/default handling, ordering/distinctness, exceptions, side effects, downstream contract shape.
3. Compare the replacement path line-by-line against that list. A cleaner abstraction is not evidence of parity.
4. For every dropped semantic, classify: `behavior_intentionally_changed`, regression finding, or `behavior_uncertain`.
5. Check tests against the old semantics. If tests only cover the new happy path, flag missing regression coverage even when the implementation looks plausible.

**High-signal smells**:
- helper/extension/mapper call removed and replaced with direct property/dictionary access
- dynamic/object/DTO/record value extraction moved across layers
- string casing, key lookup, enum parsing, numeric/date formatting, culture, rounding, or serialization touched
- fallback, compatibility shim, null/default guard, or "legacy" branch deleted
- code path changes owner while caller-visible behavior is described as preserved
- tests assert the new owner exists but do not prove old edge behavior still works

**Stance**: Be aggressive. If the old helper did five small things and the new code visibly does two, assume there is a regression until the missing three are proven irrelevant or intentional.

**Required output**: For each dropped or changed semantic, emit:
- `[file:line]` **{old behavior}** → **{new behavior}** — classified as `{behavior_preserved | behavior_intentionally_changed | behavior_uncertain | regression}` — {user-visible or caller-visible scenario that would catch it, or "no scenario identified"}

### Critical Path Tracing Gate

**Trigger**: new features, bug fixes, or any diff where behavior traversal across layers is the dominant question.

**Steps**:
1. Start from the architect brief's provisional read. Prove, refine, or discard its smell hypotheses with code-path evidence before adding unrelated findings.
2. For each major behavior area, trace the affected path end-to-end across every layer it touches. At each hop, identify: what data is transformed/validated/persisted, what could fail, how failure manifests.
3. For new features: trace happy path from entry point to durable side effect, response, event, or external call. Then trace at least one failure path end to end — validation, rollback/partial-write behavior, user-facing response, telemetry.
4. If the feature adds statuses, flags, lifecycle states, retries, sync states, or workflow steps, audit allowed and illegal transitions.

**High-signal smells**:
- happy path clean, failure path undefined or silently swallowed
- partial writes with no rollback or compensating action
- state transitions with no explicit guard against illegal moves
- telemetry present on success, absent on failure

**Stance**: Trace until you can describe what the user sees and what the system records for each path. If you can't, mark `NEEDS-VERIFICATION` and say what you'd need.

**Required output**: For each traced path:
- **Path**: `{entry} → ... → {durable effect}`
- **Happy path**: `{observations and file:line citations}`
- **Failure modes traced**: `{list, each with file:line and user-visible outcome}`
- **Untraced failure modes**: `{list, or "none"}`

### Targeted Sweep Gate

**Trigger**: new features, boundary/contract changes, config/infra changes.

**Steps**: Run targeted greps and reads against the diff's affected areas.

**Required output**: Emit exactly one line per category below. Do not omit categories — "none found" is a valid line, but silence is non-compliance.

- **Hardcoded strings/configs/secrets**: `{file:line}` or "none found"
- **Env var access inside shared libraries or domain code**: `{file:line}` or "none found"
- **Type assertions/casts/`any`/`unknown as X` bypassing validation**: `{file:line}` or "none found"
- **Catch blocks silently swallowing errors**: `{file:line}` or "none found"
- **Direct I/O in business logic**: `{file:line}` or "none found"
- **Async work without cancellation where callers may need it**: `{file:line}` or "none found"
- **Duplicate validation across boundaries**: `{file:line}` or "none found"
- **Circular or layering-violating imports introduced**: `{file:line}` or "none found"

### Boundary and Ownership Audit Gate

**Trigger**: refactors, new features, boundary/contract changes, config/infra changes.

**Steps**:
1. For each architectural boundary the diff touches, list what now crosses it.
2. Check permission and ownership: who can invoke, on whose data, on whose behalf, enforced where.
3. Check idempotency and retry behavior for commands, event handlers, webhooks, background jobs, external writes.
4. Check observability: structured logs, telemetry, audit trail, correlation, secret-safe diagnostics.
5. Check configuration/default behavior: missing config, false flags, old config, partial rollout, migrations.
6. Check backward/forward compatibility with old clients, old data, new optional fields, mixed-version deployments.
7. If logic moved into shared code, list all changed and existing callers; decide whether new behavior is intentionally broad or accidentally over-applied.
8. If a new shared policy or interface/base method is introduced, verify every same-purpose call site adopts it or document why it's outside the behavior surface.
9. If changes fan out to many concrete implementations, build a matrix of `concrete × behavior dimension × test coverage`. Missing cells are findings or explicit review notes.

**High-signal smells**:
- domain logic leaking out of its layer
- shared code containing app-specific assumptions
- lower layers depending on upper layers
- implementation details leaking into public APIs
- "generic" code that only one consumer could use
- stale concrete type references after a move toward interfaces/domain contracts
- equivalent responsibility paths left on stale behavior after one path changed

**Stance**: Ownership moves are high-risk. Grep for stale expectations in tests and callers — old exception types, DTOs, interfaces, concrete models, marker interfaces, serializers, boundary contracts.

**Required output**: For each boundary touched:
- **Boundary**: `{name}` — `{what crosses it now}`
- **Permission/ownership**: `{where enforced, or "not applicable"}`
- **Idempotency/retry**: `{behavior, or "not applicable"}`
- **Observability**: `{telemetry present/absent}`
- **Compatibility concerns**: `{list, or "none"}`
- **Fan-out coverage**: `{matrix summary, or "not a fan-out change"}`

### Behavioral Equivalence Gate

**Trigger**: refactors, boundary/contract changes, any `behavior_preserved` or `behavior_uncertain` deltas.

**Steps**:
1. For moved or generalized logic, prove behavioral equivalence from the old code path before reviewing the new abstraction's style. Compare inputs, matching predicates, traversal/bubbling behavior, side effects, caller-visible results.
2. After iterative review changes, run a coherence pass over the final shape: remove temporary scaffolding, duplicate fallback paths, stale compatibility shims, PR/test wording that only made sense during an earlier direction.
3. Check whether upstream normalization or ownership movement made downstream guards, casts, or compatibility checks redundant fossils; keep only when they are defensive compatibility, not stale logic.
4. Run a semantic naming pass for changed concepts and policies: confirm names describe exact behavior, polarity, and scope.
5. Check whether the chosen decision shape (boolean/enum/result object/exception) preserves enough information for user-facing errors, telemetry, retries, recovery, or compatibility.

**High-signal smells**:
- direct dictionary/property access replacing a removed value-extraction helper
- renamed concept with one call site still relying on legacy meaning
- lossy decision shape (boolean) chosen where callers need reason-specific diagnostics
- workaround fossils left behind after the original abstraction became usable again

**Stance**: A cleaner abstraction is not evidence of parity. Require proof.

**Required output**: For each moved/generalized logic site:
- **Site**: `{file:line}`
- **Old semantics**: `{list}`
- **New semantics**: `{list}`
- **Delta**: `{list, each classified}`
- **Parity proof or NEEDS-VERIFICATION**: `{evidence}`

### Test Quality Gate

**Trigger**: any diff that adds or changes tests.

**Steps**: For each added/changed test, classify and identify what it catches.

**Classifications**:
- `durable` — catches a behavior, contract, or regression a future refactor could break
- `weak` — tests real behavior but too brittle or narrow to be load-bearing
- `junk` — proves only that the diff happened (file moved, type disappeared, name exists, constant copied, private arrangement stayed arranged, reflection can find/not find implementation details)

**Smells that usually indicate junk**:
- receipt tests memorializing a one-time migration
- tests coupled to locations, names, copied constants, private wiring, assembly/type lookup
- reflection/name tests that only assert type/file names after a move
- tests that pass with or without the implementation behavior

**Stance**: Be blunt. Junk tests are false confidence and maintenance drag. Recommend deletion or replacement with consumer-facing behavior/contract coverage. Do not soften this into "test gap" language.

Exception: preserve tests that enforce durable architecture rules **only** when the rule would still matter after a future refactor and the assertion is not just a snapshot of today's file layout.

Prefer validation commands (stale-reference grep, compile/build checks, focused consumer tests) over unit tests for mechanical-move evidence.

**Required output**: For each added/changed test:
- **Test**: `{file:line or test name}`
- **Classification**: `durable | weak | junk`
- **What it catches**: `{behavior/contract/regression, or "only that diff happened"}`
- **Recommendation**: `{keep | strengthen to cover {X} | delete — replace with {Y} if {Z}}`

### Structural Risks Gate

**Trigger**: any diff.

**Steps**:
1. Look for missing boundary mappings, layer leaks, overbuilt abstractions, stale PR text, dead code, accidental generated-file edits, regressions against existing callers.
2. Identify the top complexity hotspots touched using concrete signals (responsibility count, fan-in/fan-out). A changed file with >3 distinct responsibilities is a decomposition concern.
3. Reuse check: if the diff adds a helper or parallel implementation, quick-search adjacent/shared modules for existing utilities that already solve the same shape — unnecessary duplication is a finding only when it increases drift risk or violates boundaries.

**Required output**: A short list of structural risks with `file:line` and proposed fix, or "none found."

### Adversarial Self-Review Gate

**Trigger**: always, last.

**Steps**: Produce real output for each of the four parts below. "Passed" is not an answer. "No changes needed" is valid only with evidence of the check you ran.

1. **Weakest finding revisited.** Name your weakest finding — the one most likely shallow or wrong. Do one more round of investigation (read the actual implementation, check call sites, targeted grep). Upgrade, downgrade, or remove based on what you find.

2. **Steelman the top finding.** For your top-severity finding, write the strongest case for why the current code is correct and you're wrong. If the steelman holds up, downgrade or remove. If it doesn't, the finding is stronger for surviving it — note what broke the steelman.

3. **Three things not looked for.** Name three categories of issues your audit did NOT investigate for this diff (e.g., telemetry changes, thread-safety, locale handling, cache invalidation, cold-start behavior). For each, state whether it's safe to skip (and why) or whether it belongs in OPEN QUESTIONS for the human to decide.

4. **One unverified assumption, then check it.** State one architectural or behavioral assumption you've been making while reviewing (e.g., "the caller validates input before reaching this method", "the DB transaction wraps the whole handler"). Then verify it against the code. Report whether it held.

**Stance**: False confidence is worse than no finding. The goal is not more findings — it's truer ones. Parts 3 and 4 exist because the most dangerous findings are the ones you didn't know to look for.

**Required output**:
- **Weakest finding revisited**: `{name}` — `{upgraded | downgraded | removed}` because `{evidence}`
- **Top finding steelmanned**: `{name}` — `{held | downgraded | removed}` because `{steelman + counter}`
- **Not looked for**:
  1. `{category}` — `{safe to skip because X | OPEN QUESTION: Y}`
  2. `{category}` — `{...}`
  3. `{category}` — `{...}`
- **Assumption checked**: `{assumption}` — `{held | broke}` — `{evidence}`
- **NEEDS-VERIFICATION items**: `{list, or "none"}`

## Artisan Audit Format

After the architect brief is confirmed or redirected, and all dispatched gates have produced their required output, compile:

```markdown
**Findings**
[Prioritized findings with `file:line` references. Tag actionable items with severity: **Critical** | **Important** | **Minor** (Critical: correctness/security/data loss/blocking merge; Important: likely bugs or contract risk; Minor: hygiene without near-term risk). Each: what is wrong, where, why it matters, what would fix it. Say "No findings" if none.]

**Strengths**
[Optional. 0–2 bullets: durable positives tied to this diff (`file:line` or behavior). Omit the section entirely if nothing substantive — no performative praise.]

**Bad Tests**
[Call out junk/weak tests added or changed by the diff, or say "No bad tests found." Do not hide junk tests under generic test gaps.]

**Test Gaps**
[Missing or weak coverage tied to real behavior.]

**Validation**
[Commands run and outcomes, or why not run.]

**NEEDS-VERIFICATION**
[Items where you could not verify from code alone — what you'd need (a test run, a log sample, a doc, a maintainer answer) to close each one. "None" is a valid answer.]

**Open Questions**
[Product/intent/scope questions only the human can answer, including anything the Adversarial Self-Review's "not looked for" step flagged as needing human judgment. Distinct from NEEDS-VERIFICATION, which is about evidence you didn't have access to. "None" is a valid answer.]

**Not Flagged**
[Things you considered and deliberately excluded, one line each. Include anything the Adversarial Self-Review steelmanned away. This gives the human a chance to override your exclusions. "None" is a valid answer.]

**Residual Risk**
[Concrete remaining uncertainty that isn't captured above — integration-level concerns, rollout risk, observability gaps the diff doesn't address.]
```

### Severity → expected author response

Use these defaults unless the user sets different merge policy:

| Severity | Author expectation |
| --- | --- |
| **Critical** | Fix before merge / before declaring ready; do not silence with "follow-up". |
| **Important** | Fix before merge unless explicitly accepted with recorded risk; do not ignore by default. |
| **Minor** | Track or batch; optional before merge if product timeline dominates. |

Optional tone labels when helpful (they do not replace severity): **`[nit]`** — optional polish; **`[suggestion]`** — alternate approach for author judgment; **`[question]`** — honest uncertainty pending answer or verification.

**Handling disagreement:** If the reviewer is wrong, push back with **`file:line` evidence**, tests, or measured behavior — not authority appeals. If unclear, upgrade to `NEEDS-VERIFICATION` and state what would falsify each side.

## Author-Mode Readiness Gate

Only use this when the user asks whether work you or another agent produced this session is ready to hand off.

Before saying "ready for review", run this audit:

- Does the final diff cohere to the stated intent, or did review-driven pivots leave residue?
- Is each responsibility owned once at the right boundary, with no stale shims or duplicated concepts?
- Did moved or generalized logic preserve old observable behavior where behavior was meant to stay the same?
- Do tests prove behavior or durable contracts rather than implementation arrangement?
- Are contracts, permissions, idempotency, observability, configuration, compatibility, and failure paths covered where they matter?
- Does the PR body and final summary describe `HEAD`, not an intermediate plan?

**If any answer reveals avoidable slop, do NOT say "ready for review."** Respond in this exact shape:

```markdown
**Not ready** — [1-sentence reason]

**Blocking issues:**
- [file:line] [what's wrong] [what would fix it]

**Optional cleanup (non-blocking):**
- [file:line] [nice-to-have]
```

Only after blocking issues are resolved (or explicitly accepted by the user) may you say "ready for review."

When inline review comments are requested, phrase uncertain intent with epistemic humility:

```text
From my reading, this path now treats X as Y. I think maybe that changes Z for existing callers when A happens. Is that intentional?
```

## Author discipline (requesting / consuming review)

**Never:**

- Skip review because the change feels trivial — trivial diffs still ship bugs at boundaries.
- Ignore **Critical** findings or merge with unfixed **Important** issues without explicit documented acceptance.
- Nitpick the reviewer instead of engaging with cited evidence at `file:line`.

**Healthy loop:** Request review → triage findings by severity → fix or formally accept → re-run validation touching affected contracts → only then proceed or merge.

## Red Flags — STOP and hand control back

These thoughts mean you are about to violate the skill. If any of them appear, stop and return control to the human with the architect brief only.

| Rationalization | Reality |
|---|---|
| "The user clearly wants everything now" | The skill is the contract. Brief first. User instructions override only if explicit in this turn. |
| "I already have the findings, it's wasteful to hold them" | Findings before human steer are anchored on unverified intent. Holding is the point. |
| "This is a small diff, one pass is fine" | The pause protects against misread intent, not diff size. |
| "The user is senior, they'd rather see it all" | Seniority means they want steering authority, not more output. |
| "I'll just note the findings at the end of the brief" | That is the audit. Do not smuggle it in. |
| "Intent is obvious from the diff" | "Appears to" language exists for exactly this case. Use it. |
| "I can skip the Targeted Sweep categories that look unlikely" | The sweep's value is exhaustiveness. "None found" is the required output. |
| "I'll call this finding high-confidence even though I didn't verify" | Mark `NEEDS-VERIFICATION`. False confidence is a review risk. |
| "The test covers the new happy path, that's enough" | For refactors, old edge semantics need explicit parity proof. |
| "The commit passed CI, behavior must be preserved" | CI proves the tests pass, not that the tests cover what changed. |
| "Framing this as a test gap is softer than calling it junk" | Junk tests are junk. Say so. Soft language is a failure mode. |
| "I'll pad the audit with style and import nits" | Burn budget on correctness and contracts; mechanical style belongs to lint/format unless it hides a defect or violates explicit team rules. |

**All of these mean: stop. Produce only the architect brief. Wait for the human.**

## Review Heuristics

| Signal | What to Check |
| --- | --- |
| Implementation model | Can the human understand what the PR actually does without reading the diff? |
| Design smell hypothesis | What abstract design question should the human decide, and what code evidence supports it? |
| New abstraction | What responsibility moved behind it, and is that movement simpler for callers? |
| Boundary movement | Did ownership move inward without leaking outer-layer concerns back into core? |
| Mapping/translation change | Are all boundary conversions explicit, consumer-safe, and covered at the right level? |
| Model/contract move | Are stored data, serialized forms, public consumers, and generated artifacts safe? |
| Test additions | Do tests fail without the implementation and protect real behavior? |
| Junk tests | Would deleting the test lose real protection, or only remove theater? |
| Reflection/name tests | Is reflection checking public compatibility, or just asserting type/file names after a move? |
| Migration tests | Should this be a grep/build/review note instead of a permanent unit test? |
| New feature flow | Can you trace the happy path from entry point to durable side effect, response, event, or external call? |
| Failure path | What happens on validation failure, dependency failure, partial write, retry, or external error? |
| State transition | Are allowed and illegal status/lifecycle/workflow transitions explicit and tested? |
| Public contract | Are APIs, events, DTOs, messages, persisted models, and external calls stable and consumer-safe? |
| Permission/ownership | Who can do this, to whose data, on whose behalf, and where is it enforced? |
| Idempotency/retry | What happens if the command, webhook, job, handler, or external write runs twice? |
| Observability | Are logs, telemetry, audit, correlation, and secret-safe diagnostics enough for production support? |
| Config/defaults | What happens when config is missing, old, false, partially deployed, or migrated? |
| Compatibility | Do old clients/data and mixed-version deployments still work? |
| Feature scope | What is intentionally out of scope, and do review findings respect that boundary? |
| Ownership move | Do tests and callers assert the new owner's semantics, or stale boundary behavior? |
| Decision drift | Did review discussion change the architecture direction, and has the final decision been restated before continuing? |
| Iteration residue | Did temporary scaffolding, duplicate fallback paths, stale tests, or intermediate PR wording survive after later changes? |
| Behavioral equivalence | For moved/generalized logic, does the new path preserve old inputs, predicates, side effects, and caller-visible results? |
| Deleted helper call | What behavior did the removed helper/extension/mapper perform besides the obvious lookup or assignment? |
| Value extraction parity | Did dynamic/object/DTO extraction preserve casing fallback, null/default handling, formatting, culture, parsing, and conversion behavior? |
| Compatibility fallback | Was an old fallback or shim intentionally removed, and are old data/config/client shapes still safe? |
| Responsibility clarity | Is each responsibility owned once, at the right boundary, with callers pointed at that owner? |
| Concept clarity | Did the change introduce two names or shapes for one concept, or one name for two concepts? |
| Semantic rename | Does the new name preserve meaning, polarity, and scope across all affected call sites? |
| Decision shape | Does the return type preserve the information callers need for branching, diagnostics, recovery, or compatibility? |
| Sibling/fan-out paths | Did equivalent responsibilities adopt the new behavior consistently, and are exclusions intentional? |
| Shared-code extraction | Did the moved behavior intentionally affect every existing caller of the shared code? |
| Legacy parity | Which paths must preserve old behavior while architecture improves? |
| Redundant guard | Did upstream normalization make downstream checks unreachable or stale? |
| Concrete type after abstraction move | If contracts moved inward, is every remaining concrete type reference necessary and layer-appropriate? |
| Existing pattern bypass | Did changed code stop using a local factory, serializer, mapper, or abstraction without a clear reason? |
| Workaround fossil | Did an earlier workaround remain after a later change made the original abstraction usable again? |
| Diff shape mismatch | Does the dominant code movement match the requested outcome, or is most of the patch support machinery? |
| Migration directness | For mechanical moves or ownership changes, is the patch direct consumer migration and deletion, or unnecessary indirection? |
| Verification scope | Were full affected test projects run for boundary/ownership changes when feasible? |
| CI mismatch | If CI failed after local green, were check annotations/logs inspected before calling it flaky? |
| PR body | Does it describe current code, not stale earlier changes? |
| Agent-written code | Look harder for plausible-but-wrong intermediates and over-scoped cleanup. |
| Readiness claim | In Author Mode, challenge whether the diff aggressively coheres to its stated intent before saying "ready." |

## Stop Conditions

Stop and ask for human input when:

- The inferred intent conflicts with the ticket or PR description.
- A public API or persisted contract may be breaking and no migration/compatibility story is visible.
- The architecture choice is plausible but product ownership determines correctness.
- The review requires secrets, production data, or destructive local changes.

## Common Rationalizations (Rejected)

These are self-talk patterns that precede skill violations. Recognize them in your own reasoning and stop.

- "The user said 'review this' — that means full audit, not just a brief." → No. Brief first unless they say "one-pass full review."
- "I'll skip the Targeted Sweep on this category because nothing in the diff matches it." → You don't know that until you sweep. Emit the line.
- "This test is arguably useful, I'll call it `weak` instead of `junk`." → If it proves only that the diff happened, it is junk. Say so.
- "I already have a strong finding, Adversarial Self-Review is a formality." → The steelman is where shallow findings die. Run it.
- "The helper was simple, direct property access must be equivalent." → Helpers usually do more than they look like. Inventory before trusting.
- "Intent is obvious." → Then state it as `[inferred]`, not as fact.
- "I ran focused tests, that's validation." → Not for boundary/ownership changes. Run the full affected project.
- "The PR description says what changed." → It describes intent at some point in time, not necessarily `HEAD`. Verify.
- "CI is green, so behavior is preserved." → CI proves the tests pass. It does not prove the tests cover what changed.
- "The human is busy, I'll combine the brief and audit to save their time." → The brief exists to save their time. The audit exists to spend yours.

## Completion

You are done with the review only when:

- Architect brief was presented and the human confirmed, corrected, or redirected.
- All dispatched gates emitted their required output.
- Adversarial Self-Review ran all four parts (weakest finding, steelmanned top finding, three not-looked-for categories, one assumption checked) — not just the first two.
- Artisan Audit sections (Findings, Bad Tests, Test Gaps, Validation, NEEDS-VERIFICATION, Open Questions, Not Flagged, Residual Risk) are all populated (or explicit "none" / "No findings" for each). **Strengths** is optional — include only when substantive; otherwise omit the heading entirely.
- If Author Mode: Readiness Gate produced either "ready for review" or the structured not-ready response.

Do not claim completion before all of the above. Do not invent validation that wasn't run. Do not downgrade `NEEDS-VERIFICATION` items to assertions just to close out the review. Do not collapse Open Questions into NEEDS-VERIFICATION or vice versa — they are different channels and the human reads them differently.
