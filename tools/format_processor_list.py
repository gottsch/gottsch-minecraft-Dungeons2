#!/usr/bin/env python3
"""Collapse leaf objects inside chosen arrays onto one line, in a JSON file WITH comments.

    python tools/format_processor_list.py src/main/resources/data/dungeons2/worldgen/processor_list/classic_weathering.json --write

Turns this:

    "output_blocks": [
      {
        "block": "minecraft:cobblestone_stairs",
        "probability": 0.43
      },
      ...

into this:

    "output_blocks": [
      { "block": "minecraft:cobblestone_stairs", "probability": 0.43 },
      ...


WHY THIS IS A TEXT TRANSFORMER AND NOT A JSON ROUND TRIP
--------------------------------------------------------
The processor lists are roughly a thousand lines each and most of that is `//` comments carrying
the reasoning behind every rate. `json.load` drops all of them and `json.dump` cannot put them
back. So this never parses the file to rewrite it: it walks the text, finds the objects it may
collapse, and rewrites only those spans. Every byte outside them -- comments, blank lines,
alignment, key order -- is untouched.

The parser IS used, once, as a safety net: the result is compared to the original with comments
stripped, and the tool refuses to write if the two do not decode to identical data.

WHAT IT WILL AND WILL NOT COLLAPSE
----------------------------------
Only a LEAF object: one containing no nested `{`/`[` and no comment of its own. So a rules entry
with an `input_predicate` inside it is left expanded, and an entry with a `// note` above one of
its fields keeps its shape rather than folding the note into oblivion.

Only inside the arrays named by --arrays (default: output_blocks). Widen it with
--arrays output_blocks,variants,blocks or hand it --all-leaves to collapse every leaf object in
the file.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


def scan(text: str):
    """Index the text once: which characters are inside a string, and where comments run.

    Everything else here needs to tell a real `{` from one inside a string literal or a comment,
    and doing that ad hoc in three places is how such a tool gets subtly wrong.
    """
    in_string = False
    in_line_comment = False
    in_block_comment = False
    escaped = False
    string_mask = bytearray(len(text))
    comment_mask = bytearray(len(text))

    i = 0
    while i < len(text):
        c = text[i]
        nxt = text[i + 1] if i + 1 < len(text) else ""

        if in_line_comment:
            comment_mask[i] = 1
            if c == "\n":
                in_line_comment = False
        elif in_block_comment:
            comment_mask[i] = 1
            if c == "*" and nxt == "/":
                comment_mask[i + 1] = 1
                i += 1
                in_block_comment = False
        elif in_string:
            string_mask[i] = 1
            if escaped:
                escaped = False
            elif c == "\\":
                escaped = True
            elif c == '"':
                in_string = False
        else:
            if c == '"':
                in_string = True
                string_mask[i] = 1
            elif c == "/" and nxt == "/":
                in_line_comment = True
                comment_mask[i] = comment_mask[i + 1] = 1
                i += 1
            elif c == "/" and nxt == "*":
                in_block_comment = True
                comment_mask[i] = comment_mask[i + 1] = 1
                i += 1
        i += 1
    return string_mask, comment_mask


def match_brace(text: str, start: int, string_mask, comment_mask) -> int:
    """Index of the `}` or `]` closing the bracket at `start`."""
    opener = text[start]
    closer = {"{": "}", "[": "]"}[opener]
    depth = 0
    for i in range(start, len(text)):
        if string_mask[i] or comment_mask[i]:
            continue
        if text[i] in "{[":
            depth += 1
        elif text[i] in "}]":
            depth -= 1
            if depth == 0:
                assert text[i] == closer, f"mismatched bracket at {i}"
                return i
    raise ValueError(f"unclosed {opener} at offset {start}")


def leaf(body: str, string_mask, comment_mask, offset: int) -> bool:
    """True if this object holds only scalars and carries no comment of its own.

    The object's OWN braces are excluded, which is not a nicety: counting them makes every object
    look nested and the tool silently collapses nothing at all.
    """
    for i, c in enumerate(body[1:-1], start=1):
        absolute = offset + i
        if comment_mask[absolute]:
            return False
        if string_mask[absolute]:
            continue
        if c in "{[":
            return False
    return True


def one_line(body: str, string_mask, offset: int) -> str:
    """`{ "a": 1, "b": 2 }` -- whitespace runs outside strings squeezed to a single space."""
    out = []
    space_pending = False
    for i, c in enumerate(body):
        if not string_mask[offset + i] and c.isspace():
            space_pending = True
            continue
        if space_pending and out:
            out.append(" ")
        space_pending = False
        out.append(c)
    inner = "".join(out).strip()
    # inner still carries the braces; normalise the padding inside them
    inner = inner[1:-1].strip()
    inner = re.sub(r"\s*,\s*", ", ", inner)
    inner = re.sub(r'"\s*:\s*', '": ', inner)
    return "{ " + inner + " }" if inner else "{}"


def array_names(text: str, position: int, string_mask, comment_mask) -> str | None:
    """The key an array at `position` is the value of, e.g. `output_blocks`."""
    i = position - 1
    while i >= 0 and (text[i].isspace() or comment_mask[i]):
        i -= 1
    if i < 0 or text[i] != ":":
        return None
    i -= 1
    while i >= 0 and (text[i].isspace() or comment_mask[i]):
        i -= 1
    if i < 0 or text[i] != '"':
        return None
    end = i
    i -= 1
    while i >= 0 and text[i] != '"':
        i -= 1
    return text[i + 1:end]


def collapse(text: str, wanted: set[str] | None) -> tuple[str, int]:
    """Rewrite the collapsible objects. `wanted` of None means every leaf object."""
    string_mask, comment_mask = scan(text)

    # Right to left, so an edit never invalidates an offset we have not used yet.
    edits = []
    for i, c in enumerate(text):
        if c != "[" or string_mask[i] or comment_mask[i]:
            continue
        if wanted is not None:
            name = array_names(text, i, string_mask, comment_mask)
            if name not in wanted:
                continue
        end = match_brace(text, i, string_mask, comment_mask)
        j = i + 1
        while j < end:
            if text[j] == "{" and not string_mask[j] and not comment_mask[j]:
                close = match_brace(text, j, string_mask, comment_mask)
                body = text[j:close + 1]
                if "\n" in body and leaf(body, string_mask, comment_mask, j):
                    edits.append((j, close + 1, one_line(body, string_mask, j)))
                j = close + 1
            else:
                j += 1

    for start, stop, replacement in sorted(edits, reverse=True):
        text = text[:start] + replacement + text[stop:]
    return text, len(edits)


def strip_comments(text: str) -> str:
    string_mask, comment_mask = scan(text)
    return "".join(c for i, c in enumerate(text) if not comment_mask[i])


def data_of(text: str):
    """The file's actual data, for the before/after safety check."""
    return json.loads(strip_comments(text))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("files", nargs="+", type=Path)
    parser.add_argument("--write", action="store_true",
                        help="edit in place; without it the result goes to stdout")
    parser.add_argument("--check", action="store_true",
                        help="exit 1 if any file would change, and write nothing")
    parser.add_argument("--arrays", default="output_blocks",
                        help="comma-separated array keys to collapse inside"
                             " (default: output_blocks)")
    parser.add_argument("--all-leaves", action="store_true",
                        help="collapse every leaf object in the file, whatever array it is in")
    args = parser.parse_args()

    wanted = None if args.all_leaves else {a.strip() for a in args.arrays.split(",") if a.strip()}
    changed_any = False

    for path in args.files:
        original = path.read_text(encoding="utf-8")
        result, count = collapse(original, wanted)

        # The safety net. A formatter that silently alters data is worse than no formatter, and
        # the brace matching above is exactly the kind of code that is wrong in one file out of
        # fifty. Comments are compared too: they are most of these files.
        if data_of(result) != data_of(original):
            print(f"{path}: ABORTED -- the rewrite changed the data. This is a bug in the tool;"
                  f" nothing was written.", file=sys.stderr)
            return 2
        if strip_comments(original).count("\n") and \
                len(re.findall(r"//", original)) != len(re.findall(r"//", result)):
            print(f"{path}: ABORTED -- a comment was lost. Nothing was written.", file=sys.stderr)
            return 2

        if result == original:
            if not args.write and not args.check:
                sys.stdout.write(result)
            continue

        changed_any = True
        if args.check:
            print(f"{path}: {count} object(s) would be collapsed")
        elif args.write:
            path.write_text(result, encoding="utf-8", newline="")
            print(f"{path}: collapsed {count} object(s)")
        else:
            sys.stdout.write(result)

    return 1 if (args.check and changed_any) else 0


if __name__ == "__main__":
    raise SystemExit(main())
