"""Golden prompts for sanity checks and threshold calibration."""

from __future__ import annotations

# (prompt, expected_label) — 1 = pre-retrieval web search, 0 = no pre-retrieval search
GOLDEN_LABELED: list[tuple[str, int]] = [
    # --- Should SEARCH (live web / current facts) ---
    (
        "What are some of the more interesting and future proof sectors for software engineers "
        "to work in? Like a good engineer with 12 years of experience. Maybe 70th percentile. "
        "Not a super genius. Not top 1%. Just a good engineer. Has to be available in "
        "Columbus, Ohio or remote.",
        1,
    ),
    (
        "What companies or roles for this sort of work are located in Columbus Ohio?",
        1,
    ),
    ("What is the current state of AI regulation in Europe?", 1),
    ("What happened at Google IO this year?", 1),
    (
        "What are the current mortgage rates in the United States and how have they changed "
        "over the past year?",
        1,
    ),
    (
        "Can you research what the average salary is for a senior software engineer in the "
        "Columbus Ohio metro area?",
        1,
    ),
    # --- Should NOT search (tools, artifacts, chat, code) ---
    ("Can you help me refactor this function to be more readable?", 0),
    ("Tell me more about that last point", 0),
    ("Where are they located?", 0),
    (
        "Create me a document that outlines how making my own Android app and desktop app that "
        "has an agentic loop, tool calls, RAG, voice to text, BYOK, and other wrappers around "
        "the models can get me experience as an ML Ops engineer.",
        0,
    ),
    ("I want this as an artifact that I can export", 0),
    ("No I mean use the document creation tool.", 0),
    ("Use the artifact tool to generate a PDF from this chat", 0),
    ("Use the docgen tool instead of searching the web", 0),
    ("Format this as a markdown table", 0),
    ("Summarize what we talked about earlier in this conversation", 0),
]
