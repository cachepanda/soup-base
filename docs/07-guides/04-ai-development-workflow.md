# Soup-Base — AI Development Workflow

**Version:** 1.0<br>
**Status:** Draft<br>
**Last updated:** April 2026<br>

---

## How this project is built

One engineer. No code written by hand. AI agents implement everything from specs.
The engineer writes specs, reviews PRs, and merges.

---

## The loop

```
1. Open a GitHub issue with @claude in the body
2. Claude reads the issue + relevant specs
3. Claude opens a PR (branch: feat/{description})
4. CI runs (compile + test)
5. If CI fails: Claude reads the output, pushes a fix, repeat
6. Engineer reviews the diff
7. Engineer merges (or comments @claude to fix something)
```

---

## Writing issues for Claude

Good issues are specific and point at the spec. Claude reads AGENTS.md and the docs
automatically — you don't need to paste spec content into the issue.

**Good issue:**
```
@claude Implement task 1.1 from the implementation plan:
UserService + UserRepository + POST /api/auth/session endpoint.

Acceptance criteria are in FR-AU-002. Follow module boundaries doc.
Include integration tests per the testing strategy.
```

**Too vague:**
```
@claude add user auth
```

**Template to follow:**
```
@claude Implement [task number and name] from the implementation plan.

Spec references:
- [FR or ADR that defines the behaviour]
- [Any other relevant doc section]

Definition of done:
- [ ] [acceptance criterion 1]
- [ ] [acceptance criterion 2]
- [ ] Integration test covering the happy path and [edge case]
```

---

## Reviewing PRs

You are not auditing every line. You are checking:

1. **Does the PR description make sense?** Does it reference the task and FR?
2. **Does the shape look right?** New controller in `api/`, service in `domain/`, repo in `db/`?
3. **Did CI pass?** If not, don't merge — comment `@claude fix the CI failure`.
4. **Any obvious security issue?** Credentials logged? Missing auth annotation?

If something is wrong, comment on the PR line and tag `@claude`. It will push a fix.
If it's structurally wrong, close the PR and open a new issue with clearer acceptance criteria.

---

## What Claude handles well

- All Phase 0 scaffolding (Gradle, docker-compose, Flyway, jOOQ, CI)
- CRUD services and REST controllers with clear specs
- Integration tests from acceptance criteria
- React components and TanStack Query hooks
- Fixing its own CI failures

## What needs human judgment

- CDK infrastructure changes (review carefully before merging)
- Security-sensitive code (JWT filter, credential handling)
- Schema migrations (review SQL before merging — hard to reverse)
- Anything touching the hosted-cluster admin connection

---

## Kicking off a new phase

Open one issue to create all the issues for the next phase:

```
@claude Read docs/06-implementation/03-implementation-plan.md.
Create one GitHub issue per task in Phase 0.
Each issue should include the task description, expected output,
and the FR reference. Label all issues `phase-0`.
Do not start implementing yet — just create the issues.
```

Then let Claude pick up and implement each one.
