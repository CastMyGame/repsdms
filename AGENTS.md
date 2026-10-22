# Pull request descriptions

When creating a PR, automatically populate `.github/pull_request_template.md` from the actual git diff, commits, and test results. Use its five sections and keep the description concise and easy to review.

- Do not invent passing tests, results, or verification. Write "Not run" for checks that were not performed.
- Write "None" when a section has no relevant information.
- Note security, environment-variable, secret, and database implications whenever present. Never include secret values.
- Preserve existing developer instructions and PR conventions. Manual template completion is optional; do not add approval gates or require developers to answer a questionnaire.
