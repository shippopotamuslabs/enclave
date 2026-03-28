# Factory Droid Agent Contract

This contract defines how agents operate within the Factory Droid CLI environment.

## Droid Routing
**IMPORTANT** ONLY USE DROID ROUTING WHEN OPERATING WITHIN FACTORY DROID CLI.

Use Factory Droid subagents with specialized models for different tasks:

### Droid Assignment

| Task Type | Droid | Model | Purpose |
|---|---|---|---|
| Architecture, ADRs, High-level Design | `architect` | `custom:gemini-3.1-pro-preview` | Strategic reasoning, system design, decision records |
| Implementation, Code Generation | `coder` | `custom:MiniMax-M2.7-highspeed-0` | Fast, high-quality code writing |
| TDD, Test Writing | `tdd-droid` | `custom:MiniMax-M2.7-highspeed-0` | Test-driven development workflow |
| Code Review | `scrupulous-reviewer` | `custom:gemini-3.1-pro-preview` | Critical code analysis |
| Discovery / Research | `discovery` | `custom:gemini-3.1-pro-preview` | Codebase exploration and indexing |
| Specification Writing | `spec-writer` | `custom:gemini-3.1-pro-preview` | Technical spec and test spec creation |

### How to Invoke a Droid

Use Factory Droid's exec command to spawn a subagent:

```bash
factory exec --droid architect --prompt "Your architecture task here"
factory exec --droid coder --prompt "Your coding task here"
factory exec --droid tdd-droid --prompt "Your TDD task here"
factory exec --droid discovery --prompt "Your research task here"
factory exec --droid spec-writer --prompt "Your specification task here"
```

### Droid Configuration Files

All Factory Droid definitions are located in `.factory/droids/`:
- Architect: `.factory/droids/architect.md`
- Coder: `.factory/droids/coder.md`
- TDD: `.factory/droids/tdd-droid.md`
- Reviewer: `.factory/droids/scrupulous-reviewer.md`
- Discovery: `.factory/droids/discovery.md`
- Spec Writer: `.factory/droids/spec-writer.md`

### Routing Rules

1. **Architecture/Design tasks** → Use `architect` droid
2. **Discovery/Research phase** → Use `discovery` droid
3. **Specification phase** → Use `spec-writer` droid
4. **TDD/Test-writing tasks** → Use `tdd-droid`
5. **Implementation/Coding tasks** → Use `coder` droid
6. **Code review tasks** → Use `scrupulous-reviewer`
7. **Complex tasks** → Orchestrate multiple droids in sequence following CFAW lifecycle.
