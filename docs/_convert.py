import io
import re
from html import unescape

src = r'd:\SplitFlat\docs\TechnicalDesignDocument.html'
dst = r'd:\SplitFlat\docs\TechnicalDesignDocument.md'

c = io.open(src, encoding='utf-8').read()

# ---------- helpers ----------

def strip_tags(s):
    s = re.sub(r'<br\s*/?>', ' ', s)
    s = re.sub(r'<[^>]+>', '', s)
    return s.strip()

def inline(s):
    """Convert inline HTML to markdown."""
    s = re.sub(r'<br\s*/?>', '<br>', s)
    s = re.sub(r'<code>(.*?)</code>', r'`\1`', s, flags=re.S)
    s = re.sub(r'<strong>(.*?)</strong>', r'**\1**', s, flags=re.S)
    s = re.sub(r'<em>(.*?)</em>', r'*\1*', s, flags=re.S)
    s = re.sub(r'<span[^>]*>(.*?)</span>', r'\1', s, flags=re.S)
    s = re.sub(r'<a href="#([^"]+)">(.*?)</a>', r'\2', s, flags=re.S)
    s = re.sub(r'<[^>]+>', '', s)
    s = unescape(s)
    return s.strip()

def table_to_md(tbl):
    rows = re.findall(r'<tr>(.*?)</tr>', tbl, re.S)
    parsed = []
    for r in rows:
        cells = re.findall(r'<t[hd][^>]*>(.*?)</t[hd]>', r, re.S)
        parsed.append([inline(x) for x in cells])
    if not parsed:
        return ''
    out = []
    out.append('| ' + ' | '.join(parsed[0]) + ' |')
    out.append('|' + '---|' * len(parsed[0]))
    for row in parsed[1:]:
        out.append('| ' + ' | '.join(row) + ' |')
    return '\n'.join(out)

def ul_to_md(block):
    items = re.findall(r'<li>(.*?)</li>', block, re.S)
    lines = []
    for it in items:
        # nested lists not present in this doc; treat inline
        lines.append('- ' + inline(it))
    return '\n'.join(lines)

def block_to_md(block):
    """Convert a chunk of HTML body to markdown lines."""
    lines = []
    pos = 0
    # tokenize by top-level elements
    for m in re.finditer(r'<(h3|h4|p|pre|table|ul|div)[^>]*>.*?</\1>', block, re.S):
        # text before this element
        between = block[pos:m.start()]
        txt = strip_tags(between).strip()
        if txt:
            lines.append(txt)
            lines.append('')
        el = m.group(0)
        tag = m.group(1)
        if tag == 'h3':
            lines.append('### ' + inline(m.group(0)))
            lines.append('')
        elif tag == 'h4':
            lines.append('#### ' + inline(m.group(0)))
            lines.append('')
        elif tag == 'p':
            lines.append(inline(m.group(0)))
            lines.append('')
        elif tag == 'pre':
            code = re.sub(r'<[^>]+>', '', m.group(0))
            code = unescape(code)
            lines.append('```')
            lines.append(code.strip('\n'))
            lines.append('```')
            lines.append('')
        elif tag == 'table':
            lines.append(table_to_md(m.group(0)))
            lines.append('')
        elif tag == 'ul':
            lines.append(ul_to_md(m.group(0)))
            lines.append('')
        elif tag == 'div':
            inner = m.group(0)
            inner = inner[inner.find('>') + 1:inner.rfind('</div>')]
            if 'class="flow"' in m.group(0):
                # flow diagram: steps joined with arrows
                steps = re.findall(r'<span class="step[^"]*">(.*?)</span>', inner, re.S)
                if steps:
                    lines.append('**Flow:** ' + ' → '.join(inline(x) for x in steps))
                    lines.append('')
            else:
                lines.extend(block_to_md(inner))
                lines.append('')
        pos = m.end()
    tail = block[pos:]
    txt = strip_tags(tail).strip()
    if txt:
        lines.append(txt)
        lines.append('')
    return lines

# ---------- build markdown ----------

md = []

# Header
m = re.search(r'<h1[^>]*>(.*?)</h1>', c, re.S)
md.append('# ' + inline(m.group(1)))
md.append('')

# Intro paragraph after h1 (if any)
m = re.search(r'</h1>\s*<p[^>]*>(.*?)</p>', c, re.S)
if m:
    md.append(inline(m.group(1)))
    md.append('')

# TOC from nav
md.append('## Table of Contents')
md.append('')
nav = re.search(r'<nav[^>]*>(.*?)</nav>', c, re.S)
if nav:
    for href, txt in re.findall(r'<a href="#([^"]+)">([^<]+)</a>', nav.group(1)):
        anchor = href.lower()
        md.append('- [' + txt + '](#' + anchor + ')')
md.append('')

# Sections
for sec in re.finditer(r'<section id="([^"]+)">(.*?)</section>', c, re.S):
    sid, body = sec.group(1), sec.group(2)
    h2 = re.search(r'<h2[^>]*>(.*?)</h2>', body, re.S)
    title = inline(h2.group(1)) if h2 else sid
    md.append('---')
    md.append('')
    md.append('## ' + title)
    md.append('')
    # strip the h2 itself then convert rest
    rest = body[h2.end():] if h2 else body
    md.extend(block_to_md(rest))

# Footer
m = re.search(r'<footer>(.*?)</footer>', c, re.S)
if m:
    md.append('---')
    md.append('')
    md.append('*' + inline(m.group(1)) + '*')
    md.append('')

out = '\n'.join(md)
# collapse 3+ blank lines
out = re.sub(r'\n{3,}', '\n\n', out)
io.open(dst, 'w', encoding='utf-8', newline='\n').write(out)
print('written', dst, len(out), 'chars')