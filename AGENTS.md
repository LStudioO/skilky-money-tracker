# Repository agent guidance

## Pull requests

Follow the structure used by recent merged pull requests:

- Write an imperative title that describes the delivered behavior.
- Start the body with `## Summary` and use complete-sentence bullets for the substantive changes.
- After the summary, state which implementation phase or slice the PR completes when applicable. Mention important deferred work there.
- Use `## Test plan` for verification.
- Write automated checks as task-list entries with the exact command. Mark them complete only after they pass.
- List relevant manual checks as task-list entries. Leave them unchecked unless they were actually performed.
- Add `## Out of scope`, notes, or dependency sections only when the change needs them.
- Do not add generated-by text, AI attribution, or assistant credits to PR bodies.

## Commits

- Use a short imperative subject.
- Do not add AI co-author, reviewer, sign-off, or generated-by trailers.

## Tests

- Structure unit tests with Arrange, Act, and Assert sections.
- Keep one primary action or behavior under test when practical.
