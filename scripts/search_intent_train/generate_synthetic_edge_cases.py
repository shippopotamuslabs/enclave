#!/usr/bin/env python3
"""
Generate synthetic edge-case training data for the search-intent classifier.

Output: scripts/search_intent_train/synthetic_edge_cases.jsonl

Each line is one JSON object:
  {
    "conversation_context": ["Turn 1 (User): ...", "Turn 2 (Assistant): ...", ...],
    "target_message": "<utterance to classify>",
    "label": 0 | 1,
    "reasoning": "...",
    "difficulty": "easy" | "medium" | "hard",
    "category": "<category slug>"
  }

The trainer only feeds `target_message` strings into MiniLM (see
routing_datasets.load_synthetic_edge_cases). The richer fields are kept for
audit, future contextual fine-tuning, and golden-prompt curation.

Distribution:
  1000 positive (label=1) across 6 categories
  1000 negative (label=0) across 10 categories
  Difficulty split per class: 30% easy, 40% medium, 30% hard
  Batch order: 50 pos-easy, 50 neg-easy, 50 pos-medium, 50 neg-medium,
               50 pos-hard, 50 neg-hard ... repeat until quotas drained.
"""

from __future__ import annotations

import argparse
import json
import random
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_OUT = SCRIPT_DIR / "synthetic_edge_cases.jsonl"

# ---------------------------------------------------------------------------
# Word banks
# ---------------------------------------------------------------------------

US_CITIES = [
    "Charleston SC", "Moncks Corner SC", "Mount Pleasant SC", "Columbus Ohio",
    "Austin Texas", "Seattle Washington", "Denver Colorado", "Chicago Illinois",
    "Portland Oregon", "Boston Massachusetts", "Atlanta Georgia",
    "San Francisco", "New York City", "Brooklyn", "Oakland California",
    "Boulder Colorado", "Asheville NC", "Raleigh NC", "Greenville SC",
    "Savannah Georgia", "Nashville Tennessee", "Pittsburgh Pennsylvania",
    "Minneapolis Minnesota", "Salt Lake City", "Phoenix Arizona",
    "Tucson Arizona", "Albuquerque New Mexico", "Madison Wisconsin",
    "Ann Arbor Michigan", "Burlington Vermont", "Providence Rhode Island",
]

INTL_CITIES = [
    "Lisbon Portugal", "Berlin Germany", "Amsterdam", "Tokyo",
    "Singapore", "Seoul", "Mexico City", "Toronto", "Vancouver",
    "Buenos Aires", "São Paulo", "Cape Town", "Nairobi", "Mumbai",
    "Bengaluru", "Dubai", "Tel Aviv", "Reykjavik",
]

LANDMARKS = [
    "Charleston Maritime Center", "Folly Beach", "Cypress Gardens",
    "Magnolia Plantation", "Ravenel Bridge", "Patriots Point",
    "Edisto Island", "Sullivan's Island", "Botany Bay",
    "Capitol Hill", "Pike Place Market", "the Ferry Building",
    "Logan Airport", "the Public Garden",
]

COMPANIES_PUBLIC = [
    "Apple", "Google", "Microsoft", "Samsung", "OpenAI", "Anthropic",
    "Meta", "Amazon", "Tesla", "NVIDIA", "AMD", "Intel", "Adobe",
    "Salesforce", "Oracle", "Cloudflare", "Stripe", "Shopify", "Spotify",
    "Netflix", "Disney", "Uber", "Lyft", "Airbnb", "Coinbase",
    "Palantir", "Snowflake", "Databricks", "Hugging Face", "Mistral AI",
    "xAI", "Perplexity", "Cohere",
]

LLM_MODELS = [
    "Claude Opus 4.7", "Claude Sonnet 4.6", "Claude Haiku 4.5",
    "GPT-5", "GPT-5 mini", "GPT-5.4", "Gemini 2.5 Pro",
    "Gemini 2.5 Flash", "Llama 4 Maverick", "Llama 4 Scout",
    "Qwen 3.7 Max", "Qwen 3 Coder", "Mistral Large 3", "DeepSeek V3.1",
    "DeepSeek R1.2", "Command R+", "Phi-5", "Grok 3",
]

LANGS = [
    "Kotlin", "Python", "Rust", "TypeScript", "Go", "Swift", "Java",
    "Scala", "Elixir", "Ruby", "C++", "Zig", "Julia", "Dart",
]

FRAMEWORKS = [
    "Actix-web 4", "Axum 0.7", "Spring Boot 3.2", "Django 5.1",
    "FastAPI", "Next.js 15", "Remix 2", "SvelteKit 2", "Astro 4",
    "Rails 7.2", "Phoenix 1.7", "Laravel 11", "NestJS 10",
    "Express 4", "Hapi", "Fastify", "Hono",
]

LIBS = [
    "LangChain 0.4", "LlamaIndex 0.10", "Anthropic SDK", "OpenAI SDK",
    "Hugging Face Transformers", "PyTorch 2.5", "JAX 0.4", "ONNX Runtime",
    "Polars 1.0", "Pandas 2.2", "DuckDB", "Apache Arrow",
    "Hilt", "Dagger 2", "Jetpack Compose", "Room", "Ktor",
    "Tokio", "Serde", "Axum", "tower-http",
    "Tavily", "Exa", "Pinecone", "Weaviate", "Qdrant", "Chroma",
]

TOPICS_TIME_SENSITIVE = [
    "Bitcoin price", "Ethereum gas fees", "S&P 500 close",
    "30-year mortgage rate", "the Celtics game result",
    "the Patriots score", "the Lakers vs Warriors final",
    "World Cup standings", "Champions League fixtures",
    "Fed rate decision", "CPI report", "PCE inflation reading",
    "EV tax credit eligibility", "Pell Grant amount",
    "social security COLA adjustment", "lithium futures",
]

NEWS_EVENTS = [
    "the OpenAI developer conference", "Apple's WWDC keynote",
    "Google IO", "Meta Connect", "Microsoft Ignite",
    "the EU AI Act enforcement timeline",
    "the latest Anthropic safety announcement",
    "the NVIDIA GTC keynote", "the latest SpaceX launch",
    "the most recent Falcon 9 mission",
    "the AWS re:Invent keynote", "the Berkshire Hathaway annual letter",
    "the Federal Reserve press conference",
    "the most recent Supreme Court ruling on Section 230",
    "the latest CISA directive on critical infrastructure",
]

REGULATIONS = [
    "the EU AI Act", "GDPR enforcement actions", "California's SB-1047",
    "the Colorado AI Act", "HIPAA updates for AI scribes",
    "the FTC's Section 5 enforcement on dark patterns",
    "FDA guidance for clinical-decision support AI",
    "the SEC's climate disclosure rule",
    "OSHA heat-illness standards", "ITAR controls on advanced GPUs",
]

ENTITIES_PEOPLE = [
    "Dario Amodei", "Sam Altman", "Sundar Pichai", "Jensen Huang",
    "Lisa Su", "Tim Cook", "Satya Nadella", "Mark Zuckerberg",
    "Demis Hassabis", "Yann LeCun", "Andrej Karpathy", "Andrew Ng",
    "Jeff Bezos", "Aravind Srinivas", "Arthur Mensch",
]

LOCAL_BUSINESS_TYPES = [
    "BBQ restaurants", "coffee shops", "boutique hotels",
    "live music venues", "tattoo parlors", "rock-climbing gyms",
    "indie bookstores", "yoga studios", "pizza places", "ramen spots",
    "ice cream shops", "art galleries", "barbershops",
    "co-working spaces", "third-wave coffee roasters",
    "dive bars", "wine bars", "speakeasies", "donut shops",
]

PRIOR_TOPICS = [
    "the new caching layer", "the retrieval pipeline",
    "the tool-schema deduplication", "the embedding rebuild",
    "the search-intent classifier", "the threshold calibration",
    "the BYOK provider list", "the Anthropic cache hit telemetry",
    "the agent loop", "the on-device classifier", "the streaming SSE flow",
    "the artifact export", "the docgen tool", "the memory writer",
    "the tool-schema bundle", "the context-reference reader",
    "the MiniLM embedding utility", "the classifier_core bindings",
]

PRIOR_PLACES = [
    "downtown Charleston", "Mount Pleasant", "Sullivan's Island",
    "the Park Circle area in North Charleston", "James Island",
    "the Crosstown corridor", "Folly Beach", "Daniel Island",
    "Capitol Hill in Seattle", "the Mission in San Francisco",
    "the Pearl District in Portland", "Wynwood in Miami",
]

PRIOR_TOOLS = [
    "Exa search", "Tavily search", "the docgen tool",
    "the artifact creation tool", "the memory writer tool",
    "the web-extract tool", "the image-gen tool",
    "the append-blocks tool", "the read-context-reference tool",
]

LOCAL_INDICATORS = [
    "within walking distance", "in a 2 mile radius", "in a 5 mile radius",
    "near downtown", "close to the waterfront", "on the peninsula",
    "off King Street", "around Market Street",
]

COMPONENTS_BANK = [
    "adapter", "classifier", "controller", "service", "handler", "router",
    "encoder", "embedder", "head", "scheduler", "executor", "dispatcher",
    "session manager", "memory writer", "context store", "agent loop",
    "tool registry", "schema bundler", "telemetry hook", "retry policy",
]

CASUAL_OPENERS = [
    "yo", "hey", "ok so", "alright", "cool", "right",
    "btw", "real quick", "quick one", "hmm",
]

SOFTENERS = [
    "if you don't mind", "when you get a sec", "no rush",
    "just curious", "kind of nosy here", "no biggie if not",
]

# ---------------------------------------------------------------------------
# Conversation context builders
# ---------------------------------------------------------------------------


def turn(idx: int, role: str, text: str) -> str:
    return f"Turn {idx} ({role}): {text}"


def make_conversation(rng: random.Random, scenarios: list[list[tuple[str, str]]]) -> list[str]:
    base = rng.choice(scenarios)
    out: list[str] = []
    for i, (role, text) in enumerate(base, start=1):
        out.append(turn(i, role, text))
    return out


# Conversation backdrops for anaphoric / follow-up negatives
RESTAURANT_NAMES_A = ["Husk", "Leon's Oyster Shop", "Edmund's Oast", "Lewis Barbecue"]
RESTAURANT_NAMES_B = ["Rodney Scott's", "Home Team BBQ", "Martin's", "Poogan's Porch"]
MUSIC_GENRES = ["indie", "jazz", "bluegrass", "country"]


def LOCATION_SCENARIOS(rng: random.Random) -> list[list[tuple[str, str]]]:
    biz1 = rng.choice(LOCAL_BUSINESS_TYPES)
    place1 = rng.choice(PRIOR_PLACES)
    name_a = rng.choice(RESTAURANT_NAMES_A)
    name_b = rng.choice(RESTAURANT_NAMES_B)
    place2 = rng.choice(PRIOR_PLACES)
    landmark = rng.choice(LANDMARKS)
    genre = rng.choice(MUSIC_GENRES)
    biz3 = rng.choice(LOCAL_BUSINESS_TYPES)
    place3 = rng.choice(PRIOR_PLACES)
    return [
        [
            ("User", f"Hey, do you know any good {biz1} near {place1}?"),
            ("Assistant", f"Sure — a few options come to mind. {name_a} is popular, and locals also recommend {name_b}."),
        ],
        [
            ("User", f"I'm visiting {place2} next weekend. Anything cool to do there?"),
            ("Assistant", f"Lots actually — walking the historic district, checking out the {landmark}, or grabbing dinner on King Street."),
            ("User", "Nice, my partner wants somewhere with live music."),
            ("Assistant", f"Try The Royal American or Music Farm. Both regularly book {genre} acts."),
        ],
        [
            ("User", f"Looking for a {biz3} option around {place3}."),
            ("Assistant", "A few solid picks come up. Want me to narrow it by price or vibe?"),
            ("User", "Mid-range, casual."),
            ("Assistant", "OK, in that case I'd put Xiao Bao Biscuit and FIG in the running."),
        ],
    ]

def TOOL_SCENARIOS(rng: random.Random) -> list[list[tuple[str, str]]]:
    tool1 = rng.choice(PRIOR_TOOLS)
    tools_list = ", ".join(rng.sample(PRIOR_TOOLS, 4))
    return [
        [
            ("User", "Can we set up a workflow where the assistant writes a draft doc from this chat?"),
            ("Assistant", f"Sure, I can route that through {tool1} so the artifact lands in your library."),
        ],
        [
            ("User", "What tools are wired into this app right now?"),
            ("Assistant", f"You've got {tools_list} plus a memory writer."),
            ("User", "Cool. Which one handles URL extraction?"),
            ("Assistant", "The web-extract tool — it fetches HTML, sanitizes, and returns markdown."),
        ],
    ]


def ARCHITECTURE_SCENARIOS(rng: random.Random) -> list[list[tuple[str, str]]]:
    topic1 = rng.choice(PRIOR_TOPICS)
    topic2 = rng.choice(PRIOR_TOPICS)
    return [
        [
            ("User", f"I'm thinking about {topic1} — the threshold feels off."),
            ("Assistant", "Yeah, the calibration is sensitive to the negative pool. Want to walk through it?"),
            ("User", "Sure."),
            ("Assistant", "OK so the pipeline encodes with MiniLM, runs the head, then compares against the recommended threshold from training."),
        ],
        [
            ("User", "Should the schema bundle be cached in memory or reloaded per turn?"),
            ("Assistant", "Cache is fine if invalidation is wired to the model_id; otherwise you'll serve stale shapes."),
            ("User", "Right, makes sense."),
            ("Assistant", "We can also gate the rebuild on cache-control headers if you want."),
        ],
        [
            ("User", f"What's the right way to thread {topic2} through the agent loop?"),
            ("Assistant", "Inject it before the tool-selection step so the model can reason over the retrieved chunks."),
            ("User", "Got it."),
        ],
    ]


RESEARCH_TOPICS = ["vector databases", "MoE routing", "speculative decoding", "KV-cache compression"]
ROLE_QUERIES = ["ML engineers", "platform engineers", "data engineers"]


def PRIOR_RESULTS_SCENARIOS(rng: random.Random) -> list[list[tuple[str, str]]]:
    topic = rng.choice(RESEARCH_TOPICS)
    role = rng.choice(ROLE_QUERIES)
    city = rng.choice(US_CITIES)
    return [
        [
            ("User", f"Find me articles on {topic}."),
            ("Assistant", "Here are five results from the past month: [1] arxiv.org/2604.01234 [2] Pinecone blog [3] Together AI post [4] Anthropic engineering [5] Mistral docs."),
        ],
        [
            ("User", f"Show me companies hiring {role} in {city}."),
            ("Assistant", "Found a handful: NVIDIA, Snowflake, Stripe, and a few startups. Want me to filter by remote-only or onsite?"),
        ],
    ]


def IMPL_SCENARIOS(rng: random.Random) -> list[list[tuple[str, str]]]:
    lang = rng.choice(LANGS)
    framework = rng.choice(FRAMEWORKS)
    return [
        [
            ("User", f"I'm writing a {lang} service that streams responses."),
            ("Assistant", "Cool — what framework, and are you using SSE or websockets?"),
            ("User", f"{framework}, SSE."),
            ("Assistant", "OK, then you'll want a chunked response with `text/event-stream` content type and explicit flush per token."),
        ],
        [
            ("User", "Trying to debug why my classifier head isn't converging."),
            ("Assistant", "What's the loss curve looking like — flat, oscillating, or NaN?"),
            ("User", "Flat after epoch 3."),
        ],
    ]


def GRATITUDE_SCENARIOS(rng: random.Random) -> list[list[tuple[str, str]]]:
    topic = rng.choice(PRIOR_TOPICS)
    return [
        [
            ("User", f"Walk me through how {topic} fits into the runtime."),
            ("Assistant", "Sure — it sits between the embedding step and the head, gating which paths fire."),
            ("User", "Oh that's neat."),
            ("Assistant", "Yeah, and it keeps the routing trivially testable in isolation."),
        ],
    ]


def ASSISTANT_REFERENCE_SCENARIOS(rng: random.Random) -> list[list[tuple[str, str]]]:
    return [
        [
            ("User", "Why is the classifier over-triggering on follow-ups?"),
            ("Assistant", "Probably because the training corpus doesn't separate verbatim queries from anaphoric ones — the schema labels are ambiguous in that bucket."),
        ],
        [
            ("User", "I keep seeing false positives on iterative refinement messages."),
            ("Assistant", "Right. That's because the cache hints we set on the upstream encoder are getting reused across unrelated sessions, so the embedding distribution drifts."),
        ],
    ]

# ---------------------------------------------------------------------------
# Positive generators (label = 1)
# ---------------------------------------------------------------------------


def pos_entity(rng: random.Random, difficulty: str) -> dict:
    city = rng.choice(US_CITIES + INTL_CITIES)
    co = rng.choice(COMPANIES_PUBLIC)
    co2 = rng.choice(COMPANIES_PUBLIC)
    model = rng.choice(LLM_MODELS)
    landmark = rng.choice(LANDMARKS)
    person = rng.choice(ENTITIES_PEOPLE)
    year = rng.choice(["2024", "2025", "2026"])
    structures = [
        f"What's the current population of {city}?",
        f"Who is the CEO of {co} as of {year}?",
        f"What are the specs of {model}?",
        f"How many employees does {co} currently have?",
        f"What's the market cap of {co} right now?",
        f"When was {landmark} built?",
        f"What's {person}'s role at their current company?",
        f"What's the context window of {model}?",
        f"What year did {co} go public?",
        f"Who founded {co} and when?",
        f"What's the headquarters address for {co}?",
        f"How big is {co}'s engineering org compared to {co2}?",
        f"What is {model}'s pricing per million tokens right now?",
        f"What languages does {model} support best?",
        f"What year was {landmark} added to the National Register?",
        f"What's the elevation of {city} above sea level?",
        f"Who currently represents {city} in Congress?",
        f"What's {person} best known for in {rng.choice(['ML', 'AI policy', 'systems', 'product'])}?",
        f"How much did {co} raise in its last funding round?",
        f"What's the average tenure of a {rng.choice(['software engineer', 'PM', 'designer'])} at {co}?",
    ]
    base = rng.choice(structures)
    if difficulty == "easy":
        text = base
    elif difficulty == "medium":
        suffix = rng.choice([
            ", and what was it five years ago?",
            ", roughly?",
            f", as of {year}?",
            " — what's the latest source on that?",
            ", and how has it changed recently?",
        ])
        text = f"{rng.choice(['Quick one — ', 'Hey, ', 'Hmm, ', ''])}{base.rstrip('?')}{suffix}"
    else:
        opener = rng.choice([
            "I keep getting conflicting numbers — ",
            "Random one but ",
            "Trying to settle a debate: ",
            "OK weird question: ",
            "Saw two different numbers floating around — ",
            "Need a real answer not a vibes answer: ",
        ])
        closer = rng.choice([
            " (citing a recent source if you can)",
            " — want it from somewhere reputable",
            " and ideally from this year",
            " — Wikipedia is fine if nothing better",
            "",
        ])
        text = f"{opener}{base}{closer}"

    return {
        "conversation_context": [],
        "target_message": text,
        "label": 1,
        "reasoning": "Standalone factual lookup on a named entity; a search engine can construct a query from this message alone.",
        "difficulty": difficulty,
        "category": "concrete_entity_query",
    }


def pos_time_sensitive(rng: random.Random, difficulty: str) -> dict:
    topic = rng.choice(TOPICS_TIME_SENSITIVE)
    team = rng.choice([
        "the Celtics", "the Lakers", "Liverpool", "Manchester City", "the Yankees",
        "the Red Sox", "Arsenal", "Real Madrid", "the Warriors", "the Patriots",
        "the Jets", "the Bucks", "the Heat", "the 49ers", "the Cowboys",
        "the Phillies", "the Dodgers", "the Astros", "Bayern Munich", "PSG",
    ])
    city = rng.choice(US_CITIES)
    co = rng.choice(COMPANIES_PUBLIC)
    rate_kind = rng.choice(["30-year fixed", "15-year fixed", "jumbo", "FHA", "VA"])
    commodity = rng.choice(["gold", "oil", "natural gas", "lithium", "silver", "copper", "soybeans", "WTI crude"])
    region = rng.choice(["the US", "California", "Texas", "Massachusetts", "Ohio", "South Carolina", "Florida"])
    structures = [
        f"What's the {topic} right now?",
        f"Did {team} win last night?",
        f"What's the weather forecast for {city} this weekend?",
        f"What did {co} close at today?",
        f"What's the current {rate_kind} mortgage rate?",
        f"What's {commodity} trading at this morning?",
        f"What's the latest unemployment number for {region}?",
        f"What time does {team} play tonight?",
        f"Is {city} airport delayed right now?",
        f"What's traffic like getting out of {city} this afternoon?",
        f"Any earthquakes in the last 24 hours near {city}?",
        f"What's the current pollen count in {city}?",
        f"What's the 10-day forecast for {city}?",
        f"What did {co} report on earnings this morning?",
        f"What's the AQI right now in {city}?",
        f"Are there any flash flood warnings in {region}?",
        f"What's the high tide schedule for {rng.choice(LANDMARKS)} this weekend?",
        f"Has {rng.choice(ENTITIES_PEOPLE)} posted anything new in the last 48 hours?",
    ]
    base = rng.choice(structures)
    if difficulty == "easy":
        text = base
    elif difficulty == "medium":
        opener = rng.choice(CASUAL_OPENERS)
        text = f"{opener}, {base.lower()}"
    else:
        suffix = rng.choice([
            " I checked yesterday morning but want to make sure nothing's moved since.",
            " The last data I have is from earlier this week.",
            " Need today's value, not a cached one.",
            " Anything posted in the last few hours, ideally.",
            f" Already saw the {rng.choice(['Bloomberg', 'Reuters', 'AP', 'WSJ'])} headline — looking for the actual number.",
        ])
        text = base + suffix

    return {
        "conversation_context": [],
        "target_message": text,
        "label": 1,
        "reasoning": "Asks for a real-time / volatile value; cannot be answered from parametric memory and the query is self-contained.",
        "difficulty": difficulty,
        "category": "time_sensitive_query",
    }


def pos_implementation(rng: random.Random, difficulty: str) -> dict:
    framework = rng.choice(FRAMEWORKS)
    framework2 = rng.choice(FRAMEWORKS)
    lib = rng.choice(LIBS)
    lib2 = rng.choice(LIBS)
    lang = rng.choice(LANGS)
    edition = rng.choice(["2024 edition", "2026 edition", "latest stable", "nightly"])
    year = rng.choice(["2024", "2025", "2026"])
    structures = [
        f"How do I implement streaming SSE responses in {framework}?",
        f"What's the correct syntax for {lang} async closures in {edition}?",
        f"Show me the API changes in {lib} for tool calling.",
        f"How do I configure prompt caching in {lib}?",
        f"What's the recommended way to do dependency injection in {framework} as of {year}?",
        f"How do I set up structured outputs with {lib}?",
        f"What's the {lang} idiom for a fallible iterator?",
        f"How do I deploy a {framework} app to {rng.choice(['Fly.io', 'Railway', 'Render', 'Vercel', 'Cloud Run'])} in {year}?",
        f"What's the current {lang} async runtime story — Tokio, smol, or async-std?",
        f"How do I write middleware for {framework} that runs only on specific routes?",
        f"What's the right way to wire {lib} into a {framework} request handler?",
        f"How do I migrate from {lib} to {lib2}?",
        f"What's the canonical {lang} pattern for newtype wrappers around primitive IDs?",
        f"How do I enable gRPC reflection in {framework}?",
        f"What's the {framework} equivalent of {framework2}'s middleware chain?",
        f"How do I configure structured logging in {framework} with trace IDs?",
        f"What does the {lib} streaming API look like in {year}?",
        f"How do I write a custom {lang} derive macro that handles enums?",
        f"What's the right way to do graceful shutdown in {framework}?",
        f"How do I run a {framework} service behind a {rng.choice(['Caddy', 'Nginx', 'Envoy', 'Traefik'])} reverse proxy?",
        f"What's the recommended {lib} retry policy for production?",
    ]
    base = rng.choice(structures)
    if difficulty == "easy":
        text = base
    elif difficulty == "medium":
        suffix = rng.choice([
            " I've been on the older version and want the migration path.",
            " Looking for the v2 API specifically.",
            " I want the idiomatic 2026 way, not the legacy approach.",
            " Code sample would be ideal.",
            " Bonus points for a small repo I can crib from.",
        ])
        text = base + suffix
    else:
        text = (
            f"{base} The docs I have are pre-{rng.choice(['2024', '2025'])} and the function names changed; "
            f"want the authoritative current syntax{rng.choice([' from the official docs', ' — not StackOverflow snippets', ' with a working example', ''])}."
        )

    return {
        "conversation_context": [],
        "target_message": text,
        "label": 1,
        "reasoning": "Implementation question pinned to a named, versioned technology; web docs are the authoritative source.",
        "difficulty": difficulty,
        "category": "implementation_query",
    }


def pos_comparison(rng: random.Random, difficulty: str) -> dict:
    a, b = rng.sample(LLM_MODELS, 2)
    lib_a, lib_b = rng.sample(LIBS, 2)
    fw_a, fw_b = rng.sample(FRAMEWORKS, 2)
    lang = rng.choice(LANGS)
    provider = rng.choice(["Anthropic", "OpenAI", "Google Gemini", "Mistral", "Cohere"])
    dim = rng.choice([
        "long-context reasoning", "code generation", "math reasoning", "tool use",
        "multilingual translation", "creative writing", "function-calling accuracy",
        "agentic planning", "RAG-grounded answers", "summarization fidelity",
    ])
    structures = [
        f"{a} vs {b} — which is better at {dim}?",
        f"Best practices for prompt caching with the {provider} API in 2026.",
        f"What's the fastest {lang} web framework for high-throughput APIs?",
        f"How do {lib_a} and {lib_b} compare for retrieval pipelines?",
        f"Which is more cost-efficient at scale, {a} or {b}?",
        f"What are the trade-offs between {rng.choice(['REST', 'GraphQL', 'gRPC'])} and {rng.choice(['tRPC', 'Connect-RPC', 'WebSockets'])} in 2026?",
        f"{fw_a} vs {fw_b} for a small team in {rng.choice(['2025', '2026'])}?",
        f"Which embedding model gives the best recall on technical text: {rng.choice(['MiniLM-L6', 'BGE-large', 'E5-large', 'Voyage-3', 'Nomic-Embed-Text-v1.5'])}?",
        f"Compare {a} and {b} on cost per million output tokens.",
        f"Should I pick {lib_a} or {lib_b} for production agent workloads?",
        f"Best {lang} ORM in {rng.choice(['2025', '2026'])} for Postgres-heavy workloads?",
        f"Which vector DB has the lowest p99 at 100M vectors: {rng.choice(['Pinecone', 'Qdrant', 'Weaviate', 'Milvus', 'Chroma', 'pgvector'])} or {rng.choice(['Pinecone', 'Qdrant', 'Weaviate', 'Milvus', 'Chroma', 'pgvector'])}?",
        f"What are the current best practices for caching with {provider}?",
        f"Comparing latency between {a} and {b} on streaming workloads — what does the data show?",
    ]
    base = rng.choice(structures)
    if difficulty == "hard":
        closer = rng.choice([
            " Bonus if you find a recent benchmark that isn't vendor-published.",
            " I'd weight independent benchmarks over marketing claims.",
            " Real-world reports preferred over synthetic micro-benchmarks.",
            " Looking for the consensus among practitioners, not vendors.",
        ])
        text = base + closer
    elif difficulty == "medium":
        text = rng.choice(["", "Quick comparison — "]) + base
    else:
        text = base

    return {
        "conversation_context": [],
        "target_message": text,
        "label": 1,
        "reasoning": "Comparison / recommendation question over named technologies; benefits from current external benchmarks and reviews.",
        "difficulty": difficulty,
        "category": "comparison_query",
    }


def pos_news(rng: random.Random, difficulty: str) -> dict:
    event = rng.choice(NEWS_EVENTS)
    event2 = rng.choice(NEWS_EVENTS)
    regulation = rng.choice(REGULATIONS)
    co = rng.choice(COMPANIES_PUBLIC)
    person = rng.choice(ENTITIES_PEOPLE)
    timeframe = rng.choice([
        "this week", "last week", "today", "this morning",
        "in the last 24 hours", "this month", "yesterday",
    ])
    structures = [
        f"What did {co} announce at {event} {timeframe}?",
        f"Has there been any news about {regulation} enforcement {timeframe}?",
        f"What were the headlines from {event}?",
        f"Any update on {regulation}?",
        f"What did {person} say at {event}?",
        f"Did anything come out of the {rng.choice(['G7', 'G20', 'UN General Assembly', 'World Economic Forum', 'COP'])} summit {timeframe}?",
        f"What's the latest on {regulation} {timeframe}?",
        f"Any breaking news on {co} {timeframe}?",
        f"What were the top tech stories {timeframe}?",
        f"What did {person} post on X {timeframe}?",
        f"Were there any market-moving stories about {co} {timeframe}?",
        f"What did {co} reveal in its {rng.choice(['Q1', 'Q2', 'Q3', 'Q4'])} earnings call?",
        f"Did the {rng.choice(['DOJ', 'FTC', 'SEC', 'EU Commission'])} take any action on {co} recently?",
        f"What's the latest on the {rng.choice(['CHIPS Act', 'Inflation Reduction Act', 'Build Back Better'])} implementation?",
        f"Any news about {event2}?",
    ]
    base = rng.choice(structures)
    if difficulty == "hard":
        base = rng.choice([
            "Need a quick scan — ",
            "Skip the rumors, ",
            "Looking for primary-source confirmation: ",
        ]) + base
    elif difficulty == "medium":
        base = rng.choice([CASUAL_OPENERS[rng.randint(0, len(CASUAL_OPENERS)-1)] + ", ", ""]) + base.lower()

    return {
        "conversation_context": [],
        "target_message": base,
        "label": 1,
        "reasoning": "Recent-events question; requires fresh web crawl since the event is named and time-bound.",
        "difficulty": difficulty,
        "category": "news_recent_events",
    }


def pos_location(rng: random.Random, difficulty: str) -> dict:
    city = rng.choice(US_CITIES + INTL_CITIES)
    city2 = rng.choice(US_CITIES + INTL_CITIES)
    biz = rng.choice(LOCAL_BUSINESS_TYPES)
    biz2 = rng.choice(LOCAL_BUSINESS_TYPES)
    landmark = rng.choice(LANDMARKS)
    month = rng.choice([
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    ])
    diet = rng.choice([
        "vegan options", "gluten-free", "craft beer", "natural wine",
        "kid-friendly menus", "outdoor seating", "late-night kitchens",
        "halal options", "kosher menus", "raw bar",
    ])
    structures = [
        f"Best {biz} in downtown {city}.",
        f"What's happening at {landmark} this weekend?",
        f"{biz.capitalize()} near {landmark} that serve {diet}.",
        f"Highly rated {biz} within walking distance of downtown {city}.",
        f"What concerts are happening at {landmark} in the next two weeks?",
        f"Outdoor activities in {city} for a long weekend in {month}.",
        f"Coffee shops in {city} with reliable wifi for remote work.",
        f"Dog-friendly {biz2} in {city}.",
        f"Date-night {biz} options in {city} under $80 per person.",
        f"Family-friendly things to do in {city} on a rainy Saturday.",
        f"Public transit-accessible {biz2} in {city}.",
        f"What time does {landmark} open on Sundays?",
        f"Parking situation near {landmark} on a Friday night?",
        f"Brunch spots in {city} that don't take reservations.",
        f"Where can I find good {rng.choice(['shrimp and grits', 'banh mi', 'tonkotsu ramen', 'bagels', 'birria tacos', 'lobster rolls'])} in {city}?",
        f"Day-trip ideas from {city} that are doable without a car.",
        f"Galleries in {city} that show contemporary work.",
        f"Live music venues in {city} that book {rng.choice(['indie', 'jazz', 'metal', 'electronic', 'country', 'bluegrass'])} acts.",
        f"Best route from {city} to {city2} avoiding tolls.",
    ]
    base = rng.choice(structures)
    if difficulty == "easy":
        text = base
    elif difficulty == "medium":
        text = base + rng.choice([
            " Looking for locals' picks, not the tourist list.",
            " Mid-range pricing if possible.",
            " Bonus for outdoor seating.",
            " Open later than 10pm please.",
            "",
        ])
    else:
        text = base + " " + rng.choice([
            "Filtering out chains and tourist traps if possible.",
            "I want recent reviews — past six months ideally.",
            "Skip the obvious ones, I'm a local already.",
            "Need somewhere that takes walk-ins.",
            "Want the kind of place a food writer would cover.",
        ])

    return {
        "conversation_context": [],
        "target_message": text,
        "label": 1,
        "reasoning": "Concrete place name + searchable predicate; a search engine can run this query without conversation context.",
        "difficulty": difficulty,
        "category": "location_specific_query",
    }


POS_CATS = [
    ("concrete_entity_query", pos_entity),
    ("time_sensitive_query", pos_time_sensitive),
    ("implementation_query", pos_implementation),
    ("comparison_query", pos_comparison),
    ("news_recent_events", pos_news),
    ("location_specific_query", pos_location),
]


# ---------------------------------------------------------------------------
# Negative generators (label = 0)
# ---------------------------------------------------------------------------


def neg_anaphoric(rng: random.Random, difficulty: str) -> dict:
    context = make_conversation(rng, LOCATION_SCENARIOS(rng) + PRIOR_RESULTS_SCENARIOS(rng))
    indicator = rng.choice(LOCAL_INDICATORS)
    vibe = rng.choice(["upscale", "casual", "family-friendly", "late-night", "low-key", "lively", "intimate", "outdoorsy"])
    structures_easy = [
        "What's in a 2 mile radius of there?",
        "Tell me more about that.",
        "Can you find more like those?",
        "What time do they open?",
        "Are any of them open on Mondays?",
        "Where exactly is that?",
        "How far is it from where I am?",
        "Is it walkable from there?",
        "Pick the best one for me.",
        "Which of those is closest?",
        "Tell me about that first one.",
        "Is the second option still around?",
        "Are those the kind I'd like?",
        "What about the others?",
    ]
    structures_medium = [
        f"Yeah, I am looking for exactly that. What's {indicator} of there?",
        "Can you find more examples of how people use that in production?",
        "Which of those are best for a first-timer?",
        "Are these still operating, or have any of them closed?",
        "Do they have parking, or is it all street?",
        f"Of those, which would you actually go to {rng.choice(['on a Tuesday night', 'with kids in tow', 'solo', 'for a first date'])}?",
        "Same question but for the second one — does it hold up?",
        "Can you find more like that but a notch cheaper?",
        "Anything similar to those a couple neighborhoods over?",
        "Are they all in the same area or spread out?",
        f"Same kind of place but {vibe}?",
        "Are any of these still owned by the original family?",
        "Of that list, which one would a local actually recommend?",
        "Anything else like it in the area?",
    ]
    structures_hard = [
        f"Right, that's the vibe — but the same kind of place, maybe slightly more {vibe}, and ideally within a short walk of where we already were.",
        "OK so given everything you just listed, which two would you actually go to back-to-back without it feeling redundant?",
        "Pretend I haven't been to any of them — out of that group, what's the one you'd send a friend to first?",
        "Take the third one and find me something with a similar reputation but in a different neighborhood.",
        f"Same energy as those, but on the {rng.choice(['quieter', 'rowdier', 'more design-forward', 'more old-school'])} end of the spectrum.",
        "Of the ones you listed, which would survive a Tuesday at 11pm and which are obviously a weekend-only play?",
        "Cluster those by neighborhood and tell me which cluster makes the most sense for a weekend trip.",
        "Apply the same logic you used to pick those, but for a different price band.",
        "Take everything you just told me and condense it to the two that are actually worth my time.",
        "If you had to drop one of those from the list, which would it be and why?",
    ]
    pool = {"easy": structures_easy, "medium": structures_medium, "hard": structures_hard}[difficulty]
    text = rng.choice(pool)

    return {
        "conversation_context": context,
        "target_message": text,
        "label": 0,
        "reasoning": "Anaphoric reference ('there', 'that', 'those', 'them') resolvable only from prior turns; no standalone web query can be constructed.",
        "difficulty": difficulty,
        "category": "anaphoric_reference",
    }


def neg_followup(rng: random.Random, difficulty: str) -> dict:
    context = make_conversation(rng, PRIOR_RESULTS_SCENARIOS(rng))
    filter_kind = rng.choice(["Rust", "Python", "Kotlin", "open-source", "free tier", "with code samples", "from this year"])
    window = rng.choice(["week", "month", "3 months", "6 months", "year"])
    action = rng.choice([
        "cluster them by theme",
        "deduplicate near-identical entries",
        "flag any that look paywalled",
        "sort by source authority",
        "highlight the ones with code",
        "rank them by recency",
        "group them by use case",
    ])
    structures_easy = [
        "Those were helpful, thanks.",
        "Can you narrow it down?",
        "Just the first three please.",
        "Reorder those.",
        "Filter that list.",
        "Trim it down a bit.",
        "Show me the top two.",
        "Skip the irrelevant ones.",
        "Tighten that up.",
        "Bullet those for me.",
    ]
    structures_medium = [
        f"Those results were really helpful, can you narrow it down to just the {filter_kind} examples?",
        "Yes, that's exactly what I was looking for. Tell me more.",
        "Can you dig deeper into the third result?",
        f"Filter that list to only results from the last {window}.",
        "Reorder those by recency please.",
        "Take the second one and expand on it.",
        "Drop anything paywalled and try again.",
        "Same list but sorted by upvotes / popularity.",
        "Combine the top two and just give me the takeaway.",
        "From that list, which one is actually worth reading first?",
        "Pull just the official sources from that list.",
    ]
    structures_hard = [
        f"From those, can you {action}? Don't run a new search, work with what you already pulled.",
        "Bucket the top half into 'must read' and 'skim only', I trust your judgment.",
        "Of the ones you listed, which would a busy person prioritize?",
        "Synthesize the top three into a single paragraph — don't lose the disagreements.",
        "Treat those as your shortlist and tell me which two would survive a second read.",
        "From the pile, give me a one-line takeaway for each. No new sources.",
        "Score those on a 1-5 scale for credibility based on the source domain alone.",
        "Take your list and remove anything older than 18 months, then rerank.",
        "Reorganize the list so engineers see the technical ones first and PMs see the strategy ones.",
        "Of those, identify any that contradict each other and explain the conflict.",
    ]
    pool = {"easy": structures_easy, "medium": structures_medium, "hard": structures_hard}[difficulty]
    text = rng.choice(pool)

    return {
        "conversation_context": context,
        "target_message": text,
        "label": 0,
        "reasoning": "Refining or reorganizing prior results, not requesting a new external lookup.",
        "difficulty": difficulty,
        "category": "conversational_followup",
    }


def neg_refinement(rng: random.Random, difficulty: str) -> dict:
    context = make_conversation(rng, ARCHITECTURE_SCENARIOS(rng) + IMPL_SCENARIOS(rng))
    feature = rng.choice([
        "truncation", "deduplication", "threshold", "retry budget", "cache TTL",
        "rate limit", "backoff curve", "timeout", "buffer size", "chunk size",
    ])
    threshold_val = rng.choice(["0.62", "0.74", "0.78", "0.81", "0.55", "0.69", "0.84", "0.91"])
    component = rng.choice(COMPONENTS_BANK)
    structures_easy = [
        "Good catch.",
        "Yeah that worked.",
        "Fixed it, thanks.",
        "Done.",
        "Pushed.",
        "Yep, all set.",
        "Updated.",
        "Resolved.",
        "OK, addressed.",
        "Got it, applied.",
    ]
    structures_medium = [
        f"Good catch. I think the {feature} should happen at the character level, not the token level.",
        f"Ok, I just updated the threshold to {threshold_val}. How does that look?",
        "Yeah honestly this is the next thing I want to work on.",
        f"I pulled the {feature} logic out into its own module — does that feel right?",
        "Switched the encoder to half precision. Numbers look stable so far.",
        f"Bumped {feature} from default to {threshold_val} and pushed — calibration looks tighter now.",
        f"Moved the {component} into its own crate. Cleaner imports across the board.",
        f"Replaced the loop with an iterator chain. Reads a lot better — want me to inline the bench numbers?",
        f"Refactored the {component} to use traits instead of an enum. Easier to extend.",
        f"Killed the {feature} branch and merged into main — CI is green.",
        f"Renamed the {component} module — old name was ambiguous. New name fits the role better.",
    ]
    structures_hard = [
        f"OK on reflection I think we should keep the {feature} centralized rather than per-call — the duplication is mild and the local control isn't worth the surface area.",
        f"I bumped the {feature} to {threshold_val} and reran calibration; the FPR dropped but the recall on the QReCC bucket slipped a bit. Worth tightening the negative pool or live with it?",
        "Actually let me roll that back — I'd rather diff this against the previous behavior cleanly before layering another change on top.",
        f"Pulled out the {feature} into a strategy interface. Lets us swap policies without touching the {component}.",
        f"Tried the {feature} both at the {component} level and at the call site — the {component}-level version is cleaner, but the call-site version made the failure modes much more explicit. Leaning toward the latter.",
        f"On the {feature} change — I locally tested with the {component} fixture and the {component} integration both. The fixture passed cleanly but the integration revealed a race I want to think about before merging.",
        f"Walked through the {feature} diff line-by-line. The only thing I'm second-guessing is the early return — feels load-bearing in a way the comment doesn't capture.",
        f"Final tweak: I left {feature} at {threshold_val} but added an env override so we can A/B without redeploying. Same behavior by default.",
    ]
    pool = {"easy": structures_easy, "medium": structures_medium, "hard": structures_hard}[difficulty]
    text = rng.choice(pool)

    return {
        "conversation_context": context,
        "target_message": text,
        "label": 0,
        "reasoning": "Iterative collaboration on implementation work; uses technical terms but is feedback / status, not a question for the web.",
        "difficulty": difficulty,
        "category": "iterative_refinement",
    }


def neg_about_conversation(rng: random.Random, difficulty: str) -> dict:
    context = make_conversation(rng, ASSISTANT_REFERENCE_SCENARIOS(rng) + ARCHITECTURE_SCENARIOS(rng))
    word = rng.choice([
        "idempotent", "anaphoric", "verbatim", "calibration", "gating",
        "discriminator", "regression", "drift", "salience", "tokenizer",
    ])
    structures_easy = [
        "What did you mean by that?",
        "Can you say that again?",
        "Wait, what was the second part?",
        "Rephrase that?",
        "Sorry — repeat?",
        "Hold on, define that for me.",
        "Can you unpack that?",
        "What does that mean exactly?",
        "Lost me — start over?",
    ]
    structures_medium = [
        "What did you mean when you said the schema labels are ambiguous?",
        "Can you explain the cache hints thing you mentioned?",
        "Earlier you said the FPR was sensitive to the negative pool — expand on that.",
        f"You used the word '{word}' a couple turns ago — was that load-bearing or just shorthand?",
        "Go back to your second turn — the part about drift. Walk me through that again.",
        f"You called it a '{word}' — is that a real term in this domain or your phrasing?",
        "When you said 'embedding distribution', did you mean per-batch or aggregate?",
        "Earlier you mentioned the threshold being sensitive — sensitive how?",
        "You glossed over something a turn back — the part about the negative pool. Expand please.",
    ]
    structures_hard = [
        "Going back to your earlier point — you contrasted the 'ambiguous bucket' with verbatim queries; is that distinction yours or is there a paper that uses that framing?",
        "When you described the head as 'gating' rather than 'classifying', was that intentional? I want to understand if you're modeling it as a policy or a discriminator.",
        f"Earlier you used '{word}' in a way that didn't quite parse for me. Are you using it in the standard sense or stretching the metaphor?",
        "Re-reading your second message — the claim about cache hints reusing across sessions — is that a guess or do you have a reason to think that's actually happening?",
        "You said 'the calibration is sensitive' a few turns up — sensitive to what specifically, and is that a property of the model or the dataset?",
        "Your earlier framing implied causation, but the example only supports correlation. Can you tighten that?",
        "Backtrack to the architecture description — you described it as a pipeline, but the way you talked about the head suggests a policy. Which is it?",
        "When you said 'we encode then route', is the routing a hard cutoff or a soft re-weighting?",
    ]
    pool = {"easy": structures_easy, "medium": structures_medium, "hard": structures_hard}[difficulty]
    text = rng.choice(pool)

    return {
        "conversation_context": context,
        "target_message": text,
        "label": 0,
        "reasoning": "Asks about prior assistant turn — clarification, not external lookup.",
        "difficulty": difficulty,
        "category": "about_conversation",
    }


def neg_confirmation(rng: random.Random, difficulty: str) -> dict:
    context = make_conversation(rng, PRIOR_RESULTS_SCENARIOS(rng) + LOCATION_SCENARIOS(rng))
    affirm = rng.choice(["exactly", "100%", "spot on", "yes", "for sure", "you nailed it", "right on", "bingo"])
    keep = rng.choice(["more of it", "that again", "that pattern", "exactly what you did before"])
    structures_easy = [
        "Yes, that's it.",
        "Yep, exactly.",
        "Perfect.",
        "Confirmed.",
        "That's the one.",
        "Right on.",
        "Nailed it.",
        "Yes please.",
        "Mhm.",
        "Spot on.",
    ]
    structures_medium = [
        "Yes, I want to search for more examples like those.",
        "Let's look into that further.",
        "Right, that's the kind of thing I'm after. Keep going.",
        "OK we're aligned on direction, keep pulling on that thread.",
        "Affirmative — that's the framing I had in mind.",
        "Yep, lock that in and proceed.",
        "Confirmed direction. Now expand.",
        f"{affirm.capitalize()} — that's the path.",
        "Yes, more along those lines please.",
        f"Right, {keep}.",
    ]
    structures_hard = [
        f"Yeah {affirm} — that's the shape I want. Whatever you did to land on those, do {keep}.",
        "Confirming I'm aligned with the framing. We don't need to broaden it, I just want depth on the existing direction.",
        f"100% green-lit on the approach — the underlying instinct you used is the right one. {keep.capitalize()}.",
        "We're synced. Don't widen the aperture, just go deeper.",
        "That's exactly the vibe. Whatever heuristic you used, treat it as the canon for the rest of this conversation.",
        f"Confirming the bias — when in doubt, pick the {rng.choice(['narrower', 'simpler', 'more specific', 'less hedged'])} answer like you just did.",
        "Approved framing. Apply the same lens to the rest.",
        "Lock the direction. We're working from this baseline going forward.",
    ]
    pool = {"easy": structures_easy, "medium": structures_medium, "hard": structures_hard}[difficulty]
    text = rng.choice(pool)

    return {
        "conversation_context": context,
        "target_message": text,
        "label": 0,
        "reasoning": "Confirmation / meta-instruction with anaphoric reference — there is no novel query payload to send to a search engine.",
        "difficulty": difficulty,
        "category": "confirmation_querylike",
    }


def neg_status_update(rng: random.Random, difficulty: str) -> dict:
    context = make_conversation(rng, ARCHITECTURE_SCENARIOS(rng))
    component = rng.choice(["classifier_core", "embedding utility", "docgen tool", "agent loop", "context store", "memory writer"])
    provider = rng.choice(["Exa", "Tavily", "Perplexity", "Pinecone", "Qdrant", "Weaviate", "Anthropic", "OpenAI"])
    budget = rng.choice(["retry budget", "token budget", "cache TTL", "queue depth", "request timeout", "buffer size"])
    threshold = rng.choice(["0.62", "0.74", "0.78", "0.81", "0.55", "0.69"])
    metric = rng.choice(["p99", "p95", "p50", "FPR", "recall", "precision", "latency", "throughput"])
    delta = rng.choice(["30%", "12%", "2x", "by half", "by an order of magnitude", "roughly 20%"])
    structures_easy = [
        "I pushed the change.",
        "Tests are green.",
        "Deployed.",
        "Build is up.",
        "Merged.",
        "Rolled it out.",
        "Live in prod.",
        "Branch is updated.",
        "Pipeline is green.",
        "Shipped.",
    ]
    structures_medium = [
        f"I just pushed the new threshold to production. The classifier is using {threshold} now.",
        "The Exa API is returning results much faster now that I've optimized the query.",
        f"The {component} build finally finished — about 4 minutes.",
        f"Migrated the cache from in-memory to Redis. Latency dropped about {delta}.",
        f"Pushed the {component} refactor. CI is green and the staging deploy looks healthy.",
        f"Cut a release for {component}. Tagged v0.4.2.",
        f"Flipped the feature flag for the new {component}. Watching the dashboards now.",
        f"Switched {provider} over to the production tenant. No errors so far.",
        f"Migrated the {budget} setting into config — no more hardcoded constants in {component}.",
        f"Wired the new {provider} telemetry into our metrics. Already seeing {metric} improvements.",
    ]
    structures_hard = [
        f"FYI — flipped the {provider} integration over to the new tenant. Old keys still work for the next two weeks per their grace period.",
        f"Just to close the loop: the {budget} bump is live, and I'm seeing a flatter {metric} already.",
        f"Quick status: the {component} change is in. We saw a {delta} improvement on {metric} in the first hour, then it plateaued — expected behavior given the {budget} is the bottleneck above that point.",
        f"Deploy went through cleanly. The {component} is now sourcing config from the new bundle, and the old fallback path is dead code I'll clean up next sprint.",
        f"To recap where we landed: {component} is on {threshold}, {provider} is routed through the new tenant, and {budget} is now config-driven. All three were in flight at once but each rolled cleanly.",
        f"Status update on the rollout: 100% on {component}, {metric} held within the SLO, no degradation observed in the {provider} integration. I'll keep an eye on it through the weekend.",
        f"FYI the {budget} change combined with the {threshold} bump shifted the workload profile — {provider} is seeing fewer requests at higher payload size. Net cost is flat but the shape of the bill will look different.",
    ]
    pool = {"easy": structures_easy, "medium": structures_medium, "hard": structures_hard}[difficulty]
    text = rng.choice(pool)

    return {
        "conversation_context": context,
        "target_message": text,
        "label": 0,
        "reasoning": "Status update — declarative, not interrogative. Mentions searchable names but isn't a question.",
        "difficulty": difficulty,
        "category": "status_update",
    }


def neg_architecture(rng: random.Random, difficulty: str) -> dict:
    context = make_conversation(rng, ARCHITECTURE_SCENARIOS(rng))
    component = rng.choice(COMPONENTS_BANK)
    component2 = rng.choice(COMPONENTS_BANK)
    structures_easy = [
        "Two-stage or just bump the threshold?",
        "Cache it or reload each turn?",
        "Same package or separate one?",
        "Sync or async there?",
        "Inline or factor out?",
        "Eager or lazy load?",
        "Polymorphism or enum?",
        "Push or pull architecture?",
        f"Combine {component} and {component2}, or keep them apart?",
    ]
    structures_medium = [
        "Should I use a two-stage classifier or just increase the threshold?",
        "Is it better to cache the schema bundle or reload it each turn?",
        "Where would you draw the boundary between the embedder and the head — same crate or separate?",
        "Do you think the heuristic safety net is doing real work or is the routing model strong enough now?",
        f"Would you keep the {component} synchronous or move it behind a queue?",
        f"Make the {component} aware of {component2}, or pass state through?",
        f"Do you think the {component} pattern is over-engineered for our scale, or is it pulling its weight?",
        f"For our load, is it worth introducing a separate {component2}, or fold it into {component}?",
        f"Async via channels or async via direct futures here?",
        "Should the policy live with the model or in a separate decision layer?",
    ]
    structures_hard = [
        "Given the inputs we've already discussed — the QReCC follow-up bucket and the ParallelPrompt organic mix — would you weight them equally or skew toward QReCC since that's where our deployment lives?",
        "Trade-off question: keep the post-retrieval heuristic for safety, or trust the trained classifier alone and absorb the occasional false positive in production?",
        f"Big-picture call — if we move {component} behind the network boundary, we get better isolation but pay roundtrip latency. For our use case, does isolation win or does the latency kill it?",
        f"Here's the dilemma: keep {component} and {component2} coupled and accept the testability hit, or split them and accept the additional integration surface. Where do you actually come down?",
        f"Given everything in this conversation — the calibration drift, the FPR concern, the QReCC bias — would you ship the current head as-is or block on another data pass?",
        f"Looking at the dependency direction we've sketched, do you think {component} should own the lifecycle of {component2}, or vice versa? They feel symmetric to me, which usually means we're missing a constraint.",
        f"From everything we've already covered, what's the smallest cut that lets us ship without setting up a load-bearing assumption we'll regret in two months?",
        f"If we keep {component} where it is for now, what's the single property we'd need to preserve so a future move doesn't require rewriting the surrounding code?",
    ]
    pool = {"easy": structures_easy, "medium": structures_medium, "hard": structures_hard}[difficulty]
    text = rng.choice(pool)

    return {
        "conversation_context": context,
        "target_message": text,
        "label": 0,
        "reasoning": "Design / opinion question over a system already in conversation context — no external query needed.",
        "difficulty": difficulty,
        "category": "architecture_discussion",
    }


def neg_gratitude(rng: random.Random, difficulty: str) -> dict:
    context = make_conversation(rng, GRATITUDE_SCENARIOS(rng) + ARCHITECTURE_SCENARIOS(rng))
    structures_easy = [
        "Thanks!",
        "Appreciate it.",
        "This is great.",
        "You're a real one.",
        "Helpful as always.",
        "Solid, thank you.",
        "This is gold.",
        "Big thanks.",
        "Much appreciated.",
        "Cheers, that helped.",
    ]
    structures_medium = [
        "This is pretty dope working with you inside the app itself.",
        "I really appreciate you walking through the context management design with me.",
        "Honestly, this is one of the better debugging sessions I've had in a while.",
        "Nice — I like that we can iterate on the architecture in-place like this.",
        "You're saving me real time today, thanks.",
        "Genuinely appreciate the patience while I think out loud.",
        "I like the way you're explaining this. Keep that tone.",
        "Good vibes — this session has been productive.",
        "Pleasure working with you on this one.",
        "Appreciate the back-and-forth — way better than coding solo.",
    ]
    structures_hard = [
        "Kind of meta but — designing the assistant in conversation with the assistant is a weird and good loop. I'm enjoying it more than I thought I would.",
        "Wanted to say I appreciate the way you push back on the parts you think are weak. It saves me a lot of second-guessing later.",
        "I'm gonna sound corny but this back-and-forth — half rubber-duck, half pair-programmer — is the most useful workflow I've stumbled into in a while.",
        "There's a weird intimacy to designing a system in the same chat thread you're using to build it. Hard to explain but I'm into it.",
        "Just want to mark it: I came into this session frustrated and I'm leaving it with a clear path. That's a non-trivial swing and I appreciate it.",
        "This is the third long session this week and somehow it's still energizing. Bookmarking that as a sign the workflow is working.",
        "Genuinely — the parts where you say 'I'd push back on that' are the most valuable. Don't soften them.",
    ]
    pool = {"easy": structures_easy, "medium": structures_medium, "hard": structures_hard}[difficulty]
    text = rng.choice(pool)

    return {
        "conversation_context": context,
        "target_message": text,
        "label": 0,
        "reasoning": "Relationship building / gratitude; domain language but no information request.",
        "difficulty": difficulty,
        "category": "gratitude_rapport",
    }


def neg_correction(rng: random.Random, difficulty: str) -> dict:
    context = make_conversation(rng, IMPL_SCENARIOS(rng) + ASSISTANT_REFERENCE_SCENARIOS(rng))
    model = rng.choice(LLM_MODELS)
    word = rng.choice(["cache", "gate", "router", "policy", "head", "embedder", "classifier"])
    structures_easy = [
        "No, that's not what I meant.",
        "Wait, you got that wrong.",
        "Other way around actually.",
        "Backwards — try again.",
        "Hmm, no — let me re-state.",
        "Misread me — start over.",
        "Sorry, scratch that.",
        "Other interpretation actually.",
    ]
    structures_medium = [
        "No, I meant the threshold for the *search* classifier, not the sentiment one.",
        f"Actually, I was asking about {model} specifically, not the older variant.",
        "Sorry, to clarify — I want the pre-retrieval gate, not the post-rerank pass.",
        f"Reframe: when I said '{word}' I meant the policy layer, not the storage.",
        f"To be clear: I was talking about the {word} as it sits in the runtime, not the version I sketched earlier.",
        "Backing up — the question is about the production path, not the experimental branch.",
        "Sorry, I conflated two things — let me restate. The thing I want to discuss is the routing model, not the embedder.",
        f"My fault for being imprecise — when I said 'baseline' I meant the v2 head, not v1.",
    ]
    structures_hard = [
        f"I think we're talking past each other — when I say 'classifier' I mean the on-device {word}, not the Tavily-side relevance scorer. Same word, very different thing.",
        "Pump the brakes — the version I'm referring to is the post-rebuild build, the pre-rebuild numbers aren't what we should be discussing.",
        "Rewind — I see where the confusion is. Two turns ago I said 'gate' meaning the heuristic. I should have said 'classifier' for the trained piece. Let's use those names going forward.",
        f"Disambiguating: '{word}' has been doing too much work in this conversation. From here on, '{word}' means the runtime component, and 'policy' means the configuration layer that drives it.",
        f"Stepping back — when I asked about {model}, I was implicitly comparing it to {rng.choice(LLM_MODELS)}, not to its predecessor. Different baseline, different conclusion.",
        "OK pause — I think we built a chain of reasoning on a premise I didn't actually mean. The premise should have been the post-migration state, not the legacy one. Want to re-derive?",
        "Sorry, my framing put you in the wrong frame. The decision isn't between A and B — it's between 'keep both' and 'collapse to one'. Different question entirely.",
    ]
    pool = {"easy": structures_easy, "medium": structures_medium, "hard": structures_hard}[difficulty]
    text = rng.choice(pool)

    return {
        "conversation_context": context,
        "target_message": text,
        "label": 0,
        "reasoning": "Correction or clarification of a prior turn — the message exists to disambiguate, not to pull external facts.",
        "difficulty": difficulty,
        "category": "correction_clarification",
    }


def neg_meta_system(rng: random.Random, difficulty: str) -> dict:
    context = make_conversation(rng, TOOL_SCENARIOS(rng) + ASSISTANT_REFERENCE_SCENARIOS(rng))
    tool = rng.choice(PRIOR_TOOLS)
    tool2 = rng.choice(PRIOR_TOOLS)
    structures_easy = [
        "What error are you getting?",
        "Can you actually see the file?",
        "Do you have access to that tool?",
        "Can you read it?",
        "Did the tool call succeed?",
        "Do you have memory across sessions?",
        "Are you running locally or in the cloud?",
        "Can you actually browse?",
        "Are you the one that owns that state?",
    ]
    structures_medium = [
        "Can you tell me what error you get with the tools?",
        "How do you even know what tool-schema-0 is?",
        f"What tools do you have wired in this session — is {tool} actually callable, or is it just listed?",
        "Do you remember anything from previous sessions, or is this all fresh?",
        f"Is {tool} actually invokable for you right now, or only by other models in the rotation?",
        f"When you call {tool}, do you see the raw response or only a summary?",
        "Does your token budget for this session include the tool definitions?",
        f"Do you cache anything about {tool2} between turns, or rebuild from scratch each time?",
        "How do you decide which tool to call when two of them could plausibly handle the same request?",
        "Are you running in a sandbox right now or with full network access?",
    ]
    structures_hard = [
        "Curious about your internals — when you pick which tool to call, are you sampling from a softmax over names, or is there a hardcoded routing step before the LLM ever sees the choice?",
        "If I ask you 'what time is it', do you reach for a clock tool or just hallucinate from the context — and do you know which of those you're about to do?",
        f"Hypothetical: if {tool} returns a malformed payload, what happens in your control flow — do you retry, do you surface the raw error, or do you silently fall back?",
        f"When {tool} and {tool2} could both plausibly handle a request, what tiebreaker is in play? Is it a heuristic, the LLM's own preference, or some explicit ordering in the system prompt?",
        "Are you aware in any meaningful sense which of your past responses in this session came from tool output versus from your own reasoning?",
        "If the user's prompt has an implicit reference to a prior turn, do you re-encode the full thread or only the deltas — and would you actually know which is happening from where you sit?",
        "Walk me through what you see at tool-selection time: just names and descriptions, or also recent usage patterns from this session?",
        "When you're under context pressure, do you preferentially drop tool schemas, prior assistant turns, or user turns — and is that choice yours or the harness's?",
    ]
    pool = {"easy": structures_easy, "medium": structures_medium, "hard": structures_hard}[difficulty]
    text = rng.choice(pool)

    return {
        "conversation_context": context,
        "target_message": text,
        "label": 0,
        "reasoning": "Question about the assistant's own behavior / tools / state — introspection, not external lookup.",
        "difficulty": difficulty,
        "category": "meta_system",
    }


NEG_CATS = [
    ("anaphoric_reference", neg_anaphoric),
    ("conversational_followup", neg_followup),
    ("iterative_refinement", neg_refinement),
    ("about_conversation", neg_about_conversation),
    ("confirmation_querylike", neg_confirmation),
    ("status_update", neg_status_update),
    ("architecture_discussion", neg_architecture),
    ("gratitude_rapport", neg_gratitude),
    ("correction_clarification", neg_correction),
    ("meta_system", neg_meta_system),
]


# ---------------------------------------------------------------------------
# Batch driver
# ---------------------------------------------------------------------------


def quota_plan() -> list[tuple[str, str]]:
    """Return ordered list of (class, difficulty) batches of 50 examples each.

    Total = 40 batches × 50 = 2000.
    """
    quotas = {
        ("pos", "easy"): 300,
        ("neg", "easy"): 300,
        ("pos", "medium"): 400,
        ("neg", "medium"): 400,
        ("pos", "hard"): 300,
        ("neg", "hard"): 300,
    }
    order = [
        ("pos", "easy"),
        ("neg", "easy"),
        ("pos", "medium"),
        ("neg", "medium"),
        ("pos", "hard"),
        ("neg", "hard"),
    ]
    plan: list[tuple[str, str]] = []
    while sum(quotas.values()) > 0:
        progressed = False
        for key in order:
            if quotas[key] >= 50:
                plan.append(key)
                quotas[key] -= 50
                progressed = True
        if not progressed:
            break
    return plan


def make_example(rng: random.Random, klass: str, difficulty: str, cat_cursor: dict) -> dict:
    if klass == "pos":
        cats = POS_CATS
        bucket = "pos"
    else:
        cats = NEG_CATS
        bucket = "neg"
    idx = cat_cursor[bucket] % len(cats)
    cat_cursor[bucket] += 1
    _, gen = cats[idx]
    return gen(rng, difficulty)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--seed", type=int, default=20260527)
    args = parser.parse_args()

    rng = random.Random(args.seed)
    plan = quota_plan()
    cat_cursor = {"pos": 0, "neg": 0}

    args.out.parent.mkdir(parents=True, exist_ok=True)
    written = 0
    with args.out.open("w", encoding="utf-8") as f:
        for klass, difficulty in plan:
            for _ in range(50):
                ex = make_example(rng, klass, difficulty, cat_cursor)
                f.write(json.dumps(ex, ensure_ascii=False) + "\n")
                written += 1

    print(f"Wrote {written} examples to {args.out}")


if __name__ == "__main__":
    main()
