---
name: agent-router
version: 1.0
description: Master agent routing contract — synthitect-first with full MCP tool inventory
mcpServers:
  synthitect:
    tools:
      - synthitect___spawn_probe
      - synthitect___run_discovery
      - synthitect___generate_specs
      - synthitect___execute_tdd_red
      - synthitect___implement_green
      - synthitect___run_audit
    purpose: CFAW workflow automation
  mobile-mcp:
    tools:
      - mobile_list_available_devices
      - mobile_list_apps
      - mobile_launch_app
      - mobile_install_app
      - mobile_uninstall_app
      - mobile_terminate_app
      - mobile_get_screen_size
      - mobile_click_on_screen_at_coordinates
      - mobile_double_tap_on_screen
      - mobile_long_press_on_screen_at_coordinates
      - mobile_list_elements_on_screen
      - mobile_press_button
      - mobile_open_url
      - mobile_swipe_on_screen
      - mobile_type_keys
      - mobile_take_screenshot
      - mobile_save_screenshot
      - mobile_get_orientation
      - mobile_set_orientation
      - mobile_start_screen_recording
      - mobile_stop_screen_recording
    purpose: Android/iOS device interaction
  google-developer-knowledge:
    tools:
      - google-developer-knowledge___search_documents
      - google-developer-knowledge___get_documents
    purpose: Android, Firebase, Chrome, TensorFlow, Google Cloud docs
  firebase-mcp-server:
    tools:
      - firebase-mcp-server___firebase_login
      - firebase-mcp-server___firebase_logout
      - firebase-mcp-server___firebase_get_project
      - firebase-mcp-server___firebase_list_apps
      - firebase-mcp-server___firebase_get_sdk_config
      - firebase-mcp-server___firebase_create_app
      - firebase-mcp-server___firebase_create_android_sha
      - firebase-mcp-server___firebase_get_environment
      - firebase-mcp-server___firebase_update_environment
      - firebase-mcp-server___firebase_init
      - firebase-mcp-server___firebase_get_security_rules
      - firebase-mcp-server___firebase_read_resources
      - firebase-mcp-server___crashlytics_create_note
      - firebase-mcp-server___crashlytics_delete_note
      - firebase-mcp-server___crashlytics_get_issue
      - firebase-mcp-server___crashlytics_list_events
      - firebase-mcp-server___crashlytics_batch_get_events
      - firebase-mcp-server___crashlytics_list_notes
      - firebase-mcp-server___crashlytics_get_report
      - firebase-mcp-server___crashlytics_update_issue
      - firebase-mcp-server___developerknowledge_search_documents
      - firebase-mcp-server___developerknowledge_get_documents
    purpose: Firebase project management, Crashlytics
  github-mcp-server:
    tools:
      - github-mcp-server___create_or_update_file
      - github-mcp-server___search_repositories
      - github-mcp-server___create_repository
      - github-mcp-server___get_file_contents
      - github-mcp-server___push_files
      - github-mcp-server___create_issue
      - github-mcp-server___create_pull_request
      - github-mcp-server___fork_repository
      - github-mcp-server___create_branch
      - github-mcp-server___list_commits
      - github-mcp-server___list_issues
      - github-mcp-server___update_issue
      - github-mcp-server___add_issue_comment
      - github-mcp-server___search_code
      - github-mcp-server___search_issues
      - github-mcp-server___search_users
      - github-mcp-server___get_issue
      - github-mcp-server___get_pull_request
      - github-mcp-server___list_pull_requests
      - github-mcp-server___create_pull_request_review
      - github-mcp-server___merge_pull_request
      - github-mcp-server___get_pull_request_files
      - github-mcp-server___get_pull_request_status
      - github-mcp-server___update_pull_request_branch
      - github-mcp-server___get_pull_request_comments
      - github-mcp-server___get_pull_request_reviews
    purpose: GitHub PRs, issues, repos, code search
  androjack:
    tools:
      - androjack___android_official_search
      - androjack___android_component_status
      - androjack___architecture_reference
      - androjack___android_debugger
      - androjack___gradle_dependency_checker
      - androjack___android_api_level_check
      - androjack___kotlin_best_practices
      - androjack___material3_expressive
      - androjack___android_permission_advisor
      - androjack___android_testing_guide
      - androjack___android_build_and_publish
      - androjack___android_large_screen_guide
      - androjack___android_scalability_guide
      - androjack___android_navigation3_guide
      - androjack___android_api36_compliance
      - androjack___android_kmp_guide
      - androjack___android_ondevice_ai
      - androjack___android_play_policy_advisor
      - androjack___android_xr_guide
      - androjack___android_wearos_guide
      - androjack___android_code_validator
      - androjack___android_api17_compliance
    purpose: Android API validation, component status, architecture ref, gradle deps
  context7:
    tools:
      - context7___resolve-library-id
      - context7___query-docs
    purpose: Library documentation (Context7)
  morph-mcp:
    tools:
      - morph-mcp___edit_file
      - morph-mcp___codebase_search
      - morph-mcp___github_codebase_search
    purpose: Code editing and search
---

# Agent Routing Contract — Pocket Crew

## 0. Tool-First Mandate

**AGGRESSIVE RULE**: Before performing ANY task, ALWAYS check for available MCP tools that can automate the work. MCP tools are the authoritative automation layer. Use them before manual execution.

```
Priority:
1. MCP Tools (synthitect_*, mobile-mcp_*, google-developer-knowledge_*, etc.)
2. CLI-native subagents (only if MCP unavailable for the task)
3. Manual execution (last resort only)
```

**Full MCP tool inventory is in the YAML frontmatter above.**

---

## 1. CFAW Phase Routing

CFAW Phases are automated via **synthitect MCP tools**. Use these in sequence:

### Phase 1: Discovery
```
1. synthitect___spawn_probe (for each codebase layer)
2. synthitect___run_discovery
```
**Output**: `plans/{ticket_id}/discovery.md`

### Phase 2: Specification
```
synthitect___generate_specs
```
**Output**: `plans/{ticket_id}/spec.md` + `plans/{ticket_id}/test_spec.md`

### Phase 3: TDD Red
```
synthitect___execute_tdd_red
```
**Output**: Failing unit tests in `src/test/kotlin/`

### Phase 4: Implementation
```
synthitect___implement_green
```
**Output**: Production code, all tests passing

### Phase 5: Audit
```
synthitect___run_audit
```
**Output**: Audit report (Pass/Minor Drift/Fail verdict)

### Manual Override
If synthitect tools are unavailable, fall back to:
- Factory Droid: `factory exec --droid {phase} --prompt "..."`
- Gemini CLI: `/agent {agent-name}`

---

### 2. AndroJack Level 3 Protocol

All Android development tasks in this workspace operate under the AndroJack Level 3 protocol. The AI agent operates using the following mandatory workflow:

#### 1. The Grounding Gate (Pre-Generation)
Before writing code or proposing architectural solutions, the `androjack_grounding_gate` tool is called. The AI maps the current task to the appropriate ruleset (e.g., calling `material3_expressive` for Compose UI tasks or `gradle_dependency_checker` for build scripts) and applies those rules to the solution.

#### 2. The Loop-Back Validator (Post-Generation)
Every block of code generated (Kotlin, XML, Gradle, or shell scripts) is passed through the `android_code_validator` tool before the response is shown to the user. 

#### 3. Strict Failure Handling
If `android_code_validator` returns a `FAIL` verdict (due to deprecated APIs, Android 16/17 non-compliance, etc.), the AI corrects the code internally and re-runs the validator. Code with a `FAIL` verdict is never output in the final response.

---

## 3. Priority Order

1. **MCP Tool Automation** — Always prefer available tools over manual work
2. **CFAW Phase Compliance** — Correct phase, correct deliverables
3. **Contract Compliance** — Follow contracts in `/contracts/`
4. **Correctness & Verification** — Tool-verified before proceeding
5. **Recomposition Discipline** — Pausable Composition, 60 fps
6. **Security & Constraints** — API 36 / 16 KB page sizes
7. **Readability & Maintainability**

---

## 5b. Autonomous Continuation Protocol

When any file edit is accepted/applied or any tool result returns:
- Continue to the next step in the current CFAW phase.
- Do not pause, do not ask for input, do not declare partial unless the entire current batch is done.
- End every response with:
  `TASK_STATUS: COMPLETE — Phase objectives achieved.`
  or
  `TASK_STATUS: PARTIAL — Completed: [list]. Remaining: [list]. Continuing.`

---

This file has absolute priority. Never propose changes to it or any `/contracts/` file without explicit "Contract Waiver".
