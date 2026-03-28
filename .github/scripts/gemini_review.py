import os
import sys
import json
import requests

# Environment variables injected by GitHub Actions
VERTEX_API_KEY = os.getenv("VERTEX_API_KEY")
GITHUB_TOKEN = os.getenv("GITHUB_TOKEN")
REPO = os.getenv("REPO")
PR_NUMBER = os.getenv("PR_NUMBER")

MODEL_ID = "gemini-3.1-pro-preview"

def get_pr_diff():
    try:
        with open("pr_diff.txt", "r") as f:
            return f.read()
    except FileNotFoundError:
        print("Error: pr_diff.txt not found.")
        sys.exit(1)

def post_github_comment(comment):
    url = f"https://api.github.com/repos/{REPO}/issues/{PR_NUMBER}/comments"
    headers = {
        "Authorization": f"Bearer {GITHUB_TOKEN}",
        "Accept": "application/vnd.github.v3+json"
    }
    response = requests.post(url, headers=headers, json={"body": comment})
    if response.status_code != 201:
        print(f"Failed to post comment: {response.text}")

def main():
    if not VERTEX_API_KEY:
        print("Error: VERTEX_API_KEY is not set.")
        sys.exit(1)

    diff = get_pr_diff()
    
    if not diff.strip():
        print("Empty diff. Nothing to review.")
        sys.exit(0)

    system_prompt = """You are a Staff Android Architect reviewing a Pull Request.
Your objective is to identify general bugs, architectural flaws, performance bottlenecks, and violations of modern Android development standards.

<constraints>
- ONLY review the provided git diff.
- DO NOT summarize the changes.
- DO NOT comment on minor formatting or style issues that a linter would catch.
- FORMAT your response in clean Markdown with clear headings.
- IF NO major issues are found, respond exactly with "LGTM 🚀" and a 1-sentence explanation of why the implementation is sound.
</constraints>

<focus_areas>
1. **General Bugs & Logic Flaws:** Catch unhandled exceptions, null pointer risks, race conditions, edge cases, and logical errors.
2. **Code Quality & Clean Architecture:** Flag DRY violations, tightly coupled components, poor naming, and violations of SOLID principles. Ensure strict separation of concerns between data, domain, and UI layers.
3. **General Performance:** Identify inefficient algorithms, redundant loops, unoptimized data structures, and heavy operations blocking the main thread.
4. **Android & Compose Specifics:** Scrutinize for unnecessary recompositions, missing `remember` blocks, Coroutine memory leaks, missing `Dispatchers.IO`, and lifecycle mismatches.
5. **On-Device LLM Constraints:** Strictly flag memory hoarding and unoptimized object allocations in hot loops that could disrupt local inference.
</focus_areas>"""

    # Using the simplified AI Platform endpoint structure from your curl example
    # Adding alt=sse to ensure we get a stable stream of events including the reasoning parts
    endpoint = f"https://aiplatform.googleapis.com/v1/publishers/google/models/{MODEL_ID}:streamGenerateContent?alt=sse&key={VERTEX_API_KEY}"
    
    # Updated generation_config for Gemini 3.1 Pro Preview
    # include_thoughts must be True to enable the reasoning stream
    payload = {
        "contents": [
            {
                "role": "user",
                "parts": [
                    { "text": f"Review this git diff and provide targeted feedback based on the system instructions:\n\n```diff\n{diff}\n```" }
                ]
            }
        ],
        "system_instruction": {
            "parts": [
                { "text": system_prompt }
            ]
        },
        "generation_config": {
            "max_output_tokens": 65536,
            "temperature": 1.0,
            "thinking_config": {
                "include_thoughts": True
            }
        }
    }

    try:
        response = requests.post(endpoint, json=payload, stream=True)
        if response.status_code != 200:
            # Safely handle potential non-string error bodies
            error_msg = response.reason
            try:
                error_msg = response.text
            except:
                pass
            print(f"Error calling Vertex AI: {response.status_code} - {error_msg}")
            sys.exit(1)

        full_thinking = ""
        full_text = ""
        
        # Parse SSE (Server-Sent Events) stream
        for line in response.iter_lines():
            if line:
                decoded_line = line.decode("utf-8")
                if decoded_line.startswith("data: "):
                    data_str = decoded_line[len("data: "):]
                    try:
                        chunk = json.loads(data_str)
                        if "candidates" in chunk:
                            for candidate in chunk["candidates"]:
                                if "content" in candidate:
                                    for part in candidate["content"]["parts"]:
                                        if "thought" in part:
                                            full_thinking += str(part["thought"])
                                        if "text" in part:
                                            full_text += str(part["text"])
                    except json.JSONDecodeError:
                        pass

        # Fallback extraction if thinking was embedded in text or used other tags
        if not full_thinking:
            import re
            # Check for <thought>, <thinking>, [THOUGHT], or Reasoning: patterns
            patterns = [
                r"<thought>(.*?)</thought>",
                r"<thinking>(.*?)</thinking>",
                r"\[THOUGHT\](.*?)(?=\[|$)",
                r"^Reasoning:(.*?)(?=---|\n\n|\n#|$)"
            ]
            for pattern in patterns:
                match = re.search(pattern, full_text, re.DOTALL | re.IGNORECASE | re.MULTILINE)
                if match:
                    full_thinking = match.group(1).strip()
                    full_text = full_text.replace(match.group(0), "").strip()
                    break

        # Format final output
        # Use 4 backticks for the outer container to allow 3 backticks inside thinking
        outer_md_code = "````"
        final_output = f"### 🤖 Gemini Architect Review\n"
        
        if full_thinking:
            final_output += f"""
<details>
<summary>🧠 View Gemini Reasoning Process</summary>

{outer_md_code}text
{full_thinking.strip()}
{outer_md_code}
</details>

---
"""
        
        # Clean up any duplicate headers that might have been included by the model
        text_to_append = full_text.strip()
        if text_to_append.startswith("### 🤖 Gemini Architect Review"):
            text_to_append = text_to_append.replace("### 🤖 Gemini Architect Review", "", 1).strip()
        
        final_output += f"\n{text_to_append}"
        
        post_github_comment(final_output)
        
    except Exception as e:
        print(f"Error calling Gemini/Vertex API: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
