#!/usr/bin/env python3
"""Filter candidate cards against the full canonical GitHub vocabulary.

python scripts/vocabulary_filter.py --profile papa --language es \
    --candidates proposed.json --output approved.json

Read-only public GitHub access; fails closed for empty or invalid archives.
"""
import argparse
import json
from pathlib import Path
import sys
from profile_vocabulary_sync import remote, key


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile",required=True,choices=("papa","mama","dima"))
    parser.add_argument("--language",required=True,choices=("es","en"))
    parser.add_argument("--candidates",required=True)
    parser.add_argument("--output",required=True)
    a=parser.parse_args()
    try:
        archive,_=remote(a.profile,a.language,None)
        if not archive["items"]:
            raise ValueError("The phone has not populated the canonical vocabulary yet")
        known={key(card) for card in archive["items"]} # mastered + studying + tombstones
        candidates=json.loads(Path(a.candidates).read_text(encoding="utf-8"))
        if candidates.get("version")!=1 or not isinstance(candidates.get("items"),list):
            raise ValueError("Invalid candidate feed")
        approved=[]
        seen=set()
        for card in candidates["items"]:
            if card.get("type") not in ("word","phrase"):
                raise ValueError("Invalid candidate type")
            if not all(isinstance(card.get(f),str) and card[f].strip()
                       for f in ("id","spanish","meaning")):
                raise ValueError("Invalid candidate")
            card_key=(card["type"]," ".join(card["spanish"].casefold().split()))
            if card_key not in known and card_key not in seen:
                seen.add(card_key)
                approved.append(card)
        if Path(a.candidates).resolve()==Path(a.output).resolve():
            raise ValueError("Refusing to overwrite input")
        with Path(a.output).open("x",encoding="utf-8") as target:
            json.dump({"version":1,"items":approved},target,ensure_ascii=False,indent=2)
            target.write("\n")
        print("Approved %d of %d; all archived cards including mastered checked" %
              (len(approved),len(candidates["items"])))
        return 0
    except (ValueError,OSError,KeyError,TypeError) as exc:
        print("FAILED: "+str(exc),file=sys.stderr)
        return 1


if __name__=="__main__":
    sys.exit(main())
