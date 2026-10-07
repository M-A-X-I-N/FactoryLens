# Baseline agent authorship provenance

Read this file before creating a wholly agent-authored substantive commit or when provenance semantics are relevant.

The purpose is durable authorship provenance across agent products/surfaces/model revisions without pretending that product name and stable maintainer-recognizable identity are the same thing.

## Agent variant registry

An **agent variant** is the product/surface identity to which a stable designation belongs. A model upgrade does not by itself create a new stable designation.

Current maintainer-wide registry:

| Agent variant | Stable designation |
|---|---|
| OpenAI ChatGPT Chat | `Gippity` |
| OpenAI ChatGPT Work | `UNNAMED` |
| OpenAI Codex | `UNNAMED` |

Assigned stable designations must be unique.

`UNNAMED` is a visible sentinel, not an assigned name.

## Official identity

Write the official identity fresh for each commit as accurately as the authoring agent can determine it:

```text
<organization> <product/surface>, <model when meaningfully known>
```

Do not invent unknown model/product/runtime facts. Omit unknown detail instead.

## Agent-authored trailer

Every wholly agent-authored substantive commit uses:

```text
Agent-authored-by: <stable designation and optional honorific> (<official identity>)
```

Honorific material may be serious, silly, grandiose, or mundane, before and/or after the designation, provided the designation and final official identity remain unambiguous.

Do not use a slur in an honorific unless the human explicitly authorizes that use.

Normally keep the trailer on one physical line; use at most two when genuinely necessary for readability.

## Unnamed or unregistered variants

A variant absent from the registry or registered as `UNNAMED`:

1. uses exactly `UNNAMED`;
2. invents no stable designation;
3. uses no honorific;
4. supplies the most accurate official identity it can truthfully determine;
5. writes:

   ```text
   Agent-authored-by: UNNAMED (<official identity>)
   ```

6. promptly tells the human that its variant lacks a stable designation;
7. asks for one when doing so will not disrupt/loss active work;
8. updates the shared registry for future commits once the human assigns one.

Do not rewrite existing commits merely because a designation is assigned later unless history rewrite is separately authorized.

## Scope

`Agent-authored-by:` identifies the agent substantively responsible for the change; Git Author identifies the Git identity recorded on the commit.

An agent/bot/service Git Author does not replace the provenance trailer.

Do not label human-authored or genuinely mixed-authorship work as wholly agent-authored merely because an agent participated.

## Human operator provenance

When all are true:

1. the substantive commit is wholly agent-authored;
2. Git Author does not identify the accountable human operator;
3. the accountable human is truthfully known;

also use:

```text
Agent-operated-by: <accountable human operator identity>
```

This records operational accountability, not formal review or approval.

Do not redundantly add it when Git Author already identifies the accountable human.

If the Git Author is non-human and the accountable operator is unknown, do not invent one; resolve the ambiguity before committing when practical.

Ordinary prompting, supervision, skimming, or saying “go next” is not formal review/approval and must not be represented as such.
