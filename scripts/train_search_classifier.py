#!/usr/bin/env python3
import os
import sys
import subprocess
import shutil

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
VENV_DIR = os.path.join(SCRIPT_DIR, ".venv_classifier")
RUST_SRC_DIR = os.path.join(PROJECT_ROOT, "third_party", "classifier_core", "src")
RUST_FILE_PATH = os.path.join(RUST_SRC_DIR, "lib.rs")

def run_command(args, cwd=None):
    print(f"Running: {' '.join(args)}")
    result = subprocess.run(args, cwd=cwd)
    if result.returncode != 0:
        print(f"Command failed with code {result.returncode}")
        sys.exit(result.returncode)

def setup_venv():
    if not os.path.exists(VENV_DIR):
        print("Creating Python virtual environment...")
        run_command([sys.executable, "-m", "venv", VENV_DIR])
    
    pip_path = os.path.join(VENV_DIR, "bin", "pip")
    print("Installing required packages...")
    run_command([pip_path, "install", "--upgrade", "pip"])
    run_command([pip_path, "install", "sentence-transformers", "scikit-learn", "datasets", "tqdm", "numpy"])

def train_and_generate():
    python_exec = os.path.join(VENV_DIR, "bin", "python")
    
    temp_script = os.path.join(SCRIPT_DIR, "temp_train.py")
    
    with open(temp_script, "w") as f:
        f.write(r'''
import os
import sys
import random
import numpy as np
from datasets import load_dataset
from sentence_transformers import SentenceTransformer
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report
from tqdm import tqdm

random.seed(42)
np.random.seed(42)

print("Loading sentence-transformers model: all-MiniLM-L6-v2...")
model = SentenceTransformer("all-MiniLM-L6-v2")

# ============================================================
# SEARCH CLASS (1): Verbose, natural-language questions that
# genuinely need web search to answer properly.
# ============================================================
search_queries = []

# --- Source 1: Google Natural Questions (real user Google queries) ---
# These are actual questions people typed into Google. They are verbose,
# natural-language, and clearly require information retrieval.
print("Loading Google Natural Questions...")
try:
    nq = load_dataset("google-research-datasets/nq_open", split="train", streaming=True)
    for item in tqdm(nq, desc="Streaming NQ"):
        q = item.get("question", "")
        if q and len(q) > 20:  # Filter out very short fragments
            search_queries.append(q)
        if len(search_queries) >= 4000:
            break
    print(f"  Loaded {len(search_queries)} Natural Questions.")
except Exception as e:
    print(f"  Warning: Could not load NQ: {e}")

# --- Source 2: ELI5 (Explain Like I'm 5 - Reddit) ---
# Long-form, verbose questions from Reddit that clearly need research.
print("Loading ELI5 (Explain Like I'm 5)...")
try:
    eli5 = load_dataset("eli5_category", split="train", streaming=True)
    eli5_count = 0
    for item in tqdm(eli5, desc="Streaming ELI5"):
        title = item.get("title", "")
        if title and len(title) > 25:
            search_queries.append(title)
            eli5_count += 1
        if eli5_count >= 3000:
            break
    print(f"  Loaded {eli5_count} ELI5 questions.")
except Exception as e:
    print(f"  Warning: Could not load ELI5: {e}")

# --- Source 3: Hand-crafted verbose search queries ---
# These cover the style gap: long, conversational prompts that
# clearly require up-to-date web information.
handcrafted_search = [
    "What are some of the more interesting and future proof sectors for software engineers to work in?",
    "Can you tell me what the best remote jobs are for someone with a computer science degree and about 10 years of experience?",
    "What companies in Columbus Ohio are hiring for cybersecurity positions right now?",
    "I'm looking for information about the latest developments in quantum computing research, what's happening in 2024?",
    "What are the current mortgage rates in the United States and how have they changed over the past year?",
    "Tell me about the best restaurants in downtown Portland Oregon that serve Vietnamese food",
    "What programming languages are most in demand for machine learning jobs in 2024?",
    "I want to know about recent clinical trials for treatment of type 2 diabetes using GLP-1 drugs",
    "What happened at the latest Apple developer conference and what were the major announcements?",
    "Can you find me some good hiking trails near Asheville North Carolina that are dog friendly?",
    "What is the current state of the housing market in Austin Texas and is it a good time to buy?",
    "I need to know what the best health insurance options are for self-employed people in Ohio",
    "What are the pros and cons of the new MacBook Pro versus the Dell XPS for software development?",
    "Tell me about the ongoing situation with AI regulation in the European Union",
    "What stocks have performed the best in the healthcare sector over the past 6 months?",
    "I'm curious about what electric vehicles have the longest range and which ones are the most affordable",
    "What are the requirements for getting a real estate license in the state of California?",
    "Can you look up what the weather forecast is for next week in Denver Colorado?",
    "What are people saying about the new Samsung Galaxy phone compared to the latest iPhone?",
    "I want to understand the differences between different types of solar panels for residential use",
    "What universities offer the best online master's programs in data science?",
    "Tell me about recent breakthroughs in nuclear fusion energy research",
    "What are the visa requirements for a US citizen wanting to work remotely from Portugal?",
    "How much does it cost to build a house in the midwest United States in 2024?",
    "What are the best project management tools for small teams and how do they compare?",
    "I need information about the side effects and efficacy of the latest COVID booster vaccines",
    "What happened in the most recent Supreme Court rulings about technology and privacy?",
    "Can you find out what conferences are coming up for Android developers in the next few months?",
    "What are the current interest rates for small business loans through the SBA?",
    "Tell me about the best noise cancelling headphones currently available under 300 dollars",
    "What is the job market like for DevOps engineers in major US cities right now?",
    "I want to know about the environmental impact of lithium mining for electric vehicle batteries",
    "What new features were added in the latest version of Kotlin and how do they affect Android development?",
    "Can you research what the average salary is for a senior software engineer in the Columbus Ohio metro area?",
    "What are some good resources for learning about Rust programming for someone who knows C++?",
    "I need to find out about the latest OSHA regulations affecting warehouse workers",
    "What were the results of the latest clinical studies on intermittent fasting for longevity?",
    "Tell me about recent changes to 401k contribution limits and catch-up provisions",
    "What cybersecurity certifications are most valued by employers in 2024?",
    "I'm trying to figure out which cloud provider is cheapest for running GPU workloads for machine learning",
]
search_queries.extend(handcrafted_search)

print(f"Total search queries: {len(search_queries)}")

# ============================================================
# NON-SEARCH CLASS (0): Conversational, instructions, chitchat,
# and prompts that do NOT need web search.
# ============================================================
no_search_queries = []

# --- Source 1: CLINC150 (conversational assistant queries) ---
print("Loading conversational queries from CLINC150...")
try:
    clinc = load_dataset("DeepPavlov/clinc150", split="train")
    clinc_items = [x["utterance"] for x in clinc if x.get("utterance")]
    no_search_queries.extend(clinc_items[:5000])
    print(f"  Loaded {min(len(clinc_items), 5000)} CLINC150 queries.")
except Exception as e:
    print(f"  Warning: Could not load CLINC150: {e}")

# --- Source 2: Hand-crafted conversational / non-search prompts ---
# These are the kinds of things users say in chat that should NOT trigger search.
handcrafted_no_search = [
    "Can you help me refactor this function to be more readable?",
    "Write me a poem about the sunset over the ocean",
    "What do you think about my approach to solving this problem?",
    "Explain the difference between a stack and a queue",
    "Help me debug this Python script, it keeps throwing a KeyError",
    "Summarize what we talked about earlier in this conversation",
    "Can you rewrite this paragraph to sound more professional?",
    "What else is there?",
    "Tell me more about that last point",
    "That's interesting, go on",
    "Can you explain that in simpler terms?",
    "What would you recommend based on what I told you?",
    "How does that compare to what you mentioned before?",
    "OK so what should I do next?",
    "Thanks, that makes sense now",
    "Can you format that as a table?",
    "Write a unit test for the function I just showed you",
    "What company or role do you think I would excel most in, given my background?",
    "Where are they located?",
    "What sorts of jobs from your list above would you recommend?",
    "Create a Kotlin data class with these fields: name, age, email",
    "How would you implement a binary search in Java?",
    "Tell me a joke",
    "What's your opinion on using microservices vs monoliths?",
    "Can you proofread this email for me?",
    "Translate this sentence to Spanish: I would like a coffee please",
    "Set a reminder for my meeting tomorrow at 3pm",
    "What time is it?",
    "Play some relaxing music",
    "Add milk to my shopping list",
    "How are you doing today?",
    "What can you help me with?",
    "Never mind, forget what I said",
    "Go back to the previous suggestion",
    "Make it shorter",
    "Make it more formal",
    "Can you elaborate on point number 3?",
    "That's not what I meant, let me rephrase",
    "Convert this JSON to YAML",
    "Generate a random password with 16 characters",
]
no_search_queries.extend(handcrafted_no_search)

print(f"Total non-search queries: {len(no_search_queries)}")

# ============================================================
# Balance classes and prepare data
# ============================================================
min_class_size = min(len(search_queries), len(no_search_queries))
print(f"Balancing classes to {min_class_size} samples each...")
random.shuffle(search_queries)
random.shuffle(no_search_queries)
search_queries = search_queries[:min_class_size]
no_search_queries = no_search_queries[:min_class_size]

queries = no_search_queries + search_queries
labels = [0] * len(no_search_queries) + [1] * len(search_queries)

print(f"Total dataset size: {len(queries)} ({len(no_search_queries)} non-search + {len(search_queries)} search)")

# ============================================================
# Encode and train
# ============================================================
print("Encoding queries to embeddings (this may take a few minutes)...")
embeddings = model.encode(queries, show_progress_bar=True, normalize_embeddings=True)

# Train/test split for proper evaluation
X_train, X_test, y_train, y_test = train_test_split(
    embeddings, labels, test_size=0.15, random_state=42, stratify=labels
)

print(f"Training set: {len(X_train)} samples")
print(f"Test set: {len(X_test)} samples")

print("Training Logistic Regression classifier...")
clf = LogisticRegression(C=1.0, max_iter=1000, random_state=42)
clf.fit(X_train, y_train)

train_accuracy = clf.score(X_train, y_train)
test_accuracy = clf.score(X_test, y_test)
print(f"\nTraining set accuracy: {train_accuracy:.4f}")
print(f"Test set accuracy:     {test_accuracy:.4f}")

print("\n--- Test Set Classification Report ---")
y_pred = clf.predict(X_test)
print(classification_report(y_test, y_pred, target_names=["Non-Search", "Search"]))

# Quick sanity check on specific prompts
sanity_prompts = [
    "What are some of the more interesting and future proof sectors for software engineers to work in? Like a good engineer with 12 years of experience. Maybe 70th percentile. Not a super genius. Not top 1%. Just a good engineer. Has to be available in Columbus, Ohio or remote.",
    "What companies or roles for this sort of work are located in Columbus Ohio?",
    "What sorts of cyber security or healthcare jobs are located in Columbus? Other jobs, from your list above, I mean.",
    "Where are they located?",
    "Can you help me refactor this function?",
    "What is the current state of AI regulation in Europe?",
    "Tell me more about that",
    "What happened at Google IO this year?",
]
print("\n--- Sanity Check ---")
sanity_embeddings = model.encode(sanity_prompts, normalize_embeddings=True)
for prompt, emb in zip(sanity_prompts, sanity_embeddings):
    prob = clf.predict_proba([emb])[0][1]
    decision = "SEARCH" if prob >= 0.5 else "NO SEARCH"
    print(f"  [{decision}] (p={prob:.4f}) {prompt[:80]}...")

weights = clf.coef_[0]
bias = clf.intercept_[0]

# ============================================================
# Generate Rust source file
# ============================================================
rust_code = f"""// Auto-generated by train_search_classifier.py. DO NOT EDIT.
// Trained on Google Natural Questions + ELI5 (search) vs CLINC150 (conversational).
// Test accuracy: {test_accuracy:.4f}
uniffi::setup_scaffolding!();

pub const WEIGHTS: [f32; 384] = [
"""
for i, w in enumerate(weights):
    rust_code += f"    {w:.6f}f32,\n"
rust_code += f"""];

pub const BIAS: f32 = {bias:.6f}f32;

#[uniffi::export]
pub fn get_search_intent_probability(embedding: Vec<f32>) -> f32 {{
    if embedding.len() != 384 {{
        return 0.0;
    }}
    let mut score = BIAS;
    for i in 0..384 {{
        score += embedding[i] * WEIGHTS[i];
    }}
    1.0 / (1.0 + (-score).exp())
}}

#[uniffi::export]
pub fn classify_search_intent(embedding: Vec<f32>, threshold: f32) -> bool {{
    get_search_intent_probability(embedding) >= threshold
}}

#[cfg(test)]
mod tests {{
    use super::*;

    #[test]
    fn test_zero_embedding() {{
        let embedding = vec![0.0; 384];
        let prob = get_search_intent_probability(embedding);
        // Just ensure it returns a valid probability
        assert!(prob >= 0.0 && prob <= 1.0);
    }}

    #[test]
    fn test_invalid_length() {{
        let embedding = vec![0.0; 100];
        assert!(!classify_search_intent(embedding, 0.5));
    }}
}}
"""

rust_file_path = sys.argv[1]
os.makedirs(os.path.dirname(rust_file_path), exist_ok=True)
with open(rust_file_path, "w") as rf:
    rf.write(rust_code)

print(f"\nSuccessfully generated Rust classifier at {rust_file_path}")
''')
    
    try:
        run_command([python_exec, temp_script, RUST_FILE_PATH])
    finally:
        if os.path.exists(temp_script):
            os.remove(temp_script)

def cleanup():
    if os.path.exists(VENV_DIR):
        print("Cleaning up virtual environment...")
        shutil.rmtree(VENV_DIR)

def main():
    setup_venv()
    train_and_generate()
    cleanup()
    print("Training pipeline finished successfully.")

if __name__ == "__main__":
    main()
