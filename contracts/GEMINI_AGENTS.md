# Gemini CLI Agent Contract

This contract defines how agents operate within the Gemini CLI environment.

## Agent Startup
When operating within Gemini CLI, agents are defined as markdown files with YAML frontmatter in `~/.gemini/agents/`.

### Available Agents

| Agent | Purpose |
|---|---|
| `architect` | Strategic reasoning, system design, ADRs |
| `scrupulous-reviewer` | Critical code analysis and security audit |
| `discovery` | Codebase exploration and CFAW Phase 1 |
| `spec-writer` | Technical spec creation and CFAW Phase 2 |
| `tdd-droid` | Test-driven development and CFAW Phase 3 |
| `coder` | Expert Kotlin/Android implementer & Generalist Agent |

## CFAW Phase Routing (Gemini CLI)

When performing CFAW phases in Gemini CLI, use the specialized agent for that phase:

1. **Discovery Phase** → Use `discovery` agent
2. **Specification Phase** → Use `spec-writer` agent
3. **TDD Red Phase** → Use `tdd-droid` agent
4. **Implementation Phase** → Use `coder` agent

To switch to a specific agent in the CLI:
```bash
/agent <agent-name>
```

## Agent Configuration
All Gemini CLI agent definitions are located in `~/.gemini/agents/`. These files define the model (e.g., `gemini-3.1-pro-preview` or `MiniMax-M2.7`) and the toolset available to the agent.
