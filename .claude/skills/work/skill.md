# Skill: Sequential Issue Processing

Process open GitHub issues from simplest to hardest in a continuous loop.

## Workflow

### 1. Gather and prioritize
```bash
gh issue list --state open --json number,title,labels --limit 100
```
Sort issues by estimated complexity (simplest first):
- Small bugs / single-file fixes
- Multi-file bugs
- New features / enhancements
- Architecture / refactoring tasks

### 2. For each issue (simplest to hardest):

#### a. Plan
- Read the issue: `gh issue view <N>`
- Explore the codebase to understand the scope
- Enter plan mode if non-trivial (`EnterPlanMode`)
- Get user approval on the plan

#### b. Implement
- Create a feature branch and implement the fix/feature
- Run `./mvnw spring-javaformat:apply` after code changes
- Run `./mvnw validate` to check PMD/checkstyle
- Run relevant tests: `./mvnw test -Dtest=<TestClass> -pl <module>`
- Fix any failures before proceeding

#### c. Push and merge
- Use `/push` skill to create/update the PR (links to the issue)
- Wait for CI checks: `gh pr checks <N> --watch`
- If checks pass, merge: `gh pr merge <N> --squash --delete-branch`
- If checks fail, fix and push again

#### d. Next issue
- After merge, immediately pick the next simplest open issue
- Repeat from step 2a

### 3. Stop when
- All open issues are resolved, OR
- The user interrupts

## Notes
- Always apply labels to issues/PRs per project conventions
- Post status comments on issues via `/issue-track` skill
- Check for merge conflicts after PR creation
- Run full test suite before final push: `./mvnw test`
