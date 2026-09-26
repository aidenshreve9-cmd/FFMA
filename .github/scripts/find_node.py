"""Prints "x y", the centre of the first UI node whose text or description matches a label.

A plain label matches the start of the text; a label starting with "~" matches anywhere in it.
Prints nothing when there's no match, so the caller can report the missing control.
"""
import re
import sys
import xml.etree.ElementTree as ET

path, label = sys.argv[1], sys.argv[2]
try:
    root = ET.parse(path).getroot()
except (ET.ParseError, OSError):
    sys.exit(0)

anywhere = label.startswith("~")
wanted = label[1:] if anywhere else label
for node in root.iter("node"):
    for attr in ("text", "content-desc"):
        value = node.get(attr) or ""
        if (wanted in value) if anywhere else value.startswith(wanted):
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
            print((x1 + x2) // 2, (y1 + y2) // 2)
            sys.exit(0)
