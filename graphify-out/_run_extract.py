"""graphify pipeline for SplitFlat - Part A (AST) + Step B0 (cache check)."""
import json
from pathlib import Path

from graphify.extract import collect_files, extract
from graphify.cache import check_semantic_cache

OUT = Path("graphify-out")
detect = json.loads((OUT / ".graphify_detect.json").read_text(encoding="utf-8"))

# ---- Part A - AST extraction for code files ----
code_files = []
for f in detect.get("files", {}).get("code", []):
    p = Path(f)
    code_files.extend(collect_files(p) if p.is_dir() else [p])

if code_files:
    result = extract(code_files, cache_root=Path("."))
    (OUT / ".graphify_ast.json").write_text(
        json.dumps(result, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    print(f"AST: {len(result['nodes'])} nodes, {len(result['edges'])} edges")
else:
    (OUT / ".graphify_ast.json").write_text(
        json.dumps({"nodes": [], "edges": [], "input_tokens": 0, "output_tokens": 0}),
        encoding="utf-8",
    )
    print("No code files")

# ---- Step B0 - check semantic cache ----
all_files = [
    f
    for cat in ("document", "paper", "image")
    for f in detect["files"].get(cat, [])
]
cached_nodes, cached_edges, cached_hyperedges, uncached = check_semantic_cache(
    all_files,
    root=".",
    prompt_file="C:/Users/Anish/.agents/skills/graphify/references/extraction-spec.md",
)

if cached_nodes or cached_edges or cached_hyperedges:
    (OUT / ".graphify_cached.json").write_text(
        json.dumps(
            {
                "nodes": cached_nodes,
                "edges": cached_edges,
                "hyperedges": cached_hyperedges,
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
else:
    (OUT / ".graphify_cached.json").unlink(missing_ok=True)
(OUT / ".graphify_uncached.txt").write_text("\n".join(uncached), encoding="utf-8")
print(f"Cache: {len(all_files) - len(uncached)} files hit, {len(uncached)} files need extraction")
print("--- semantic files needing extraction ---")
for f in uncached:
    print(" ", f)