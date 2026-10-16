#!/usr/bin/env python3
"""Render the specification's headings, tables, lists and inline Markdown for print."""
from pathlib import Path
import html
import argparse
import re
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--coding', action='store_true')
args = parser.parse_args()
name = 'coding-spec' if args.coding else 'program-spec'
title = 'HeySigApp Coding Specification' if args.coding else 'HeySigApp Program Specification'
source = root / ('docs/' + name + '.md')
output = root / ('docs/HeySigApp-' + name + '.pdf')

def inline(text):
    text = html.escape(text)
    text = re.sub(r'`([^`]+)`', r'<code>\1</code>', text)
    text = re.sub(r'\*\*([^*]+)\*\*', r'<strong>\1</strong>', text)
    return re.sub(r'\[([^]]+)\]\(([^)]+)\)', lambda m: '<a href="' + html.escape('https://github.com/5310H/HeySigApp/blob/main/docs/' + html.unescape(m[2]), quote=True) + '">' + m[1] + '</a>', text)

lines = source.read_text().splitlines()
blocks = []
i = 0
while i < len(lines):
    line = lines[i]
    if not line.strip():
        i += 1
        continue
    if line.startswith('```'):
        code = []
        i += 1
        while i < len(lines) and not lines[i].startswith('```'):
            code.append(lines[i])
            i += 1
        i += 1
        blocks.append('<pre><code>' + html.escape('\n'.join(code)) + '</code></pre>')
    elif line.startswith('#'):
        level = len(line) - len(line.lstrip('#'))
        blocks.append(f'<h{level}>{inline(line[level:].strip())}</h{level}>')
        i += 1
    elif line.startswith('|'):
        rows = []
        while i < len(lines) and lines[i].startswith('|'):
            cells = [x.strip() for x in lines[i].strip().strip('|').split('|')]
            if not all(re.fullmatch(r':?-+:?', x) for x in cells):
                tag = 'th' if not rows else 'td'
                rows.append('<tr>' + ''.join(f'<{tag}>{inline(x)}</{tag}>' for x in cells) + '</tr>')
            i += 1
        blocks.append('<table><thead>' + rows[0] + '</thead><tbody>' + ''.join(rows[1:]) + '</tbody></table>')
    elif re.match(r'^(?:- |\d+\. )', line):
        ordered = bool(re.match(r'^\d+\.', line))
        tag = 'ol' if ordered else 'ul'
        items = []
        pattern = r'^\d+\. ' if ordered else r'^- '
        while i < len(lines) and re.match(pattern, lines[i]):
            items.append('<li>' + inline(re.sub(pattern, '', lines[i])) + '</li>')
            i += 1
        blocks.append(f'<{tag}>' + ''.join(items) + f'</{tag}>')
    else:
        para = [line]
        i += 1
        while i < len(lines) and lines[i].strip() and not re.match(r'^(#|```|\||- |\d+\. )', lines[i]):
            para.append(lines[i])
            i += 1
        blocks.append('<p>' + inline(' '.join(para)) + '</p>')

css = '''
@page { size: Letter; margin: .65in .65in .7in;
 @bottom-left { content: "HeySigApp · Specification · October 16, 2026"; font: 8pt Arial; color: #555; }
 @bottom-right { content: counter(page) " / " counter(pages); font: 8pt Arial; color: #555; }
}
body { font: 10pt/1.38 Arial, sans-serif; color: #18222c; }
h1 { font-size: 23pt; line-height: 1.15; margin: 0 0 12pt; }
h3 { font-size: 11pt; margin: 13pt 0 6pt; break-after: avoid; }
pre { break-inside: avoid; background: #f2f4f6; border: 1px solid #ccd3da; padding: 7pt; white-space: pre-wrap; overflow-wrap: anywhere; font: 8pt/1.3 monospace; }
pre code { font-size: inherit; }
h2 { font-size: 14pt; margin: 18pt 0 7pt; border-bottom: 1px solid #aab9c7; padding-bottom: 4pt; break-after: avoid; }
p { margin: 0 0 8pt; orphans: 3; widows: 3; }
a { color: #16496d; text-decoration: none; }
code { font-size: 8.7pt; overflow-wrap: anywhere; }
table { width: 100%; border-collapse: collapse; margin: 9pt 0 12pt; font-size: 9pt; }
th,td { border: 1px solid #b8c3cc; text-align: left; vertical-align: top; padding: 5pt; }
th { background: #e9eff4; font-weight: bold; }
tr { break-inside: avoid; } thead { display: table-header-group; }
li { margin-bottom: 5pt; orphans: 2; widows: 2; }
ul,ol { padding-left: 19pt; }
'''
rendered = root / ('docs/' + name + '-print.html')
rendered.write_text('<!doctype html><html lang="en"><meta charset="utf-8"><title>' + title + '</title><style>' + css + '</style><body>' + '\n'.join(blocks) + '</body></html>')
chrome = shutil.which('chromium') or shutil.which('google-chrome')
if not chrome:
    raise SystemExit('Chromium or Google Chrome is required')
subprocess.run([chrome, '--headless', '--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage', '--no-pdf-header-footer', '--print-to-pdf=' + str(output), rendered.as_uri()], check=True, capture_output=True)
print(output)
