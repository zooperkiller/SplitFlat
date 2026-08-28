"""graphify pipeline for SplitFlat - Step 4: merge chunks + build graph."""
import json
from pathlib import Path

from graphify.build import build_merge

OUT = Path("graphify-out")

# Load AST result + all semantic chunks
ast = json.loads((OUT / ".graphify_ast.json").read_text(encoding="utf-8"))
fragments = [ast]

for chunk_path in sorted(OUT.glob(".graphify_chunk_*.json")):
    fragments.append(json.loads(chunk_path.read_text(encoding="utf-8")))

print(f"Merging {len(fragments)} fragments (1 AST + {len(fragments)-1} semantic chunks)")

# build_merge: merges fragments, dedupes, builds networkx graph
graph = build_merge(
    fragments,
    root=".",
)

print(f"Graph: {graph.number_of_nodes()} nodes, {graph.number_of_edges()} edges")

# Persist graph for later steps (cluster, analyze, render)
import networkx as nx
nx.write_gpickle = None  # avoid legacy attr confusion
data = nx.node_link_data(graph)
(OUT / "graph.json").write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
print("Saved graph.json")
