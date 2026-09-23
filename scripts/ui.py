#!/usr/bin/env python3
"""Small uiautomator XML helper for the Android emulator workflow."""
import sys
import xml.etree.ElementTree as ET

def main():
    if len(sys.argv) != 4 or sys.argv[1] != "tap-text":
        raise SystemExit("usage: ui.py tap-text <uiautomator.xml> <visible text>")
    root = ET.parse(sys.argv[2]).getroot()
    wanted = sys.argv[3]
    for node in root.iter("node"):
        if node.attrib.get("text") == wanted or node.attrib.get("content-desc") == wanted:
            coords = node.attrib.get("bounds", "").replace("][", ",").replace("[", "").replace("]", "").split(",")
            if len(coords) == 4:
                x1, y1, x2, y2 = map(int, coords)
                print((x1 + x2) // 2, (y1 + y2) // 2)
                return
    raise SystemExit(f"text not found: {wanted}")

if __name__ == "__main__":
    main()
