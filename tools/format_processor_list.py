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

--values goes one further and collapses a leaf object in VALUE position too -- the
`"config": { ... }` objects that are most of a motif config's bulk. That one obeys --max-width
(default 100), because a config naming five block ids should stay expanded rather than become one
very long line.

--runs handles the object that can never collapse at all because it holds one array: it puts that
object's consecutive SCALAR members on one line and leaves the array alone, so a scheme slot reads

    { "weight": 3, "min_count": 0, "max_count": 2, "placement": "against_wall",
      "variants": [ ... ] }

A run stops at the first object, array or comment, and wraps at --max-width.
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


def collapse(text: str, wanted: set[str] | None, max_width: int = 0) -> tuple[str, int]:
    """Rewrite the collapsible objects. `wanted` of None means every leaf object.

    `max_width` of 0 means no limit, which is the default and what `output_blocks` has always had:
    those entries are a block id and a probability and comfortably fit. Pass one to keep an entry
    naming four blocks expanded rather than turning it into a 178-character line.
    """
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
                    line = one_line(body, string_mask, j)
                    indent = j - (text.rfind("\n", 0, j) + 1)
                    if not max_width or indent + len(line) <= max_width:
                        edits.append((j, close + 1, line))
                j = close + 1
            else:
                j += 1

    for start, stop, replacement in sorted(edits, reverse=True):
        text = text[:start] + replacement + text[stop:]
    return text, len(edits)


def collapse_values(text: str, max_width: int) -> tuple[str, int]:
    """Collapse every leaf object, wherever it sits -- including one in VALUE position.

    {@code collapse} only looks inside arrays, which is right for `output_blocks` and leaves the
    motif configs' bulk untouched: their weight is in `"config": { ... }` objects hanging off a
    key, four to six lines each and nested three deep. Those are leaves by the same rule, so the
    only new thing here is where it looks.

    Unlike the array case this one obeys `max_width`, because a `config` naming five block ids does
    not want to be a 200-character line. An object that would not fit is left expanded.
    """
    string_mask, comment_mask = scan(text)

    edits = []
    for i, c in enumerate(text):
        if c != "{" or string_mask[i] or comment_mask[i]:
            continue
        close = match_brace(text, i, string_mask, comment_mask)
        body = text[i:close + 1]
        # A leaf holds no nested brace, so no two leaf spans can overlap and the edits below are
        # independent. A parent stays expanded whatever its children do, which is what we want.
        if "\n" not in body or not leaf(body, string_mask, comment_mask, i):
            continue
        line = one_line(body, string_mask, i)
        indent = i - (text.rfind("\n", 0, i) + 1)
        if indent + len(line) <= max_width:
            edits.append((i, close + 1, line))

    for start, stop, replacement in sorted(edits, reverse=True):
        text = text[:start] + replacement + text[stop:]
    return text, len(edits)


def members(text: str, open_index: int, close_index: int, string_mask, comment_mask):
    """`(start, end)` of each member directly inside these braces, comma and padding excluded."""
    found = []
    depth = 0
    start = None
    i = open_index + 1
    while i < close_index:
        if comment_mask[i]:
            i += 1
            continue
        c = text[i]
        if not string_mask[i]:
            if c in "{[":
                depth += 1
            elif c in "}]":
                depth -= 1
            elif c == "," and depth == 0:
                if start is not None:
                    found.append((start, i))
                start = None
                i += 1
                continue
        if start is None and not c.isspace():
            start = i
        i += 1
    if start is not None:
        end = close_index
        while end > start and text[end - 1].isspace():
            end -= 1
        found.append((start, end))
    return found


def scalar_member(text: str, start: int, end: int, string_mask, comment_mask) -> bool:
    """True when this member's value is a scalar and it carries no comment of its own."""
    for i in range(start, end):
        if comment_mask[i]:
            return False
        if not string_mask[i] and text[i] in "{[":
            return False
    return True


def collapse_runs(text: str, max_width: int) -> tuple[str, int]:
    """Put consecutive SCALAR members of an object on one line, wrapping at `max_width`.

    The two passes above only ever collapse a whole object, so an object holding one array stays
    fully expanded however trivial the rest of it is -- and in a scheme that is most of the file:
    `weight`, `min_count`, `max_count` and `placement` each get a line of their own above the
    `variants` array that is the only reason the object could not collapse.

    A run stops at the first member that is an object or an array, and at any comment, so nothing
    is ever folded past something that would be lost or misplaced. A run of one is left alone: it
    is already on its own line and joining it to nothing gains nothing.
    """
    string_mask, comment_mask = scan(text)

    edits = []
    for i, c in enumerate(text):
        if c != "{" or string_mask[i] or comment_mask[i]:
            continue
        close = match_brace(text, i, string_mask, comment_mask)
        if "\n" not in text[i:close + 1]:
            continue

        run = []
        for start, end in members(text, i, close, string_mask, comment_mask) + [(None, None)]:
            gap = run and start is not None and any(comment_mask[run[-1][1]:start])
            if not gap and start is not None                     and scalar_member(text, start, end, string_mask, comment_mask):
                run.append((start, end))
                continue
            if len(run) > 1 and "\n" in text[run[0][0]:run[-1][1]]:
                edits.append((run[0][0], run[-1][1],
                              wrap([text[a:b] for a, b in run],
                                   run[0][0] - (text.rfind("\n", 0, run[0][0]) + 1), max_width)))
            run = []
    for start, stop, replacement in sorted(edits, reverse=True):
        text = text[:start] + replacement + text[stop:]
    return text, len(edits)


def wrap(pieces: list[str], indent: int, max_width: int) -> str:
    """`a, b, c` on as few lines as fit, each continuation at the members' own indent."""
    lines = [[]]
    width = indent
    for index, piece in enumerate(pieces):
        tail = "," if index < len(pieces) - 1 else ""
        addition = len(piece) + len(tail) + (1 if lines[-1] else 0)
        if lines[-1] and width + addition > max_width:
            lines.append([])
            width = indent
            addition = len(piece) + len(tail)
        lines[-1].append(piece + tail)
        width += addition
    return ("\n" + " " * indent).join(" ".join(line) for line in lines).rstrip(",")


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
    parser.add_argument("--runs", action="store_true",
                        help="also put consecutive SCALAR members of a bigger object on one line,"
                             " e.g. weight/min_count/max_count above a variants array")
    parser.add_argument("--values", action="store_true",
                        help="also collapse a leaf object in VALUE position, e.g. a short"
                             " \"config\": { ... }, subject to --max-width")
    parser.add_argument("--max-width", type=int, default=100,
                        help="longest line to produce, once --values or --runs is on"
                             " (default: 100). Without either, the array pass is unlimited, as it"
                             " always was")
    args = parser.parse_args()

    wanted = None if args.all_leaves else {a.strip() for a in args.arrays.split(",") if a.strip()}
    changed_any = False

    for path in args.files:
        original = path.read_text(encoding="utf-8")
        result, count = collapse(original, wanted,
                                 args.max_width if args.values or args.runs else 0)
        if args.values:
            result, extra = collapse_values(result, args.max_width)
            count += extra
        if args.runs:
            result, extra = collapse_runs(result, args.max_width)
            count += extra

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
