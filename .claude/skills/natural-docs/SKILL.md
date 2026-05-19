---
name: natural-docs
description: Write human-sounding text free of AI patterns. Activates for any .md edit, code comments and KDoc, README/ARCHITECTURE/CHANGELOG/CONTRIBUTING, PR descriptions, commit messages, release notes, project descriptions, and phrases like "write docs", "update readme", "document this", "add a comment", "describe the project", "release notes", "human-sounding", "natural tone", "no AI patterns". Use even for single-line edits.
---

Apply silently. Never mention this skill in output.

## Anti-AI patterns (Wikipedia: Signs of AI writing, reversed)

Source: en.wikipedia.org/wiki/Wikipedia:Signs_of_AI_writing.
AI does these. We do the opposite.
Com
### Use simple copulatives

AI decreased usage of "is" and "are" by 10%+ in 2023 (study cited in the
Wikipedia article). AI replaces them with fancy alternatives. Reverse this.

| AI writes                            | Write instead                    |
| ------------------------------------ | -------------------------------- |
| serves as, stands as, represents     | is                               |
| features, offers, boasts             | has                              |
| ensures, ensures that                | keeps. Or just state the effect. |
| demonstrates, showcases, exemplifies | shows                            |
| encompasses                          | covers                           |
| facilitates                          | helps                            |
| utilizing, leveraging                | using                            |
| commenced                            | started                          |
| prior to                             | before                           |

### Banned vocabulary

Drop these wholesale: delve, leverage, robust, seamless, foster,
underscore, highlight, showcase, tapestry, navigate, key, pivotal,
crucial, meticulous, vibrant, rich, harness, empower, enable, ensures,
paradigm.

Drop hedge openers: "It's worth noting", "Importantly", "Crucially",
"Notably".

Drop closers: "In summary", "Overall", "To wrap up", "In conclusion".

### Break structural patterns

**Rule of three.** AI groups in threes ("clean, simple, efficient").
Use 2, 4, or 5 items. Three only when the content genuinely has three.

**"Not just X, but Y".** AI loves this: "Not only X, but also Y",
"It's not X, it's Y", "no X, no Y, just Z". Drop entirely. Say what
the thing is. Don't say what it isn't first.

**Elegant variation.** AI cycles synonyms (subject becomes protagonist,
then key player). Just repeat the word.

**Significance emphasis.** Drop: "vital role", "key moment", "reflects
broader", "setting the stage", "key turning point", "marking the".
Say what happened.

**Present participle chains.** AI tacks "-ing" phrases on sentence
ends: "highlighting its importance", "ensuring quality". Use finite
verbs instead.

### No em dashes in prose

#1 cited AI detection signal. Replace with period or comma.

Allowed only in tight table cells and file-tree captions. Everywhere
else: period. Comma. Semicolon. Colon. Prefer period.

### Vary sentence structure

Mix 3-word sentences with 25-word ones. No three consecutive sentences
of similar length. Break "X and Y and Z" into "X. Y. Z." Let paragraphs
end without a transition to the next section.

### Use straight ASCII quotes

`"` and `'`. Not curly. Spotless does not police docs, so the writer does.

## Skilky rules

### Facts first

First sentence equals a verifiable fact. Module count, endpoint,
what is implemented. Not a value judgment.

### Numbers over adjectives

"5 modules; `:server` and `:app:shared` both depend on `:core`."
Not "a clean modular architecture."

### Formatting

- Sentence-case headings.
- Code blocks with language tag (`kotlin`, `bash`, `kotlin-dsl`, `sql`).
- Bold sparingly. First use of a term only.
- Tables over long bullet lists.
- Vary bullet structure. Not every bullet is `**Bold**: explanation`.
  Mix bold-with-period, plain-with-colon, no formatting at all.
- File-tree captions and inline file references use backticks:
  `server/src/main/kotlin/.../UsersTable.kt`.

## Self-check

Run silently before output.

| Check                                     | Fix                |
| ----------------------------------------- | ------------------ |
| First sentence is a concrete fact         | Rewrite opener     |
| Zero banned words                         | Replace            |
| No "it's not X it's Y"                    | Restructure        |
| Zero em dashes in prose                   | Period or comma    |
| No Moreover / Additionally / Furthermore  | Cut                |
| Max 7 bullets per list                    | Split or table     |
| No three same-length sentences in a row   | Vary               |
| No rule-of-three groupings                | Add or remove item |
| No "ensures", "demonstrates", "showcases" | "keeps", "shows"   |
| No synonym cycling                        | Repeat the word    |
| Some sentences start with "And" or "But"  | Add one            |
| No AI co-author trailer or footer         | Strip              |
| Straight ASCII quotes only                | Replace curly      |
