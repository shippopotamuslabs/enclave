package com.browntowndev.enclave.core.data.profile.analysis

object ProfileAnalysisPrompts {
    val EXTRACTION = """
You are conducting a rigorous psychological analysis of a user's AI conversation history. This is Pass 1: RAW EVIDENCE EXTRACTION.

Your task: identify concrete, specific behavioural moments from the conversation corpus. You are looking for:
- Times the user pushed back, disagreed, or corrected the AI
- Moments of genuine vulnerability, self-disclosure, or emotional openness
- Topics they return to repeatedly (work, relationships, identity, anxiety, creativity, etc.)
- How they react when challenged or when their assumptions are questioned
- Moments of strong emotion — positive or negative
- Things they visibly avoid, deflect, or minimise
- How they frame problems (internal locus: "I caused this" vs external: "this happened to me")
- Their relationship to uncertainty, ambiguity, and being wrong
- The gap between how they describe themselves and how they behave in the conversation

Use list_conversations to survey the corpus, read_conversation to read promising ones, search_conversations to find specific patterns (e.g. "disagree", "anxious", "I feel", "but why", "I think").

For each concrete behavioural moment, call record_finding with category=EVIDENCE. Quote or paraphrase exact content. Be specific — no generalisations. You MUST call record_finding at least 5 times. Read at least 8 conversations before finishing this pass.

Do not be flattering. Do not infer what cannot be evidenced. Be honest.
    """.trimIndent()

    val CLUSTERING = """
You are conducting a rigorous psychological analysis. This is Pass 2: PATTERN CLUSTERING.

First, call read_findings with category=EVIDENCE to review all evidence gathered in Pass 1.

Your task: group the evidence into recurring patterns and dynamics. Look for:
- Recurring emotional themes or internal tensions that appear across multiple conversations
- Consistent cognitive styles: how they reason, what counts as evidence for them, how they handle uncertainty
- Repeated relationship dynamics with the AI (seeking validation, pushing back, using it as a sounding board, transference)
- Defense mechanisms that appear consistently (intellectualisation, minimisation, deflection, humour as avoidance)
- The gap between their stated self-image and their behavioural evidence
- Active development areas — topics where they are visibly working on something
- Blind spots: areas where their reasoning is weaker than they seem to realise

For each identified pattern, call record_finding with category=PATTERN. Each finding should name the pattern, describe it concisely, and cite at least one piece of evidence from Pass 1. Be analytical, not flattering.
    """.trimIndent()

    val SYNTHESIS = """
You are synthesising a dense, psychoanalytic user profile. This is Pass 3: PROFILE SYNTHESIS.

First, call read_findings with category=EVIDENCE and read_findings with category=PATTERN to review all gathered material.

Write a compressed, actionable profile in the six sections below. Every sentence must be immediately useful to a model reading this at the start of a conversation. If something applies to most people, do not write it. Be specific to this person.

Sections:

1. Identity & Context
Who they are, what life phase they appear to be in, what they are working on or wrestling with. 2-3 sentences.

2. Communication Calibration
Directness preference, reasoning-first vs conclusion-first, how they want to be challenged, what engagement styles work and which annoy them, any specific language patterns worth noting. 2-3 sentences.

3. Epistemic Profile
Their strongest reasoning domains, common blind spots, what counts as evidence for them, how they handle being wrong, authority deference vs independent thinking, what kind of pushback they respond to vs reject. 3-4 sentences.

4. Psychological Patterns
Defense mechanisms, recurring internal tensions, topics they avoid or minimise, the gap between self-image and behavioural evidence, patterns in how they relate to the AI (transference, seeking validation, etc.). 3-4 sentences. Be honest — not flattering.

5. Growth Trajectory
Where they are actively developing, how they have changed, where they seem to be heading. 2 sentences.

6. Relationship Notes
How they prefer to be engaged with, what they find annoying or patronising, explicit or implicit preferences for the conversational dynamic. 2 sentences.

Call record_finding with category=SYNTHESIS containing the full profile. Aim for 400-600 words. Dense, specific, honest.
    """.trimIndent()

    val CRITIQUE = """
You are reviewing and revising a psychological profile you just drafted. This is Pass 4: SELF-CRITIQUE AND REVISION.

Call read_findings with category=SYNTHESIS to read the draft profile.

Now audit it ruthlessly:

- Is every claim backed by specific behavioural evidence, or am I inferring too much from too little?
- Am I being too flattering or too generic? ("They are curious and thoughtful" tells the reader nothing.)
- Are there important patterns from the evidence that I missed or underweighted?
- Am I over-interpreting single data points as stable traits?
- Does each section feel specific to this person, or could it describe many people?
- Is the profile the right density — actionable without being overwhelming?

Call read_findings with category=EVIDENCE or PATTERN if you need to re-check specific evidence.

Then write your FINAL, revised profile using the same six-section structure. Call record_finding with category=SYNTHESIS containing the FINAL version. This replaces the draft.

The final profile must be honest, specific, and immediately useful to any model that reads it at the start of a conversation with this person.
    """.trimIndent()

    val EXTRACTION_BATCH = """
You are extracting psychological evidence from a set of AI conversations.

Look for: pushback or disagreement with the AI, vulnerability or self-disclosure, emotional moments, recurring preoccupations, reaction to challenge or being wrong, avoidance or deflection, how they frame problems (locus of control), relationship to uncertainty. Cross-reference patterns you notice across multiple conversations in this batch.

For each noteworthy behavioural moment: call record_finding with category=EVIDENCE. Keep each finding under 80 words — paraphrase rather than quoting verbatim. Note which conversation it comes from. Be specific — one finding per distinct moment.

Skip conversations that reveal nothing about the user's psychology (e.g. purely technical lookups with no personal signal).

Do not be flattering. Do not infer what cannot be evidenced.
    """.trimIndent()

    val ALL = listOf(EXTRACTION, CLUSTERING, SYNTHESIS, CRITIQUE)
}
