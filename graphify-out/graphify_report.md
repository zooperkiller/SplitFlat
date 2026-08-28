# Graph Report - SplitFlat  (2026-08-28)

## Corpus Check
- Corpus is ~22,961 words - fits in a single context window. You may not need a graph.

## Summary
- 92 nodes · 134 edges · 14 communities
- Extraction: 92% EXTRACTED · 8% INFERRED · 0% AMBIGUOUS · INFERRED: 11 edges (avg confidence: 0.89)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `23086625`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- GroupDetailScreen.kt
- MainActivity.kt
- AppNavigation
- SplitFlat Technical Design Document (HTML)
- block_to_md
- App Launcher Icon
- ExampleInstrumentedTest
- Screen
- DebtSimplifier
- gradlew

## God Nodes (most connected - your core abstractions)
1. `SplitFlat Technical Design Document (HTML)` - 12 edges
2. `AppNavigation()` - 11 edges
3. `Group` - 9 edges
4. `Expense` - 7 edges
5. `User` - 6 edges
6. `Screen` - 6 edges
7. `GroupDetailScreen()` - 6 edges
8. `HomeScreen()` - 6 edges
9. `block_to_md()` - 6 edges
10. `App Launcher Icon` - 6 edges

## Surprising Connections (you probably didn't know these)
- `Navigation Design (TDD §7.2)` --references--> `AppNavigation()`  [EXTRACTED]
  docs/TechnicalDesignDocument.html → app/src/main/java/com/example/splitflat/navigation/AppNavigation.kt
- `SplitFlat README` --conceptually_related_to--> `MainActivity`  [INFERRED]
  README.md → app/src/main/java/com/example/splitflat/MainActivity.kt
- `Firestore Data Design (TDD §6)` --references--> `Group`  [EXTRACTED]
  docs/TechnicalDesignDocument.html → app/src/main/java/com/example/splitflat/model/Models.kt
- `Module Design (TDD §7)` --references--> `HomeScreen()`  [EXTRACTED]
  docs/TechnicalDesignDocument.html → app/src/main/java/com/example/splitflat/ui/home/HomeScreen.kt
- `Module Design (TDD §7)` --references--> `QrCodeGenerator`  [INFERRED]
  docs/TechnicalDesignDocument.html → app/src/main/java/com/example/splitflat/utils/QrCodeGenerator.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **TDD Documentation Pipeline** — docs_technicaldesigndocument_tdd, docs_technicaldesigndocument_md_version, _clinerules_documentation_convert_script, _clinerules_documentation_maintenance_rules [EXTRACTED 1.00]
- **TDD-to-Code Traceability** — docs_technicaldesigndocument_algorithms, docs_technicaldesigndocument_navigation, docs_technicaldesigndocument_data_design, docs_technicaldesigndocument_module_design, docs_technicaldesigndocument_testing [EXTRACTED 1.00]

## Communities (14 total, 0 thin omitted)

### Community 0 - "GroupDetailScreen.kt"
Cohesion: 0.23
Nodes (10): Expense, User, AddExpenseScreen(), DonutChart(), ExpenseCard(), GroupDetailScreen(), QrCodeGenerator, Bitmap (+2 more)

### Community 1 - "MainActivity.kt"
Cohesion: 0.19
Nodes (10): Auto Merge Develop to Master Workflow, Nightly Cron Merge Schedule (21:30 UTC / 3 AM IST), MainActivity, SplitFlatTheme(), Bundle, ComponentActivity, UI/UX Design System (TDD §9), Feature Branch Workflow (+2 more)

### Community 2 - "AppNavigation"
Cohesion: 0.30
Nodes (7): Group, AppNavigation(), LoginScreen(), SignUpScreen(), CreateGroupScreen(), GroupCard(), HomeScreen()

### Community 3 - "SplitFlat Technical Design Document (HTML)"
Cohesion: 0.25
Nodes (9): HTML to MD Converter (docs/_convert.py), TDD Documentation Maintenance Rules, TDD Section Map, Known Limitations (TDD §14), SplitFlat Technical Design Document (Markdown), Navigation Design (TDD §7.2), Security & Privacy (TDD §10), SplitFlat Technical Design Document (HTML) (+1 more)

### Community 4 - "block_to_md"
Cohesion: 0.46
Nodes (7): block_to_md(), inline(), Convert inline HTML to markdown., Convert a chunk of HTML body to markdown lines., strip_tags(), table_to_md(), ul_to_md()

### Community 5 - "App Launcher Icon"
Cohesion: 0.29
Nodes (7): App Launcher Icon, Launcher Icon (hdpi), Launcher Icon Round (hdpi), Launcher Icon (xhdpi), Launcher Icon (xhdpi, round), Launcher Icon (xxhdpi), Launcher Icon (xxhdpi, round)

### Community 6 - "ExampleInstrumentedTest"
Cohesion: 0.29
Nodes (3): ExampleInstrumentedTest, ExampleUnitTest, Testing Strategy (TDD §12)

### Community 7 - "Screen"
Cohesion: 0.29
Nodes (6): CreateGroup, GroupDetail, Home, Login, Screen, SignUp

### Community 8 - "DebtSimplifier"
Cohesion: 0.50
Nodes (3): DebtSimplifier, Transaction, Core Algorithms: Debt Simplification (TDD §8)

### Community 9 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

## Knowledge Gaps
- **15 isolated node(s):** `Login`, `SignUp`, `Home`, `CreateGroup`, `Technology Stack (TDD §3)` (+10 more)
  These have ≤1 connection - possible missing edges or undocumented components.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `SplitFlat Technical Design Document (HTML)` connect `SplitFlat Technical Design Document (HTML)` to `DebtSimplifier`, `GroupDetailScreen.kt`, `ExampleInstrumentedTest`, `MainActivity.kt`?**
  _High betweenness centrality (0.271) - this node is a cross-community bridge._
- **Why does `AppNavigation()` connect `AppNavigation` to `GroupDetailScreen.kt`, `MainActivity.kt`, `SplitFlat Technical Design Document (HTML)`, `Screen`?**
  _High betweenness centrality (0.165) - this node is a cross-community bridge._
- **Why does `Testing Strategy (TDD §12)` connect `ExampleInstrumentedTest` to `SplitFlat Technical Design Document (HTML)`?**
  _High betweenness centrality (0.093) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `SplitFlat Technical Design Document (HTML)` (e.g. with `SplitFlat Technical Design Document (Markdown)` and `SplitFlat README`) actually correct?**
  _`SplitFlat Technical Design Document (HTML)` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Login`, `SignUp`, `Home` to the rest of the system?**
  _15 weakly-connected nodes found - possible documentation gaps or missing edges._