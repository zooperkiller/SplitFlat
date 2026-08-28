"""graphify pipeline for SplitFlat - Steps 5-9: cluster, label, analyze, report, export HTML."""
import json
import subprocess
from pathlib import Path

import networkx as nx
from networkx.readwrite import json_graph

from graphify.cluster import cluster, cohesion_score, label_communities_by_hub
from graphify.analyze import god_nodes, surprising_connections, suggest_questions
from graphify.report import generate as generate_report
from graphify.export import to_html

OUT = Path("graphify-out")

# --- Load graph ---
data = json.loads((OUT / "graph.json").read_text(encoding="utf-8"))
G = json_graph.node_link_graph(data, directed=False, multigraph=False)
print(f"Loaded graph: {G.number_of_nodes()} nodes, {G.number_of_edges()} edges")

# --- Step 5: Cluster + label ---
communities = cluster(G, resolution=1.0)
community_labels = label_communities_by_hub(G, communities)
cohesion_scores = {cid: cohesion_score(G, nodes) for cid, nodes in communities.items()}
print(f"Communities: {len(communities)}")
for cid, nodes in sorted(communities.items(), key=lambda kv: -len(kv[1])):
    print(f"  [{cid}] {community_labels.get(cid, '?')} ({len(nodes)} nodes, cohesion={cohesion_scores[cid]:.2f})")

# --- Step 6: Analyze ---
god_node_list = god_nodes(G, top_n=10)
surprise_list = surprising_connections(G, communities, top_n=5)
questions = suggest_questions(G, communities, community_labels, top_n=7)
print(f"God nodes: {len(god_node_list)}, surprises: {len(surprise_list)}, questions: {len(questions)}")

# --- Token cost (from chunks) ---
token_cost = {"input_tokens": 0, "output_tokens": 0}
for p in [OUT / ".graphify_ast.json"] + sorted(OUT.glob(".graphify_chunk_*.json")):
    d = json.loads(p.read_text(encoding="utf-8"))
    token_cost["input_tokens"] += d.get("input_tokens", 0)
    token_cost["output_tokens"] += d.get("output_tokens", 0)

# --- Detection result ---
detection_result = json.loads((OUT / ".graphify_detect.json").read_text(encoding="utf-8"))

# --- Git commit ---
try:
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
except Exception:
    commit = None

# --- Step 7: Report ---
report_md = generate_report(
    G,
    communities,
    cohesion_scores,
    community_labels,
    god_node_list,
    surprise_list,
    detection_result,
    token_cost,
    root=".",
    suggested_questions=questions,
    min_community_size=3,
    built_at_commit=commit,
)
(OUT / "graphify_report.md").write_text(report_md, encoding="utf-8")
print(f"Report written: {len(report_md)} chars")

# --- Step 8: HTML export ---
member_counts = {cid: len(nodes) for cid, nodes in communities.items()}
ok = to_html(
    G,
    communities,
    str(OUT / "graphify.html"),
    community_labels=community_labels,
    member_counts=member_counts,
)
print(f"HTML export: {'OK' if ok else 'FAILED'}")

# --- Step 9: Manifest ---
manifest = {
    "nodes": G.number_of_nodes(),
    "edges": G.number_of_edges(),
    "communities": {str(cid): {"label": community_labels.get(cid), "size": len(nodes), "cohesion": cohesion_scores[cid]} for cid, nodes in communities.items()},
    "god_nodes": god_node_list,
    "surprising_connections": surprise_list,
    "suggested_questions": questions,
    "token_cost": token_cost,
    "built_at_commit": commit,
}
(OUT / "graphify_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
print("Manifest written: graphify_manifest.json")
print("DONE")