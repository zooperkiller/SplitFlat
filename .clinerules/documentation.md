# SplitFlat — Documentation Maintenance Rules

> **Purpose:** Keep the TDD (`docs/TechnicalDesignDocument.html` + `.md`) in sync with the codebase using **minimal tokens**. Never re-read the whole repo or whole documents to make small updates.

## 1. Golden Rules

1. **Never read the full TDD files** (`TechnicalDesignDocument.html` is ~65 KB, `.md` is ~38 KB). Use targeted `search_files` / `grep`-style commands instead.
2. **Never re-explore the repo** for a documentation update. Use the **Section Map** below to jump straight to the relevant section.
3. **Update both formats together** — HTML first, then MD (or regenerate MD from HTML with the converter script).
4. **One change = one section.** Locate the section anchor, edit only that block, done.

## 2. Section Map (where things live)

| If the change involves… | Update TDD section | HTML anchor |
|---|---|---|
| New/changed dependency, SDK, library version | 3. Technology Stack | `#3-tech-stack` |
| New screen, ViewModel, repository, service | 7. Module Design | `#7-module-design` |
| New/changed Firestore collection or fields | 6. Data Design | `#6-data-design` |
| Algorithm change (balance, debt, settlement) | 8. Core Algorithms | `#8-algorithms` |
| New route / navigation change | 7.2 Navigation | `#7-2-navigation` |
| UI/theme/design-system change | 9. UI/UX Design | `#9-ui-ux` |
| Security-relevant change | 10. Security & Privacy | `#10-security` |
| Error handling strategy change | 11. Error Handling | `#11-error-handling` |
| New tests / test strategy | 12. Testing Strategy | `#12-testing` |
| Build config, flavors, signing, CI | 13. Build & Deployment | `#13-build-deploy` |
| Known limitation added/removed | 14. Limitations | `#14-limitations` |
| Glossary, traceability matrix | 15. Appendix | `#15-appendix` |

## 3. Token-Efficient Update Workflow

### Step 1 — Locate (cheap)
```bash
# Find the section in HTML by anchor (returns ~5 lines of context)
python -c "import io,re; c=io.open(r'docs/TechnicalDesignDocument.html',encoding='utf-8').read(); i=c.find('id=\"7-2-navigation\"'); print(c[i:i+2000])"
```
Or use `search_files` with a unique keyword (e.g., a class name like `DebtSimplifier`).

### Step 2 — Edit (targeted)
Use `replace_in_file` with a SEARCH block containing **only the lines being changed** (2–5 lines), never the whole section.

### Step 3 — Regenerate MD (automatic)
```bash
python docs/_convert.py
```
This regenerates `TechnicalDesignDocument.md` from the HTML — no manual MD editing needed.

### Step 4 — Bump version footer
Update the footer line + `15.4 Document History` table row (add new version entry).

## 4. Repo Exploration Rules (for code changes)

When a code change requires doc updates, **do not re-read the whole repo**:

- **File location:** Use the **Traceability Matrix** in TDD §15.2 — it maps every feature → source file → key classes. Read only that file.
- **Quick check:** `search_files` for the class/function name across `app/src/` — read only matching files.
- **Structure changes:** Only re-run full exploration if `app/src/` package layout changed (rare).

## 5. Conventions

- **HTML structure:** Each section is `<section id="N-slug">…</section>`; subsections are `<h3 id="N-M-slug">`. Keep this pattern for new sections.
- **New section:** Copy an existing section's structure, add nav link in `<nav>`, add TOC entry in MD (converter handles it), append to Section Map above.
- **Tables:** HTML `<table>` ↔ MD pipe tables (converter handles).
- **Code samples:** HTML `<pre><code>` ↔ MD fenced blocks (converter handles).
- **Flow diagrams:** HTML `div.flow` with `span.step` ↔ MD `**Flow:** A → B → C` (converter handles).
- **Version footer:** `Version X.Y · Generated YYYY-MM-DD` — bump on every doc change.

## 6. Quick Reference — Key Files

| File | Purpose |
|---|---|
| `docs/TechnicalDesignDocument.html` | Source of truth (styled, printable) |
| `docs/TechnicalDesignDocument.md` | Generated companion (regenerate via `docs/_convert.py`) |
| `docs/_convert.py` | HTML → MD converter (run after HTML edits) |
| `.clinerules/documentation.md` | These rules |

## 7. Token Budget Guidelines

| Task | Target tokens |
|---|---|
| Small doc fix (typo, version bump) | < 2 K |
| Section update (new field, new screen) | < 8 K |
| New section | < 15 K |
| Full TDD regeneration | Only if structure overhaul — ask first |

**Rule of thumb:** If a doc update requires reading more than ~3 files or ~500 lines, stop and use the Section Map + Traceability Matrix instead.