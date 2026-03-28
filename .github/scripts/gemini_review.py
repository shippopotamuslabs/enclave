import os
import sys
import json
import requests

# Environment variables injected by GitHub Actions
VERTEX_API_KEY = os.getenv("VERTEX_API_KEY")
GCP_PROJECT_ID = os.getenv("GCP_PROJECT_ID")
GCP_REGION = os.getenv("GCP_REGION", "us-central1")
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
    if not GCP_PROJECT_ID:
        print("Error: GCP_PROJECT_ID is not set. Required for Vertex AI API.")
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

    # Exactly for Vertex AI with API Key
    endpoint = f"https://{GCP_REGION}-aiplatform.googleapis.com/v1/projects/{GCP_PROJECT_ID}/locations/{GCP_REGION}/publishers/google/models/{MODEL_ID}:streamGenerateContent?key={VERTEX_API_KEY}"
    
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
            "max_output_tokens": 8192,
            "temperature": 0.0
        }
    }

    try:
        response = requests.post(endpoint, json=payload, stream=True)
        if response.status_code != 200:
            print(f"Error calling Vertex AI: {response.status_code} - {response.text}")
            sys.exit(1)

        full_thinking = ""
        full_text = ""
        
        # Vertex AI streamGenerateContent returns a JSON array of response objects.
        # Format: [ { "candidates": [...] }, { "candidates": [...] } ]
        # We need to parse this carefully as it's not standard newline-delimited JSON.
        
        buffer = ""
        for chunk_bytes in response.iter_content(chunk_size=None):
            if chunk_bytes:
                buffer += chunk_bytes.decode("utf-8")
                
                # Try to extract JSON objects from the buffer
                # Vertex AI format is usually:
                # [
                #   {...},
                #   {...}
                # ]
                # We can strip the brackets and leading/trailing whitespace and commas.
                
                temp_buffer = buffer.strip()
                if temp_buffer.startswith("["):
                    temp_buffer = temp_buffer[1:].strip()
                if temp_buffer.endswith("]"):
                    temp_buffer = temp_buffer[:-1].strip()
                
                # Split by "}," and add the brace back
                # This is a bit hacky but works for the standard Vertex response format
                # A more robust way would be a character-by-character JSON boundary detector.
                # Let's try to parse the whole thing at the end if it's not too large.
        
        # Final parse of the accumulated buffer
        try:
            raw_content = buffer.strip()
            # Handle the case where the stream is a JSON array
            if raw_content.startswith("[") and raw_content.endswith("]"):
                data = json.loads(raw_content)
                for chunk in data:
                    if "candidates" in chunk:
                        for candidate in chunk["candidates"]:
                            if "content" in candidate:
                                for part in candidate["content"]["parts"]:
                                    if "thought" in part:
                                        full_thinking += part["thought"]
                                    if "text" in part:
                                        full_text += part["text"]
            else:
                # Fallback to line-by-line if it's not an array
                for line in raw_content.splitlines():
                    line = line.strip()
                    if line:
                        # Strip commas at start or end
                        if line.startswith(","): line = line[1:].strip()
                        if line.endswith(","): line = line[:-1].strip()
                        try:
                            chunk = json.loads(line)
                            if "candidates" in chunk:
                                for candidate in chunk["candidates"]:
                                    if "content" in candidate:
                                        for part in candidate["content"]["parts"]:
                                            if "thought" in part:
                                                full_thinking += part["thought"]
                                            if "text" in part:
                                                full_text += part["text"]
                        except:
                            pass
        except Exception as e:
            print(f"Warning: Error parsing full buffer: {e}")

        # If thinking is empty, check if it was put in text with <thought> tags
        if not full_thinking:
            import re
            thought_match = re.search(r"<thought>(.*?)</thought>", full_text, re.DOTALL | re.IGNORECASE)
            if thought_match:
                full_thinking = thought_match.group(1).strip()
                full_text = full_text.replace(thought_match.group(0), "").strip()

        # Format final output
        final_output = f"### 🤖 Gemini Architect Review\n"
        
        if full_thinking:
            final_output += f"""
<details>
<summary>🧠 View Gemini Reasoning Process</summary>

````text
{full_thinking.strip()}
````
</details>

---
"""
        
        final_output += f"\n{full_text.strip()}"
        
        post_github_comment(final_output)
        
    except Exception as e:
        print(f"Error calling Gemini/Vertex API: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
