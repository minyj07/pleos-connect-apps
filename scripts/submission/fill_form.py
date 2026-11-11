# -*- coding: utf-8 -*-
"""공모전 접수양식(02 앱 프로젝트 소개)의 1~7번 칸을 제한된 마크다운 기획서로 채웁니다.

    python fill_form.py --template-dir <unzip 한 양식 폴더> --proposal <proposal.md> --out <결과.docx>
                        --root <프로젝트 루트> [--app-name "앱 이름"] [--set KEY=VALUE ...]

- 양식의 다른 부분(참가 정보, 서명, 동의서)은 건드리지 않습니다.
- 지원하는 마크다운: `## N. 제목`(1~7), 문단, `**굵게**`, `- 글머리표`, `![캡션](경로)`, 파이프 표.
- `{{KEY}}` 자리표시는 --set 으로 채웁니다(없으면 "(접수 메일에 기재)").
표준 라이브러리 + Pillow 만 씁니다.
"""
import argparse
import io
import os
import re
import sys
import tempfile
import unicodedata
import zipfile
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

from PIL import Image

EMU_PER_CM = 360000
TABLE_W = 9400                      # nested table width (dxa); the answer cell is 9866 wide with 110 margins
IMG_MAX_W_CM = 14.0
IMG_MAX_H_CM = 8.4
IMG_MAX_PX = 1600
TAGS = ["app_summary", "planning_background", "vehicle_scenario", "key_features", "pleos_plan",
        "reference_files", "reference_link"]          # 8번째(shortform_link)는 그대로 둡니다
LANG = '<w:lang w:eastAsia="ko-KR"/>'


def run(text, bold=False, color=None, size=None):
    rpr = []
    if bold:
        rpr.append("<w:b/>")
    if color:
        rpr.append('<w:color w:val="%s"/>' % color)
    if size:
        rpr.append('<w:sz w:val="%d"/><w:szCs w:val="%d"/>' % (size, size))
    rpr.append(LANG)
    return '<w:r><w:rPr>%s</w:rPr><w:t xml:space="preserve">%s</w:t></w:r>' % ("".join(rpr), escape(text))


def inline_runs(text, bold=False, color=None, size=None):
    parts = text.split("**")
    if len(parts) % 2 == 0:            # unbalanced ** : keep the text literally
        return run(text, bold=bold, color=color, size=size)
    out = []
    for i, part in enumerate(parts):
        if part:
            out.append(run(part, bold=bold or i % 2 == 1, color=color, size=size))
    return "".join(out)


def para(text, after=90):
    return ('<w:p><w:pPr><w:spacing w:after="%d" w:line="290" w:lineRule="exact"/></w:pPr>%s</w:p>'
            % (after, inline_runs(text)))


def bullet(text, after=40):
    return ('<w:p><w:pPr><w:spacing w:after="%d" w:line="290" w:lineRule="exact"/>'
            '<w:ind w:left="300" w:hanging="220"/></w:pPr>%s<w:r><w:tab/></w:r>%s</w:p>'
            % (after, run("•"), inline_runs(text)))


def spacer(height=100):
    return '<w:p><w:pPr><w:spacing w:after="0" w:line="%d" w:lineRule="exact"/></w:pPr></w:p>' % height


def display_len(text):
    text = text.replace("**", "")
    return sum(2 if unicodedata.east_asian_width(ch) in ("W", "F") else 1 for ch in text)


def table(rows):
    ncol = max(len(r) for r in rows)
    rows = [r + [""] * (ncol - len(r)) for r in rows]
    weights = [min(max(max(display_len(r[c]) for r in rows), 8), 46) for c in range(ncol)]
    widths = [int(TABLE_W * w / sum(weights)) for w in weights]
    widths[-1] = TABLE_W - sum(widths[:-1])
    border = "".join('<w:%s w:val="single" w:sz="4" w:space="0" w:color="AEB9C5"/>' % side
                     for side in ("top", "left", "bottom", "right", "insideH", "insideV"))
    out = ['<w:tbl><w:tblPr><w:tblW w:w="%d" w:type="dxa"/><w:tblInd w:w="60" w:type="dxa"/>'
           '<w:tblBorders>%s</w:tblBorders><w:tblLayout w:type="fixed"/>'
           '<w:tblCellMar><w:top w:w="50" w:type="dxa"/><w:left w:w="90" w:type="dxa"/>'
           '<w:bottom w:w="50" w:type="dxa"/><w:right w:w="90" w:type="dxa"/></w:tblCellMar></w:tblPr><w:tblGrid>'
           % (TABLE_W, border)]
    out.extend('<w:gridCol w:w="%d"/>' % w for w in widths)
    out.append("</w:tblGrid>")
    for ri, row in enumerate(rows):
        out.append("<w:tr>")
        for ci, cell in enumerate(row):
            shade = '<w:shd w:val="clear" w:color="auto" w:fill="E8EEF4"/>' if ri == 0 else ""
            out.append('<w:tc><w:tcPr><w:tcW w:w="%d" w:type="dxa"/>%s</w:tcPr>'
                       '<w:p><w:pPr><w:spacing w:after="0" w:line="260" w:lineRule="exact"/></w:pPr>%s</w:p></w:tc>'
                       % (widths[ci], shade, inline_runs(cell.strip(), bold=ri == 0, size=18)))
        out.append("</w:tr>")
    out.append("</w:tbl>")
    return "".join(out) + spacer(120)


class Media:
    def __init__(self, root):
        self.root = root
        self.items = []                 # (part name, bytes)

    def add(self, rel_path, caption):
        path = os.path.join(self.root, rel_path.replace("/", os.sep))
        if not os.path.isfile(path):
            raise SystemExit("image not found: %s" % rel_path)
        im = Image.open(path).convert("RGB")
        if im.width > IMG_MAX_PX:
            im = im.resize((IMG_MAX_PX, round(im.height * IMG_MAX_PX / im.width)), Image.LANCZOS)
        buf = io.BytesIO()
        im.save(buf, "JPEG", quality=88, optimize=True)
        n = len(self.items) + 1
        self.items.append(("sub_%d.jpeg" % n, buf.getvalue()))
        scale = min(IMG_MAX_W_CM / im.width, IMG_MAX_H_CM / im.height)
        cx, cy = int(im.width * scale * EMU_PER_CM), int(im.height * scale * EMU_PER_CM)
        a = 'xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"'
        drawing = (
            '<w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="%d" cy="%d"/>'
            '<wp:docPr id="%d" name="Screenshot %d" descr="%s"/>'
            '<wp:cNvGraphicFramePr><a:graphicFrameLocks %s noChangeAspect="1"/></wp:cNvGraphicFramePr>'
            '<a:graphic %s><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">'
            '<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">'
            '<pic:nvPicPr><pic:cNvPr id="0" name="sub_%d.jpeg"/><pic:cNvPicPr/></pic:nvPicPr>'
            '<pic:blipFill><a:blip r:embed="rIdSub%d"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>'
            '<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="%d" cy="%d"/></a:xfrm>'
            '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom>'
            '<a:ln w="6350"><a:solidFill><a:srgbClr val="AEB9C5"/></a:solidFill></a:ln></pic:spPr>'
            '</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing>'
            % (cx, cy, 9100 + n, n, escape(caption, {'"': "&quot;"}), a, a, n, n, cx, cy))
        return ('<w:p><w:pPr><w:keepNext/><w:spacing w:before="80" w:after="30" w:line="240" w:lineRule="auto"/>'
                '<w:jc w:val="center"/></w:pPr><w:r>%s</w:r></w:p>'
                '<w:p><w:pPr><w:spacing w:after="140" w:line="250" w:lineRule="exact"/><w:jc w:val="center"/></w:pPr>%s</w:p>'
                % (drawing, inline_runs(caption, color="5B6770", size=17)))


def parse_sections(md):
    sections, cur = {}, None
    for line in md.splitlines():
        m = re.match(r"^##\s+(\d)\.\s", line)
        if m:
            cur = int(m.group(1))
            sections[cur] = []
        elif cur is not None:
            sections[cur].append(line.rstrip())
    return sections


def render(lines, media):
    """Markdown subset -> cell content XML (always ends with a paragraph)."""
    blocks, text, i = [], [], 0

    def flush():
        if text:
            blocks.append(("p", " ".join(text)))
            del text[:]

    while i < len(lines):
        line = lines[i].strip()
        img = re.match(r"^!\[(.*?)\]\((.+?)\)$", line)
        if not line:
            flush()
        elif img:
            flush()
            blocks.append(("img", img.group(2), img.group(1)))
        elif line.startswith("|"):
            flush()
            rows = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                cells = [c.strip() for c in lines[i].strip().strip("|").split("|")]
                if not all(re.match(r"^:?-{2,}:?$", c) for c in cells):
                    rows.append(cells)
                i += 1
            blocks.append(("table", rows))
            continue
        elif line.startswith("- "):
            flush()
            blocks.append(("bullet", line[2:].strip()))
        else:
            text.append(line)
        i += 1
    flush()

    out = []
    for n, block in enumerate(blocks):
        nxt = blocks[n + 1][0] if n + 1 < len(blocks) else None
        if block[0] == "p":
            out.append(para(block[1], after=50 if nxt == "bullet" else 90))
        elif block[0] == "bullet":
            out.append(bullet(block[1], after=40 if nxt == "bullet" else 100))
        elif block[0] == "table":
            out.append(table(block[1]))
        else:
            out.append(media.add(block[1], block[2]))
    if not out:
        out.append(para(" ", after=0))
    if blocks and blocks[-1][0] == "img":
        pass                              # the caption paragraph closes the cell
    return "".join(out)


def replace_cell(doc, tag, content):
    i = doc.index('<w:tag w:val="%s"/>' % tag)
    tc_start = doc.rfind("<w:tc>", 0, i)
    tcpr_end = doc.index("</w:tcPr>", tc_start) + len("</w:tcPr>")
    tc_end = doc.index("</w:tc>", i)
    tr_start = doc.rfind("<w:tr ", 0, i)
    assert tc_start < tcpr_end < i < tc_end and tr_start < tc_start
    doc = doc[:tcpr_end] + content + doc[tc_end:]
    trpr_end = doc.index("</w:trPr>", tr_start)          # long answers must be able to break across pages
    return doc[:tr_start] + doc[tr_start:trpr_end].replace("<w:cantSplit/>", "") + doc[trpr_end:]


def fill_team(doc, team):
    """Replace the underscore blank after "팀명(...)" (split over several runs by Word) with the team name."""
    count = [0]

    def para_repl(pm):
        p = pm.group(0)
        text = "".join(re.findall(r"<w:t[^>]*>([^<]*)</w:t>", p))
        if "팀명(" not in text or "____" not in text:
            return p
        count[0] += 1
        done = [False]

        def text_repl(tm):
            t = tm.group(2)
            if "_" not in t:
                return tm.group(0)
            if not done[0]:
                t = re.sub(r"_+", lambda _m: escape(team), t, count=1)
                done[0] = True
            return tm.group(1) + t.replace("_", "") + "</w:t>"

        return re.sub(r"(<w:t[^>]*>)([^<]*)</w:t>", text_repl, p)

    return re.sub(r"<w:p[ >].*?</w:p>", para_repl, doc, flags=re.S), count[0]


def unpack(docx, target):
    with zipfile.ZipFile(docx) as z:
        for name in z.namelist():
            if name.startswith(("/", "\\")) or ".." in name.replace("\\", "/").split("/"):
                raise SystemExit("unsafe path in template: %s" % name)
        z.extractall(target)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--template", help="양식 .docx (서명·참가 정보가 채워진 사본)")
    ap.add_argument("--template-dir", help="이미 unzip 한 양식 폴더")
    ap.add_argument("--proposal", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--root", required=True)
    ap.add_argument("--app-name", help="양식의 앱명(EV Companion)을 이 이름으로 바꿉니다")
    ap.add_argument("--team", help="비어 있는 팀명 칸 두 곳을 채웁니다")
    ap.add_argument("--set", action="append", default=[])
    args = ap.parse_args()
    tmp = None
    if args.template:
        tmp = tempfile.TemporaryDirectory()
        unpack(args.template, tmp.name)
        args.template_dir = tmp.name
    elif not args.template_dir:
        raise SystemExit("--template or --template-dir is required")

    values = dict(item.split("=", 1) for item in args.set)
    md = open(args.proposal, encoding="utf-8").read()
    missing = sorted(set(re.findall(r"\{\{([A-Z_]+)\}\}", md)) - set(values))
    md = re.sub(r"\{\{([A-Z_]+)\}\}", lambda m: values.get(m.group(1), "(접수 메일에 기재)"), md)
    sections = parse_sections(md)
    if sorted(sections) != [1, 2, 3, 4, 5, 6, 7]:
        raise SystemExit("proposal must have sections 1-7, found %s" % sorted(sections))

    doc_path = os.path.join(args.template_dir, "word", "document.xml")
    doc = open(doc_path, encoding="utf-8").read()
    if args.app_name:
        if doc.count("EV Companion") != 3:
            raise SystemExit("expected 3 app-name occurrences, found %d" % doc.count("EV Companion"))
        doc = doc.replace("EV Companion", escape(args.app_name))
    if args.team:
        doc, n = fill_team(doc, args.team)
        if n != 2:
            raise SystemExit("expected 2 blank team-name fields, found %d" % n)

    media = Media(args.root)
    for n, tag in enumerate(TAGS, start=1):
        doc = replace_cell(doc, tag, render(sections[n], media))
    ET.fromstring(doc.encode("utf-8"))                    # well-formed?

    rels_name = "word/_rels/document.xml.rels"
    with zipfile.ZipFile(args.out, "w", zipfile.ZIP_DEFLATED) as z:
        names = []
        for base, _dirs, files in os.walk(args.template_dir):
            for f in files:
                full = os.path.join(base, f)
                names.append((os.path.relpath(full, args.template_dir).replace(os.sep, "/"), full))
        names.sort(key=lambda t: (t[0] != "[Content_Types].xml", t[0]))
        for arc, full in names:
            data = open(full, "rb").read()
            if arc == "word/document.xml":
                data = doc.encode("utf-8")
            elif arc == rels_name and media.items:
                rels = data.decode("utf-8")
                extra = "".join(
                    '<Relationship Id="rIdSub%d" Type="http://schemas.openxmlformats.org/officeDocument/2006/'
                    'relationships/image" Target="media/%s"/>' % (k, name)
                    for k, (name, _b) in enumerate(media.items, start=1))
                data = rels.replace("</Relationships>", extra + "</Relationships>").encode("utf-8")
            elif arc == "[Content_Types].xml" and media.items:
                ct = data.decode("utf-8")
                if 'Extension="jpeg"' not in ct:
                    ct = ct.replace("<Default ", '<Default Extension="jpeg" ContentType="image/jpeg"/><Default ', 1)
                data = ct.encode("utf-8")
            z.writestr(arc, data)
        for name, blob in media.items:
            z.writestr("word/media/" + name, blob)

    print("written: %s (%d bytes, %d images)" % (args.out, os.path.getsize(args.out), len(media.items)))
    for n in range(1, 8):
        body = "\n".join(sections[n])
        print("  section %d: %d chars" % (n, len(re.sub(r"\s+", "", body))))
    if missing:
        print("  placeholders left as '(접수 메일에 기재)': %s" % ", ".join(missing))


if __name__ == "__main__":
    sys.exit(main())
