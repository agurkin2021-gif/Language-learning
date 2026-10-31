#!/usr/bin/env python3
"""Two-way, fail-closed vocabulary sync for one of the three profiles.

Phone <-> GitHub sync runs inside Android; these commands let another client
pull/push the same canonical files without introducing a second repository.

python scripts/sync_papa.py --language es --pull papa-es.json
python scripts/sync_papa.py --language es --push papa-es.json
python scripts/sync_papa.py --language es --check

For --push set GITHUB_TOKEN with Contents read/write on Language-learning.
"""
import argparse
import base64
import json
import os
from pathlib import Path
import sys
import unicodedata
import urllib.error
import urllib.parse
import urllib.request

REPO = "agurkin2021-gif/Language-learning"
BRANCH = "live-content"
PROFILES = ("papa", "mama", "dima")


def key(card):
    return (card["kind"], " ".join(unicodedata.normalize("NFC",card["spanish"]).casefold().split()))


def rank(card):
    return (
        card["revision"], card["origin"], bool(card["deleted"]),
        bool(card.get("mastered", False)), card.get("review_count",0),
        json.dumps(card, sort_keys=True, ensure_ascii=False)
    )


def validate(doc, profile, language):
    if not isinstance(doc, dict) or doc.get("version") != 1 or \
            doc.get("profile") != profile or doc.get("language") != language:
        raise ValueError("Vocabulary has wrong version/profile/language")
    cards = doc.get("items")
    if not isinstance(cards,list) or len(cards)>20000:
        raise ValueError("Missing or oversized vocabulary items")
    seen={}
    for card in cards:
        if not isinstance(card,dict) or not isinstance(card.get("spanish"),str) or \
                not card["spanish"].strip() or card.get("kind") not in ("word","phrase") or \
                not isinstance(card.get("revision"),int) or card["revision"]<0 or \
                not isinstance(card.get("origin"),str) or not card["origin"] or \
                not isinstance(card.get("deleted"),bool):
            raise ValueError("Invalid vocabulary record")
        if not card["deleted"] and (
                not isinstance(card.get("meaning"),str) or not card["meaning"].strip() or
                not isinstance(card.get("mastered"),bool) or
                type(card.get("category")) is not int or
                card["category"] not in (0,1,2,3) or
                type(card.get("last_result")) is not int or
                card["last_result"] not in (0,1,2) or
                type(card.get("review_count")) is not int or
                card["review_count"]<0 or
                type(card.get("next_due")) is not int or
                card["next_due"]<0):
            raise ValueError("Incomplete vocabulary card")
        if key(card) in seen:
            raise ValueError("Duplicate card in archive")
        seen[key(card)]=card
    return doc


def merge(a,b,profile,language):
    merged={}
    for card in a["items"]+b["items"]:
        k=key(card)
        if k not in merged or rank(card)>rank(merged[k]):
            merged[k]=card
    return {"version":1, "profile":profile,"language":language,
            "items":[merged[k] for k in sorted(merged)]}


def github(method,path,token=None,data=None):
    url="https://api.github.com/repos/"+REPO+"/"+path
    headers={"Accept":"application/vnd.github+json",
             "X-GitHub-Api-Version":"2022-11-28",
             "User-Agent":"Language-learning-vocabulary-sync"}
    if token: headers["Authorization"]="Bearer "+token
    if data is not None:
        headers["Content-Type"]="application/json"
        data=json.dumps(data).encode()
    request=urllib.request.Request(url,data=data,headers=headers,method=method)
    try:
        with urllib.request.urlopen(request,timeout=30) as r: return json.load(r)
    except urllib.error.HTTPError as exc:
        if exc.code==404 and method=="GET": return None
        if exc.code in (409,422) and method=="PUT": return "RETRY"
        raise RuntimeError("GitHub HTTP "+str(exc.code)) from None


def blank(profile,language):
    return {"version":1,"profile":profile,"language":language,"items":[]}


def remote(profile,language,token):
    path="content/"+profile+"/vocabulary-"+language+".json"
    value=github("GET","contents/"+path+"?ref="+BRANCH,token)
    if value is None:
        return blank(profile,language),None
    if value.get("type")!="file" or value.get("encoding")!="base64":
        raise ValueError("Unexpected vocabulary file")
    raw=base64.b64decode(value["content"])
    if len(raw)>1024*1024: raise ValueError("Vocabulary archive exceeds 1 MB")
    data=json.loads(raw.decode("utf-8"))
    return validate(data,profile,language),value["sha"]


def run(profile,argv=None):
    if profile not in PROFILES: raise ValueError("Unknown profile")
    cli=argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--language",choices=("es","en"),default="es")
    modes=cli.add_mutually_exclusive_group(required=True)
    modes.add_argument("--pull",metavar="LOCAL_JSON")
    modes.add_argument("--push",metavar="LOCAL_JSON")
    modes.add_argument("--check",action="store_true")
    args=cli.parse_args(argv)
    token=os.environ.get("GITHUB_TOKEN","")
    if args.push and not token:
        cli.error("GITHUB_TOKEN with Contents:write is required for --push")
    try:
        if args.push:
            path=Path(args.push)
            if not path.is_file(): raise ValueError("Local JSON file not found")
            local=validate(json.loads(path.read_text(encoding="utf-8")),profile,args.language)
            for attempt in range(5):
                remote_doc,sha=remote(profile,args.language,token)
                combined=merge(local,remote_doc,profile,args.language)
                content=json.dumps(combined,ensure_ascii=False,indent=2)+"\n"
                body={"message":"Sync "+profile+" "+args.language+" vocabulary",
                      "branch":BRANCH,"content":base64.b64encode(content.encode()).decode()}
                if sha: body["sha"]=sha
                result=github("PUT","contents/content/"+profile+"/vocabulary-"+args.language+".json",token,body)
                if result=="RETRY": continue
                path.write_text(content,encoding="utf-8")
                print("Merged %d cards for %s/%s" %
                      (len(combined["items"]),profile,args.language))
                return 0
            raise ValueError("Cloud revision conflict after five attempts")
        remote_doc,_=remote(profile,args.language,token)
        if args.check:
            print("%s/%s: %d records (%d mastered, %d tombstones)" % (
                profile,args.language,len(remote_doc["items"]),
                sum(c.get("mastered",False) for c in remote_doc["items"]),
                sum(c["deleted"] for c in remote_doc["items"])))
        else:
            path=Path(args.pull)
            if path.exists(): raise ValueError("Pull target exists; refusing to overwrite it")
            path.write_text(json.dumps(remote_doc,ensure_ascii=False,indent=2)+"\n",
                            encoding="utf-8")
            print("Saved %s/%s archive" % (profile,args.language))
        return 0
    except (RuntimeError,ValueError,urllib.error.URLError,json.JSONDecodeError) as exc:
        print("Sync failed: "+str(exc),file=sys.stderr)
        return 1


if __name__=="__main__":
    raise SystemExit("Use sync_papa.py, sync_mama.py or sync_dima.py")
