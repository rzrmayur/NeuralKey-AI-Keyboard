"""Build the offline dictionary from SCOWL's en_US Hunspell files.
Source: wooorm/dictionaries, dictionaries/en (2020.12.07).
Retain app/src/main/assets/licenses/english-dictionary.txt when redistributing.
Usage: python scripts/prepare_dictionary.py index.dic index.aff output.txt
"""
import gzip
import re
import sys
from pathlib import Path

rules = {'PFX': {}, 'SFX': {}}
cross = {}
for line in Path(sys.argv[2]).read_text().splitlines():
    fields = line.split()
    if len(fields) == 4 and fields[0] in rules:
        cross[(fields[0], fields[1])] = fields[2] == 'Y'
    elif len(fields) == 5 and fields[0] in rules:
        kind, flag, strip, add, condition = fields
        rules[kind].setdefault(flag, []).append((strip, add, condition))

def apply(word, kind, flag):
    for strip, add, condition in rules[kind].get(flag, []):
        pattern = '^' + condition if kind == 'PFX' else condition + '$'
        if re.search(pattern, word):
            base = word
            if strip != '0':
                base = word[len(strip):] if kind == 'PFX' else word[:-len(strip)]
            yield (add if add != '0' else '') + base if kind == 'PFX' else base + (add if add != '0' else '')

words = set()
for line in Path(sys.argv[1]).read_text().splitlines()[1:]:
    word, _, flags = line.partition('/')
    # Exclude proper names, acronyms and NOSUGGEST entries.
    if not re.fullmatch("[a-z]+(?:'[a-z]+)?", word) or '!' in flags:
        continue
    forms = {word}
    for flag in flags:
        forms.update(apply(word, 'PFX', flag))
        forms.update(apply(word, 'SFX', flag))
    for p in flags:
        if cross.get(('PFX', p)):
            for prefixed in apply(word, 'PFX', p):
                for s in flags:
                    if cross.get(('SFX', s)):
                        forms.update(apply(prefixed, 'SFX', s))
    words.update(w for w in forms if 2 <= len(w) <= 32)
payload = ('\n'.join(sorted(words)) + '\n').encode()
Path(sys.argv[3]).write_bytes(gzip.compress(payload, mtime=0) if sys.argv[3].endswith('.gz') else payload)
print(f'{len(words)} dictionary words')
