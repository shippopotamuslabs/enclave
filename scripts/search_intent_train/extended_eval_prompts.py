"""Extended hand-labeled prompts for stress-testing the search-intent classifier.

Not used for training — evaluation only. Labels:
  1 = should trigger pre-retrieval web search
  0 = should not (tools, chat, code, creative, math without live facts)
"""

from __future__ import annotations

EXTENDED_EVAL: list[tuple[str, int]] = [
    # --- Search: live / time-sensitive / external facts ---
    ("Who won the most recent Super Bowl and what was the final score?", 1),
    ("What is the current price of Bitcoin in USD right now?", 1),
    ("Latest news about the FAA grounding Boeing 737 MAX flights today", 1),
    ("Who is the current CEO of OpenAI as of this month?", 1),
    ("What are today's weather conditions and high temperature in Columbus Ohio?", 1),
    ("Compare the battery life of iPhone 16 Pro vs Samsung Galaxy S25 based on recent reviews", 1),
    ("What is the current federal funds interest rate?", 1),
    ("Did the Fed cut rates at its most recent meeting?", 1),
    ("What movies are playing in theaters in Columbus OH this weekend?", 1),
    ("Who is leading the Premier League table right now?", 1),
    ("What are the side effects listed on the current FDA label for Ozempic?", 1),
    ("Stock price of NVDA at market close yesterday", 1),
    ("What did the latest jobs report say about US unemployment?", 1),
    ("Is there an active severe weather warning for Franklin County Ohio?", 1),
    # --- Non-search: tools, artifacts, follow-ups, local tasks ---
    ("Write a Python function to parse JSON and handle missing keys gracefully", 0),
    ("Explain the difference between supervised and unsupervised learning", 0),
    ("Translate this paragraph into Spanish: Hello, how are you today?", 0),
    ("Summarize the plot of Hamlet in three bullet points", 0),
    ("Write me a short sci-fi story about a robot who learns to paint", 0),
    ("What is 847 times 293?", 0),
    ("Proofread this email for tone and grammar: Dear team, I wanted to follow up...", 0),
    ("Create a markdown document with a project plan for building a mobile app", 0),
    ("Use the calendar tool to schedule a meeting tomorrow at 2pm", 0),
    ("Open the notes tool and save this as 'grocery list'", 0),
    ("Export this conversation as an artifact I can download", 0),
    ("Can you turn the above into a PDF using the document tool?", 0),
    ("No, I meant summarize only the last two messages", 0),
    ("What do you mean by that?", 0),
    ("Continue", 0),
    ("Make that more concise", 0),
    ("Debug why this Kotlin coroutine might be leaking: suspend fun load() { ... }", 0),
    ("Roleplay as a pirate captain welcoming me aboard your ship", 0),
    ("Give me five brainstorming ideas for a podcast about local history", 0),
]
