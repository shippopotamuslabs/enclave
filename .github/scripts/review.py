import os
import sys
import requests
import anthropic

# Environment variables injected by GitHub Actions
MINIMAX_API_KEY = os.getenv("MINIMAX_API_KEY")
GITHUB_TOKEN = os.getenv("GITHUB_TOKEN")
REPO = os.getenv("REPO")
PR_NUMBER = os.getenv("PR_NUMBER")

client = anthropic.Anthropic(
    api_key=MINIMAX_API_KEY,
    base_url="https://api.minimax.io/anthropic"
)

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
    diff = get_pr_diff()
    
    if not diff.strip():
        print("Empty diff. Nothing to review.")
        sys.exit(0)

    # Updated Prompt targeting General Bugs, Clean Arch, AND Android specifics
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

    try:
        message = client.messages.create(
            model="MiniMax-M2.7-highspeed", 
            max_tokens=2000,
            system=system_prompt,
            messages=[
                {
                    "role": "user",
                    "content": f"Review this git diff and provide targeted feedback based on the system instructions:\n\n```diff\n{diff}\n```"
                }
            ]
        )
        
        # Safely extract both the thinking process and the final text review
        review_comment = ""
        thinking_process = ""
        
        for block in message.content:
            if block.type == "thinking":
                thinking_process = block.thinking
            elif block.type == "text":
                review_comment += block.text
                
        # Use a variable for markdown backticks to prevent parser breakage
        md_code = "```"
        
        # Construct the final markdown payload with the collapsible section
        final_output = f"""### 🤖 MiniMax Architect Review

<details>
<summary>🧠 View MiniMax Reasoning Process</summary>

{md_code}text
{thinking_process}
{md_code}
</details>

---

{review_comment}
"""
        
        post_github_comment(final_output)
        
    except Exception as e:
        print(f"Error calling MiniMax API: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()