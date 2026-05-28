"""
Procedurally generated training utterances for search-intent classification.

Goal: high lexical and structural variance for "needs live web pre-retrieval" vs
"in-app / conversational / tool-routed" without overfitting a handful of Enclave strings.

Golden evaluation prompts stay in golden_prompts.py only.
"""

from __future__ import annotations

import random

# --- Word banks (expand freely) ---

CITIES = [
    "Columbus Ohio",
    "Austin Texas",
    "Seattle Washington",
    "Denver Colorado",
    "Chicago Illinois",
    "Portland Oregon",
    "Boston Massachusetts",
    "Atlanta Georgia",
    "San Francisco",
    "New York City",
]

ROLES = [
    "software engineer",
    "DevOps engineer",
    "cybersecurity analyst",
    "machine learning engineer",
    "product manager",
    "data engineer",
    "platform engineer",
    "SRE",
    "mobile developer",
    "MLOps engineer",
]

TOPICS = [
    "AI regulation",
    "quantum computing",
    "electric vehicles",
    "mortgage rates",
    "inflation",
    "remote work policy",
    "GLP-1 drugs",
    "solar panel technology",
    "Kotlin language releases",
    "cybersecurity certifications",
    "401k contribution limits",
    "Supreme Court technology rulings",
    "GPU cloud pricing",
    "intermittent fasting studies",
    "housing market trends",
]

COMPANIES = ["Apple", "Google", "Microsoft", "Samsung", "OpenAI", "Meta", "Amazon", "Tesla"]

YEARS = ["2024", "2025", "2026"]

TOOLS_IN_APP = [
    "document creation tool",
    "artifact tool",
    "docgen tool",
    "web extract tool",
    "image generation tool",
    "manage memories tool",
    "append blocks tool",
]

# --- Search-positive templates (live / current / lookup) ---

SEARCH_OPENERS = [
    "What are the current",
    "What is the latest",
    "Can you look up",
    "Can you find out",
    "Can you research",
    "I need to know what",
    "Tell me about recent",
    "What happened at",
    "How much does it cost to",
    "What are people saying about",
    "I'm trying to figure out which",
    "What companies in {city} are hiring for",
    "What is the job market like for {role} in",
    "What are the best",
    "What were the results of the latest",
]

SEARCH_CLOSERS = [
    "right now?",
    "in {year}?",
    "in the United States?",
    "over the past year?",
    "compared to last year?",
    "for someone with about 10 years of experience?",
    "that are dog friendly?",
    "under 500 dollars?",
    "for residential use?",
    "in major US cities?",
    "and how do they compare?",
    "from reputable sources?",
]

SEARCH_FULL = [
    "What are some future proof sectors for {role} to work in, especially remote or hybrid roles?",
    "What {topic} developments should I know about in {year}?",
    "What happened at the latest {company} developer conference and what were the announcements?",
    "What are {city} employers paying for senior {role} positions this year?",
    "What stocks in the healthcare sector performed best over the last 6 months?",
    "What visa rules apply to a US citizen working remotely from Portugal in {year}?",
    "What conferences for Android developers are scheduled in the next few months?",
    "What is the weather forecast for next week in {city}?",
    "What are the pros and cons of the newest {company} laptop for software development?",
    "What OSHA regulations changed recently for warehouse workers?",
    "What clinical trials are active for {topic} treatments?",
    "What interest rates do banks offer for small business loans through the SBA today?",
    "What are the longest range affordable electric vehicles available now?",
    "What universities have highly rated online masters programs in data science?",
    "What are the side effects reported for the latest COVID booster?",
    "What new features shipped in the most recent Kotlin release?",
    "What do reviewers think about the latest {company} phone versus its main competitor?",
    "What is the average rent for a two bedroom apartment in {city} in {year}?",
    "What are the current mortgage rates and how have they moved since January?",
    "What cybersecurity certifications do employers list most often in job postings this year?",
]

# --- Non-search templates (tools, artifacts, chat, code) ---

NO_SEARCH_FULL = [
    "Can you help me refactor this {lang} function to be more readable?",
    "Write a unit test for the function I just pasted.",
    "Debug this script — it throws a KeyError on line 42.",
    "Summarize what we discussed earlier in this conversation.",
    "Tell me more about that last point you made.",
    "Can you explain that in simpler terms?",
    "What would you recommend based on what I already told you?",
    "OK so what should I do next given my constraints?",
    "Thanks, that makes sense now.",
    "Make it shorter and more direct.",
    "Make it more formal for an executive audience.",
    "Convert this JSON to YAML.",
    "Proofread this email for tone and grammar.",
    "Translate this sentence to Spanish: {phrase}",
    "That's not what I meant — let me rephrase.",
    "No, I mean use the {tool} instead.",
    "Use the {tool} to handle this, not a web search.",
    "I want this as an artifact I can export.",
    "Create a document that outlines the plan we just discussed.",
    "Generate a PDF report from this chat using the artifact flow.",
    "Export our conversation as a markdown file.",
    "Append this section to the artifact we are building.",
    "Run the {tool} on the URL I pasted above.",
    "Search my chat history for where we talked about {topic}.",
    "Add this to my memories — do not search the web.",
    "Format the table you already gave me as CSV.",
    "Rewrite paragraph three so it sounds less salesy.",
    "Give me three bullet alternatives for the intro you wrote.",
    "What do you think of my architecture sketch above?",
    "How would you implement binary search in {lang}?",
    "Create a {lang} data class with fields id, name, and email.",
    "Set the reasoning budget higher for this reply only.",
    "Stop browsing and answer from the context in this thread.",
    "Use {tool} — I'm not asking for outside news.",
    "Draft an outline only; don't look up current events.",
    "Turn your last answer into slide headings.",
    "Compare option A and B from your previous message.",
    "Where in your list above would you put someone with my background?",
    "Elaborate on item 2 from your numbered list.",
    "Never mind, ignore my last question.",
    "Go back to your earlier suggestion about microservices.",
]

# Iterative refinement templates (class 0)
iterative_templates = [
    "Good suggestion, I'll make those changes",
    "Ok, I just updated things with {feature}",
    "How does this look now?",
    "That worked. What about {next_thing}?",
    "I see what you mean. Here's what I changed: {change}",
    "Much cleaner now. The {component} is {state}",
    "This is exactly right. You've iterated from {before} to {after}",
    "Good catch — that one is genuinely confusing because it looks like it should be meaningful",
    "Are these {items} noise or actually serving a purpose?",
    "What would make this better?",
    "Is this clean enough?",
    "How do you even know what {component} is?",
]

# Feedback request templates (class 0)
feedback_templates = [
    "Are these {items} noise or actually serving a purpose?",
    "What would make this better for you?",
    "Is this clean enough?",
    "How do you even know what {component} and {other_component} are?",
    "Can you tell me what error you get with the tools?",
]

# Architecture discussion templates (class 0 when context is sufficient)
architecture_templates = [
    "I'm not going to lie, this is pretty dope working with you inside of the app itself",
    "It's like I am designing you a sort of {metaphor} and you are giving me advice",
    "Yeah honestly this is the next thing I want to work on",
]

LANGS = ["Kotlin", "Python", "Rust", "Java", "TypeScript", "Go"]
PHRASES = [
    "I would like a coffee please",
    "the deployment failed overnight",
    "we need a concise executive summary",
]

FEATURES = ["ellipses idea", "deduplication", "caching", "logging", "error handling", "retry logic"]
NEXT_THINGS = ["this layout problem", "the other controller", "that compile error", "the schema mapping"]
CHANGES = ["removed the unused import", "changed the threshold", "updated the adapter class", "deduplicated the lists"]
COMPONENTS = ["adapter", "classifier", "controller", "service", "model-schema", "tool-schema-0", "tool-schema-1"]
STATES = ["ready", "running", "initialized", "updated", "working perfectly"]
BEFORES = ["v1", "an old format", "scratch", "v2", "initial layout"]
AFTERS = ["v2", "a cleaner approach", "the final build", "v3", "optimized version"]
ITEMS = ["imports", "tokens", "logs", "variables", "references", "parameters"]
METAPHORS = ["digital canvas", "pair programmer", "co-pilot", "sounding board", "collaborative workspace"]
OTHER_COMPONENTS = ["handler", "manager", "port", "repository", "bridge", "wrapper"]


def is_iterative_refinement(message_text: str) -> bool:
    """Identify non-search iterative refinement patterns"""
    indicators = [
        "that worked", "thanks", "now try", "what about",
        "here's my updated", "i changed", "looks good",
        "good catch", "i'll make those changes", "ok i just",
        "how does this look", "is this clean enough",
        "much cleaner now", "this is exactly right",
        "i see what you mean", "done", "fixed", "updated"
    ]
    return any(indicator in message_text.lower() for indicator in indicators)


def extract_refinement_sequences(conversation: list[dict], min_depth: int = 3) -> list[list[str]]:
    """Extract multi-turn refinement sequences from conversations"""
    sequences = []
    for i in range(min_depth - 1, len(conversation)):
        text = conversation[i].get("text", "") or conversation[i].get("value", "") or conversation[i].get("content", "")
        if text and is_iterative_refinement(str(text)):
            # Grab context: 2 messages before + the refinement message
            start_idx = max(0, i - 2)
            sequences.append([
                str(msg.get("text", "") or msg.get("value", "") or msg.get("content", ""))
                for msg in conversation[start_idx:i+1]
            ])
    return sequences


def _fill(template: str, rng: random.Random) -> str:
    return (
        template.format(
            city=rng.choice(CITIES),
            role=rng.choice(ROLES),
            topic=rng.choice(TOPICS),
            company=rng.choice(COMPANIES),
            year=rng.choice(YEARS),
            tool=rng.choice(TOOLS_IN_APP),
            lang=rng.choice(LANGS),
            phrase=rng.choice(PHRASES),
            feature=rng.choice(FEATURES),
            next_thing=rng.choice(NEXT_THINGS),
            change=rng.choice(CHANGES),
            component=rng.choice(COMPONENTS),
            state=rng.choice(STATES),
            before=rng.choice(BEFORES),
            after=rng.choice(AFTERS),
            items=rng.choice(ITEMS),
            metaphor=rng.choice(METAPHORS),
            other_component=rng.choice(OTHER_COMPONENTS),
        )
        .replace("  ", " ")
        .strip()
    )


def generate_search_samples(rng: random.Random, count: int) -> list[str]:
    """Utterances that should trigger pre-retrieval web search."""
    seen: set[str] = set()
    out: list[str] = []
    attempts = 0
    max_attempts = max(count * 80, 1_000)

    while len(out) < count and attempts < max_attempts:
        attempts += 1
        if rng.random() < 0.6:
            text = _fill(rng.choice(SEARCH_FULL), rng)
        else:
            opener = _fill(rng.choice(SEARCH_OPENERS), rng)
            closer = _fill(rng.choice(SEARCH_CLOSERS), rng)
            topic = rng.choice(TOPICS)
            text = f"{opener} {topic} {closer}"

        text = " ".join(text.split())
        if len(text) < 20 or len(text) > 480:
            continue
        if text in seen:
            continue
        seen.add(text)
        out.append(text)

    return out


def generate_no_search_samples(rng: random.Random, count: int) -> list[str]:
    """Utterances that should NOT trigger pre-retrieval web search."""
    seen: set[str] = set()
    out: list[str] = []
    attempts = 0
    max_attempts = max(count * 80, 1_000)

    template_pool = NO_SEARCH_FULL + iterative_templates + feedback_templates + architecture_templates

    while len(out) < count and attempts < max_attempts:
        attempts += 1
        text = _fill(rng.choice(template_pool), rng)
        text = " ".join(text.split())
        if len(text) < 12 or len(text) > 480:
            continue
        if text in seen:
            continue
        seen.add(text)
        out.append(text)

    return out
