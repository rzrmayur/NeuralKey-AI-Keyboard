#!/usr/bin/env python3
"""
Builds the on-device language model assets used by SuggestionEngine:

  app/src/main/assets/english_lm.dict       gzip text, "word<TAB>score[<TAB>Cased]" (score = round(ln(count) * 10);
                                            Cased is the preferred capitalisation, e.g. London, I'm, OK)
  app/src/main/assets/english_bigrams.dict  gzip text, "prev<TAB>next1 next2 ... next6" (best first)

Inputs (downloaded into tools/.cache on first run, or place them there manually):
  count_1w.txt / count_2w.txt   Peter Norvig, https://norvig.com/ngrams/  (word/bigram frequency counts)
  en_US_wordlist.combined.txt   AOSP LatinIME en_US dictionary, used ONLY as a spelling filter
  english_words.dict            the SCOWL dictionary already bundled in the app, used as a spelling filter

Usage:  python tools/build_language_model.py
"""
import base64
import gzip
import math
import os
import re
import sys
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, "tools", ".cache")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")

MAX_WORDS = 100_000
MAX_NEXT = 6
MAX_PREV_RANK = 45_000

SLANG = {
    "lol", "omg", "ok", "okay", "btw", "idk", "imo", "thx", "pls", "plz", "gonna", "wanna",
    "gotta", "yeah", "yep", "nope", "hey", "haha", "hahaha", "lmao", "tbh", "fyi", "asap",
    "thanks", "hi", "hello", "bye", "yup", "cool", "awesome", "congrats", "email", "emails",
    "app", "apps", "wifi", "online", "selfie", "emoji", "podcast", "whatsapp", "instagram",
    "youtube", "google", "iphone", "android", "gmail", "facebook", "twitter", "tiktok",
}
APOSTROPHE_S = {
    "it's", "that's", "what's", "he's", "she's", "there's", "here's", "let's", "who's",
    "how's", "where's", "when's", "why's", "everyone's", "somebody's", "nobody's",
}
CONTRACTION_SUFFIXES = ("n't", "'m", "'ll", "'ve", "'re", "'d")

SOURCES = {
    "count_1w.txt": "https://norvig.com/ngrams/count_1w.txt",
    "count_2w.txt": "https://norvig.com/ngrams/count_2w.txt",
    "en_US_wordlist.combined.txt": (
        "https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/"
        "refs/heads/main/dictionaries/en_US_wordlist.combined.gz?format=TEXT"
    ),
}


def ensure_sources():
    os.makedirs(CACHE, exist_ok=True)
    for name, url in SOURCES.items():
        path = os.path.join(CACHE, name)
        if os.path.exists(path):
            continue
        print("downloading", url)
        data = urllib.request.urlopen(url, timeout=120).read()
        if name.startswith("en_US"):
            data = gzip.decompress(base64.b64decode(data))
        with open(path, "wb") as f:
            f.write(data)


def load_spelling_filter():
    scowl = set()
    with gzip.open(os.path.join(ASSETS, "english_words.dict"), "rt", encoding="utf8") as f:
        for line in f:
            scowl.add(line.strip().lower())
    aosp, offensive, raw = set(), set(), {}
    with open(os.path.join(CACHE, "en_US_wordlist.combined.txt"), encoding="utf8") as f:
        for line in f:
            m = re.match(r"\s*word=([^,]+),f=(\d+),(.*)", line)
            if not m:
                continue
            word, freq = m.group(1), int(m.group(2))
            if "possibly_offensive=true" in m.group(3):
                offensive.add(word.lower())
                continue
            aosp.add(word.lower())
            raw[word] = freq
    lower_forms = {w for w in raw if w == w.lower()}
    cased = {}
    for word, freq in raw.items():
        low = word.lower()
        if word != low and low not in lower_forms and (low not in cased or freq > cased[low][1]):
            cased[low] = (word, freq)
    return scowl, aosp, offensive, {k: v[0] for k, v in cased.items()}


def main():
    ensure_sources()
    scowl, aosp, offensive, cased = load_spelling_filter()
    uni_order, uni = [], {}
    with open(os.path.join(CACHE, "count_1w.txt"), encoding="utf8") as f:
        for line in f:
            w, c = line.rstrip("\n").split("\t")
            uni[w] = int(c)
            uni_order.append(w)
    bi = {}
    with open(os.path.join(CACHE, "count_2w.txt"), encoding="utf8") as f:
        for line in f:
            k, c = line.rstrip("\n").split("\t")
            bi[k] = int(c)

    vocab = {}
    for w in uni_order:
        if len(vocab) >= MAX_WORDS:
            break
        if not (w.isascii() and w.isalpha()):
            continue
        if len(w) == 1 and w not in ("a", "i"):
            continue
        if w in offensive:
            continue
        known = w in scowl or w in aosp or w in SLANG
        if len(w) == 2 and not (w in aosp or w in SLANG):
            continue
        if not known:
            continue
        vocab[w] = uni[w]

    for w in sorted(scowl | aosp):
        if "'" not in w:
            continue
        a, _, b = w.partition("'")
        keep = w in APOSTROPHE_S or w.endswith(CONTRACTION_SUFFIXES)
        if not keep or not a.isalpha() or not (a + b).isascii():
            continue
        stripped = a + b
        count = bi.get(a + " " + b, 0)
        if w not in APOSTROPHE_S:
            count = max(count, uni.get(stripped, 0))
        vocab[w] = max(count, 1_000_000)

    ranked = sorted(vocab.items(), key=lambda kv: -kv[1])
    rank = {w: i for i, (w, _) in enumerate(ranked)}

    nxt = {}
    for key, count in bi.items():
        a, _, b = key.partition(" ")
        if a not in rank or b not in rank or rank[a] >= MAX_PREV_RANK or a == b:
            continue
        nxt.setdefault(a, []).append((count, b))

    os.makedirs(ASSETS, exist_ok=True)
    with gzip.open(os.path.join(ASSETS, "english_lm.dict"), "wt", encoding="utf8", newline="\n") as f:
        for w, c in ranked:
            line = "%s\t%d" % (w, round(math.log(c) * 10))
            if w in cased and cased[w] != w:
                line += "\t" + cased[w]
            f.write(line + "\n")
    with gzip.open(os.path.join(ASSETS, "english_bigrams.dict"), "wt", encoding="utf8", newline="\n") as f:
        for a in sorted(nxt):
            best = [b for _, b in sorted(nxt[a], reverse=True)[:MAX_NEXT]]
            f.write("%s\t%s\n" % (a, " ".join(best)))
    print("words:", len(ranked), "bigram prevs:", len(nxt))


if __name__ == "__main__":
    sys.exit(main())
