# SplitFlat

SplitFlat is a minimalist, modern Android application for splitting expenses and managing group finances, built with Native Kotlin, Jetpack Compose, and Firebase.

## Git Workflow & Branching Strategy

This project follows a strict **Feature Branch Workflow** (similar to Gitflow) to maintain a clean, stable, and highly organized source control history.

### Core Branches
- **`main`**: The source of truth. Code on `main` is strictly production-ready and fully tested. Direct commits to `main` are prohibited.
- **`develop`**: The primary integration branch. All completed features merge into `develop` first. This branch reflects the latest delivered development changes for the next release.

### Temporary Branches
- **`feature/<feature-name>`**: Branched from `develop`. Used to build new features. 
  - *Example*: `feature/expense-management`, `feature/dark-mode`
- **`bugfix/<bug-name>`**: Branched from `develop`. Used to fix bugs discovered during development.
  - *Example*: `bugfix/login-crash`
- **`hotfix/<issue>`**: Branched from `main`. Used to urgently fix a critical bug in production. Merges back into both `main` and `develop`.

## Rules & Conventions

1. **Never commit directly to `main` or `develop`**: Always create a feature or bugfix branch.
2. **Branch Naming**: Use lowercase letters, numbers, and hyphens only. Keep names descriptive.
3. **Commit Messages**: 
   - Write clear, concise commit messages in the imperative mood (e.g., "Add user model", not "Added user model").
   - Prefix commits if necessary (e.g., `feat: Add expense split logic`, `fix: Resolve UI overlap on home screen`, `refactor: Clean up navigation`).
4. **Merging**: 
   - Always pull the latest `develop` into your feature branch and resolve conflicts before merging back.
   - Use Pull Requests (PRs) if collaborating with others, or squash-merge when closing a local feature to keep the history clean.

---

### Setup Instructions
1. `git clone` the repository.
2. `git checkout develop` to switch to the active development branch.
3. `git checkout -b feature/your-feature-name` to start working!