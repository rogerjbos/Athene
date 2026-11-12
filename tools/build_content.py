#!/usr/bin/env python3
"""Build app/src/main/assets/content.db from free-licensed sources.

Per language:
  * word frequencies   hermitdave/FrequencyWords (OpenSubtitles 2018), CC BY-SA 4.0
  * glosses, lemmas    English Wiktionary via kaikki.org, CC BY-SA 4.0
  * sentences          Tatoeba, CC BY 2.0 FR
  * starter phrases    tools/phrases.json (from the first version (Owlingo))

Japanese is built differently, because it is written without spaces:
  * sentences split into dictionary words   Tatoeba's Japanese indices (Tanaka corpus), CC BY 2.0 FR
  * readings in kana                        Tatoeba transcriptions
  * meanings                                JMdict (EDRDG) via jmdict-simplified, CC BY-SA 4.0

A course is a frequency-ordered list of dictionary words (lemmas), cut into lessons.
Each lesson also gets Tatoeba sentences made only of words taught so far.

Downloads are cached in tools/.cache/. The table layout must match the Room
entities in app/src/main/java/io/github/athene/data/Content.kt.

Usage: tools/build_content.py [lang ...]     (default: every language in LANGS)
"""
import bz2
import gzip
import json
import re
import sqlite3
import sys
import tarfile
import zipfile
import unicodedata
import urllib.request
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / "tools" / ".cache"
OUT = ROOT / "app" / "src" / "main" / "assets" / "content.db"
DB_VERSION = 3  # keep in step with ContentDatabase.version in data/Content.kt
USER_AGENT = "content-build-script"

COURSE_WORDS = 1500
WORDS_PER_LESSON = 6
LESSONS_PER_UNIT = 5
SENTENCES_PER_LESSON = 4

# elide: what a word cut short by an apostrophe stands for ("l'ami" is "le" + "ami").
# capital_nouns: nouns are written with a capital, so a capital mid-sentence is not a proper name.
LANGS = {
    "es": dict(name="Spanish", native="Español", flag="🇪🇸", locale="es-ES", tatoeba="spa", kaikki="Spanish"),
    "fr": dict(name="French", native="Français", flag="🇫🇷", locale="fr-FR", tatoeba="fra", kaikki="French",
               elide={"l": "le", "j": "je", "d": "de", "qu": "que", "n": "ne", "c": "ce", "s": "se", "m": "me",
                      "t": "te", "jusqu": "jusque", "lorsqu": "lorsque", "puisqu": "puisque", "quelqu": "quelque"},
               free={"t"}),
    "de": dict(name="German", native="Deutsch", flag="🇩🇪", locale="de-DE", tatoeba="deu", kaikki="German",
               capital_nouns=True),
    "nl": dict(name="Dutch", native="Nederlands", flag="🇳🇱", locale="nl-NL", tatoeba="nld", kaikki="Dutch",
               elide={"z": "zo", "m": "mijn", "'t": "het"}),
    "it": dict(name="Italian", native="Italiano", flag="🇮🇹", locale="it-IT", tatoeba="ita", kaikki="Italian",
               elide={"l": "lo", "un": "una", "dell": "dello", "all": "allo", "nell": "nello", "dall": "dallo",
                      "sull": "sullo", "c": "ci", "d": "di", "quest": "questo", "quell": "quello", "com": "come",
                      "dov": "dove", "m": "mi", "t": "ti", "s": "si", "n": "ne", "v": "vi", "bell": "bello",
                      "buon": "buono", "sant": "santo", "anch": "anche", "cos": "cosa", "po": "poco"}),
    "ja": dict(name="Japanese", native="日本語", flag="🇯🇵", locale="ja-JP", tatoeba="jpn"),
}

SCHEMA = """
CREATE TABLE language (
    code TEXT NOT NULL, name TEXT NOT NULL, nativeName TEXT NOT NULL, flag TEXT NOT NULL,
    locale TEXT NOT NULL, PRIMARY KEY(code));
CREATE TABLE item (
    id TEXT NOT NULL, lang TEXT NOT NULL, kind TEXT NOT NULL, text TEXT NOT NULL, reading TEXT,
    meaning TEXT NOT NULL, altMeanings TEXT NOT NULL, note TEXT NOT NULL, tokens TEXT NOT NULL,
    PRIMARY KEY(id));
CREATE TABLE unit (
    id TEXT NOT NULL, lang TEXT NOT NULL, ord INTEGER NOT NULL, title TEXT NOT NULL,
    summary TEXT NOT NULL, PRIMARY KEY(id));
CREATE TABLE lesson (
    id TEXT NOT NULL, unitId TEXT NOT NULL, lang TEXT NOT NULL, ord INTEGER NOT NULL,
    summary TEXT NOT NULL, PRIMARY KEY(id));
CREATE TABLE lesson_item (
    lessonId TEXT NOT NULL, ord INTEGER NOT NULL, itemId TEXT NOT NULL, PRIMARY KEY(lessonId, ord));
"""

# ---------------------------------------------------------------- downloads


def fetch(url: str, name: str) -> Path:
    dest = CACHE / name
    if not dest.exists():
        CACHE.mkdir(parents=True, exist_ok=True)
        print(f"downloading {url}")
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        tmp = dest.with_suffix(dest.suffix + ".part")
        with urllib.request.urlopen(request) as response, open(tmp, "wb") as out:
            while chunk := response.read(1 << 20):
                out.write(chunk)
        tmp.replace(dest)
    return dest


# ---------------------------------------------------------------- dictionary

SKIP_POS = {"name", "character", "prefix", "suffix", "symbol", "punct", "interfix", "infix", "proverb", "phrase",
            "prep_phrase", "adv_phrase"}
# A word whose leading senses carry one of these is kept out of the course and out of sentences.
BLOCK_TAGS = {"vulgar", "offensive", "derogatory", "slur", "pejorative"}
# Senses with these are passed over when choosing a gloss.
WEAK_TAGS = {"obsolete", "archaic", "rare", "dated", "misspelling", "abbreviation", "alt-of", "slang", "historical",
             "regional", "dialectal"}
DESCRIPTION = re.compile(
    r"\b(article|pronoun|particle|indicates|indicating|used (to|in|as|with|for|before|after)|expresses|expressing|"
    r"form of|marker|preposition|contraction of|letter|abbreviation|denotes|introduces|placed|followed by)\b", re.I)
POS_LABEL = {"noun": "noun", "verb": "verb", "adj": "adjective", "adv": "adverb", "pron": "pronoun",
             "prep": "preposition", "conj": "conjunction", "det": "determiner", "article": "article",
             "intj": "interjection", "num": "number", "contraction": "contraction", "particle": "particle"}


def clean_gloss(raw: str) -> str | None:
    """A short learner-facing meaning from a Wiktionary gloss, or None when it only describes usage."""
    text = re.sub(r"\([^)]*\)", "", raw)
    text = re.sub(r"\[[^\]]*\]", "", text)
    for part in text.split(";"):
        part = re.sub(r"\s+", " ", part).strip(" .,:")
        if not part or ":" in part or DESCRIPTION.search(part):
            continue
        pieces = [p.strip() for p in part.split(",") if p.strip()]
        short = ", ".join(pieces[:2])
        if 0 < len(short) <= 28 and not short[0].isupper() or short in ("I", "I am"):
            return short
        if 0 < len(pieces[0]) <= 28 and (not pieces[0][0].isupper() or pieces[0].startswith("I ")):
            return pieces[0]
    return None


def load_dictionary(lang: str) -> dict:
    """{"senses": {word: [[pos, gloss, gender], ...]}, "forms": {word: [lemma, ...]}, "blocked": [word, ...],
    "display": {word: Word}}. Keys are lower case; "display" has the spelling of nouns written with a capital."""
    distilled = CACHE / f"{lang}_dict.json"
    if distilled.exists():
        return json.loads(distilled.read_text(encoding="utf-8"))
    name = LANGS[lang]["kaikki"]
    source = fetch(f"https://kaikki.org/dictionary/{name}/kaikki.org-dictionary-{name}.jsonl.gz", f"{lang}_kaikki.jsonl.gz")
    senses, forms, blocked, display = defaultdict(list), defaultdict(list), set(), {}
    capital_senses = defaultdict(list)  # senses of nouns written with a capital, merged in after the rest
    capital_nouns = LANGS[lang].get("capital_nouns", False)
    print(f"{lang}: reading dictionary")
    with gzip.open(source, "rt", encoding="utf-8") as handle:
        for line in handle:
            entry = json.loads(line)
            word, pos = entry["word"], entry.get("pos")
            if pos in SKIP_POS or " " in word:
                continue
            if word != word.lower():
                if not (capital_nouns and pos == "noun" and word == word.lower().capitalize()):
                    continue
                display.setdefault(word.lower(), word)
                word = word.lower()
                bucket = capital_senses
            else:
                bucket = senses
            entry_senses = entry.get("senses", [])
            if any(BLOCK_TAGS & set(s.get("tags", [])) for s in entry_senses[:2]):
                blocked.add(word)
            for sense in entry_senses:
                tags = set(sense.get("tags", []))
                targets = [t["word"].lower() if capital_nouns else t["word"]
                           for t in sense.get("form_of", []) + sense.get("alt_of", []) if t.get("word")]
                if targets:
                    for target in targets:
                        if target != word and target not in forms[word]:
                            forms[word].append(target)
                    continue
                if "form-of" in tags or tags & WEAK_TAGS or tags & BLOCK_TAGS or not sense.get("glosses"):
                    continue
                gloss = clean_gloss(sense["glosses"][-1])
                if gloss and len(bucket[word]) < 6 and all(gloss != g for _, g, _ in bucket[word]):
                    gender = "m" if "masculine" in tags else "f" if "feminine" in tags else "n" if "neuter" in tags else ""
                    bucket[word].append([pos, gloss, gender])
    # "ich" is first of all a pronoun, and only then the noun "das Ich".
    for word, extra in capital_senses.items():
        senses[word].extend(extra)
    result = {"senses": senses, "forms": forms, "blocked": sorted(blocked), "display": display}
    distilled.write_text(json.dumps(result, ensure_ascii=False), encoding="utf-8")
    return json.loads(distilled.read_text(encoding="utf-8"))


def load_overrides(lang: str) -> dict:
    """tools/overrides/<lang>.tsv: word<TAB>gloss[<TAB>part of speech]. A gloss of '-' drops the word and
    '=lemma' files it under a lemma. The part of speech matters where it decides the spelling (German nouns)."""
    path = ROOT / "tools" / "overrides" / f"{lang}.tsv"
    result = {}
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.strip() and not line.startswith("#"):
                fields = [f.strip() for f in line.split("\t")] + [""]
                result[fields[0]] = (fields[1], fields[2])
    return result


# ---------------------------------------------------------------- course words

EDGES = re.compile(r"^[\W_]+|[\W_]+$")


def split_words(text: str) -> list[str]:
    """The words of a text as written, without the punctuation around them."""
    return [w for w in (EDGES.sub("", part) for part in text.split()) if w]


class Course:
    """The ordered word list for one language and what each surface form maps to."""

    def __init__(self, lang: str):
        self.lang = lang
        self.elide = LANGS[lang].get("elide", {})
        self.free = LANGS[lang].get("free", set())
        self.capital_nouns = LANGS[lang].get("capital_nouns", False)
        dictionary = load_dictionary(lang)
        self.spelling = dictionary.get("display", {})
        self.senses, self.forms = dictionary["senses"], dictionary["forms"]
        self.blocked = set(dictionary["blocked"])
        self.overrides = load_overrides(lang)
        for word, (gloss, stated_pos) in self.overrides.items():
            if gloss == "-":
                self.senses.pop(word, None)
                self.forms.pop(word, None)
                self.blocked.add(word)
            elif gloss.startswith("="):
                self.senses.pop(word, None)
                self.forms[word] = [gloss[1:]]
            else:
                existing = self.senses.get(word) or [["", "", ""]]
                # Keep the gender of the sense being replaced, when there is one of the stated kind.
                pos, _, gender = next((e for e in existing if e[0] == stated_pos), existing[0])
                if self.capital_nouns and stated_pos != "noun":
                    # Not a noun unless the override says so: take the first other part of speech on record.
                    pos, gender = next((e[0] for e in existing if e[0] != "noun"), stated_pos), ""
                self.senses[word] = [[pos, gloss, gender]]
                self.blocked.discard(word)

        freq_path = fetch(
            f"https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/{lang}/{lang}_50k.txt",
            f"{lang}_50k.txt")
        counts = defaultdict(int)
        for line in freq_path.read_text(encoding="utf-8").splitlines():
            word, _, count = line.partition(" ")
            if count.isdigit():
                for piece in self.pieces(word):
                    counts[piece] += int(count)

        self.counts = counts
        # How often each dictionary word is seen through forms that can only be its own.
        self.clear = defaultdict(int)
        for word, count in counts.items():
            options = self.candidates(word)
            if options and (len(options) == 1 or options[0] == word):
                self.clear[options[0]] += count

        # The commonest verbs. Their forms win over a noun spelled the same apart from its capital
        # ("habe" is "haben", not "die Habe"), which lower-cased frequency counts cannot tell apart.
        verbs = [w for w in self.clear if any(pos == "verb" for pos, _, _ in self.senses[w])]
        self.common_verbs = set(sorted(verbs, key=lambda w: -self.clear[w])[:40]) if self.capital_nouns else set()

        totals = defaultdict(int)
        for word, count in counts.items():
            lemma = self.lemma_of(word)
            if lemma:
                totals[lemma] += count
        ranked = sorted(totals, key=lambda w: -totals[w])
        if "--explain" in sys.argv:  # which written words put each dictionary word where it is
            sources = defaultdict(list)
            for word, count in sorted(counts.items(), key=lambda kv: -kv[1]):
                sources[self.lemma_of(word)].append(word)
            for word in sys.argv[sys.argv.index("--explain") + 1].split(","):
                print(f"{word}: rank {ranked.index(word) if word in ranked else None}, from {sources.get(word, [])[:8]}")
        pending = [w for w in ranked if w not in self.blocked][:COURSE_WORDS]
        # Two words with the same meaning must not share a lesson (which would the learner pick?),
        # so the later one waits for the next lesson.
        self.words, lesson = [], []
        while pending:
            taken = {self.senses[w][0][1] for w in lesson}
            index = next((i for i, w in enumerate(pending) if self.senses[w][0][1] not in taken), 0)
            lesson.append(pending.pop(index))
            if len(lesson) == WORDS_PER_LESSON or not pending:
                self.words += lesson
                lesson = []
        # Lesson index (1-based, after the phrase unit) at which each word is taught.
        self.level = {w: i // WORDS_PER_LESSON + 1 for i, w in enumerate(self.words)}

    def lemma_of(self, word: str) -> str | None:
        """The dictionary word a surface form is counted under."""
        if word in self.blocked:
            return None
        own = self.senses.get(word)
        if not own and len(word) == 1:
            return None  # "s", "m": abbreviations, not forms of a word
        targets = [t for t in self.forms.get(word, []) if t in self.senses and t not in self.blocked]
        if own and word in self.overrides:
            return word
        if own:
            # "pasa" is a raisin but far more often a form of "pasar"; "casa" stays a house.
            verbs = [t for t in targets if any(pos == "verb" for pos, _, _ in self.senses[t])]
            if verbs and all(pos in ("noun", "intj") for pos, _, _ in own):
                best = max(verbs, key=lambda t: self.clear[t])
                if self.clear[best] > self.counts.get(word, 0) or best in getattr(self, "common_verbs", ()):
                    return best
            return word
        return max(targets, key=lambda t: self.clear[t]) if targets else None

    def pieces(self, word: str) -> list[str]:
        """The dictionary-sized parts of one written word: "l'ami" is le + ami, "peux-tu" is peux + tu."""
        lower = word.lower().replace("’", "'")
        if lower in self.elide and lower.startswith("'"):
            return [self.elide[lower]]
        if lower in self.senses or not re.search(r"['-]", lower):
            return [lower]
        parts = re.split(r"(['-])", lower)
        result = []
        for index in range(0, len(parts), 2):
            part = parts[index]
            cut_short = index + 1 < len(parts) and parts[index + 1] == "'"
            if not part or part in self.free:
                continue
            result.append(self.elide.get(part, part) if cut_short else part)
        return result

    def display(self, word: str) -> str:
        """How a dictionary word is written; German nouns take a capital."""
        if self.capital_nouns and self.senses[word][0][0] == "noun":
            return self.spelling.get(word, word)
        return word

    def is_noun(self, token: str) -> bool:
        return any(pos == "noun" for c in self.candidates(token) for pos, _, _ in self.senses[c])

    def candidates(self, token: str) -> list[str]:
        """Every dictionary word a token could be a form of, itself first."""
        result = [token] if token in self.senses else []
        result += [t for t in self.forms.get(token, []) if t in self.senses and t not in result]
        return [t for t in result if t not in self.blocked]

    def token_level(self, token: str) -> int | None:
        """The lesson after which a token is readable, or None if it never is."""
        if token in self.blocked:
            return None
        levels = [self.level[c] for c in self.candidates(token) if c in self.level]
        return min(levels) if levels else None

    def hint(self, token: str) -> str:
        """What a tapped word shows: its meaning, with the dictionary form when it differs."""
        candidates = self.candidates(token)
        if not candidates or token in self.blocked:
            return ""
        taught = [c for c in candidates if c in self.level]
        likely = self.lemma_of(token)
        best = (token if token in taught else likely if likely in taught
                else min(taught, key=lambda c: self.level[c]) if taught else candidates[0])
        gloss = self.senses[best][0][1]
        return gloss if best == token else f"{best} · {gloss}"

    def note(self, word: str) -> str:
        pos, _, gender = self.senses[word][0]
        label = POS_LABEL.get(pos, "")
        if pos == "noun" and gender:
            label += {"m": ", masculine", "f": ", feminine", "n": ", neuter"}[gender]
        return label


# ---------------------------------------------------------------- sentences

NAMES = {"tom", "mary", "maría"}
SENTENCE_CHARS = re.compile(r"^[^\W\d_][^\d_\"“”«»:;()\[\]/…*]*[.!?]$|^[¿¡][^\d_\"“”«»:;()\[\]/…*]*[.!?]$")


def read_tsv(path: Path):
    with bz2.open(path, "rt", encoding="utf-8") as handle:
        for line in handle:
            yield line.rstrip("\n").split("\t")


def fold(text: str) -> str:
    text = unicodedata.normalize("NFD", text.lower())
    return re.sub(r"[^a-z0-9 ]", "", "".join(c for c in text if unicodedata.category(c) != "Mn"))


def load_sentences(course: Course) -> dict[int, list]:
    """{lesson level: [(id, text, words, [english, ...], has_name, inflected words), ...]} the course can read."""
    code = LANGS[course.lang]["tatoeba"]
    base = "https://downloads.tatoeba.org/exports/per_language"
    target = fetch(f"{base}/{code}/{code}_sentences.tsv.bz2", f"{code}_sentences.tsv.bz2")
    links = fetch(f"{base}/{code}/{code}-eng_links.tsv.bz2", f"{code}-eng_links.tsv.bz2")
    english = fetch(f"{base}/eng/eng_sentences.tsv.bz2", "eng_sentences.tsv.bz2")

    readable = {}
    for row in read_tsv(target):
        if len(row) < 3:
            continue
        sid, text = int(row[0]), row[2].strip()
        if len(text) > 60 or not SENTENCE_CHARS.match(text):
            continue
        words = split_words(text)
        if not 3 <= len(words) <= 8 or len({w.lower() for w in words}) < 3:
            continue
        level, has_name, ok, inflected = 0, False, True, 0
        for index, word in enumerate(words):
            lower = word.lower()
            if lower in NAMES and word[0].isupper():
                has_name = True
                continue
            if index > 0 and word[0].isupper() and not (course.capital_nouns and course.is_noun(lower)):
                ok = False  # a proper noun the course does not teach
                break
            for piece in course.pieces(word):
                piece_level = course.token_level(piece)
                if piece_level is None:
                    ok = False
                    break
                level = max(level, piece_level)
                inflected += piece not in course.level
            if not ok:
                break
        if ok and level > 0:
            readable[sid] = (text, words, level, has_name, inflected)

    wanted = defaultdict(list)
    for row in read_tsv(links):
        if len(row) == 2 and int(row[0]) in readable:
            wanted[int(row[1])].append(int(row[0]))
    translations = defaultdict(list)
    for row in read_tsv(english):
        if len(row) >= 3 and int(row[0]) in wanted:
            text = row[2].strip()
            if len(text) <= 70 and not re.search(r"[\d\"“”:;()\[\]]", text):
                for sid in wanted[int(row[0])]:
                    translations[sid].append(text)

    by_level, seen = defaultdict(list), set()
    for sid in sorted(readable):
        text, words, level, has_name, inflected = readable[sid]
        key = fold(text)
        if sid not in translations or key in seen:
            continue
        seen.add(key)
        unique = list(dict.fromkeys(translations[sid]))
        by_level[level].append((sid, text, words, unique[:6], has_name, inflected))
    return by_level


def pick_sentences(pool: list, count: int) -> list:
    """Up to `count` varied sentences from the pool, removing them from it."""
    pool.sort(key=lambda s: (s[5], abs(len(s[2]) - 5), s[0]))
    chosen, openers, names = [], set(), 0
    for sentence in list(pool):
        if len(chosen) == count:
            break
        opener = " ".join((w if isinstance(w, str) else w[0]).lower() for w in sentence[2][:2])
        if opener in openers or (sentence[4] and names >= 1):
            continue
        openers.add(opener)
        names += sentence[4]
        chosen.append(sentence)
        pool.remove(sentence)
    return chosen


# ---------------------------------------------------------------- database


def token_field(course: Course, text: str) -> str:
    """One 'word<TAB>hint' line per written word of a sentence or phrase."""
    lines = []
    for word in split_words(text):
        if word.lower() in NAMES and word[0].isupper():
            hint = "(name)"
        else:
            hint = " + ".join(h for h in (course.hint(piece) for piece in course.pieces(word)) if h)
        lines.append(f"{word}\t{hint}")
    return "\n".join(lines)


def build_language(db: sqlite3.Connection, lang: str):
    if lang == "ja":
        return build_japanese(db)
    config = LANGS[lang]
    course = Course(lang)
    sentences = load_sentences(course)
    db.execute("INSERT INTO language VALUES (?,?,?,?,?)",
               (lang, config["name"], config["native"], config["flag"], config["locale"]))

    lesson_ord = 0

    def add_lesson(unit_id: str, number: int, summary: str, item_ids: list[str]):
        nonlocal lesson_ord
        lesson_ord += 1
        lesson_id = f"{unit_id}-{number}"
        db.execute("INSERT INTO lesson VALUES (?,?,?,?,?)", (lesson_id, unit_id, lang, lesson_ord, summary))
        db.executemany("INSERT INTO lesson_item VALUES (?,?,?)",
                       [(lesson_id, i, item_id) for i, item_id in enumerate(item_ids)])

    # Unit 0: starter phrases.
    decks = json.loads((ROOT / "tools" / "phrases.json").read_text(encoding="utf-8"))[lang]
    unit_id = f"{lang}-0"
    db.execute("INSERT INTO unit VALUES (?,?,?,?,?)",
               (unit_id, lang, 0, "Survival phrases", " · ".join(d["title"] for d in decks)))
    for deck_number, deck in enumerate(decks, start=1):
        ids = []
        for index, phrase in enumerate(deck["phrases"], start=1):
            item_id = f"{lang}:p:{deck_number}-{index}"
            db.execute("INSERT INTO item VALUES (?,?,?,?,?,?,?,?,?)",
                       (item_id, lang, "phrase", phrase["text"], None, phrase["meaning"], "", "",
                        token_field(course, phrase["text"])))
            ids.append(item_id)
        add_lesson(unit_id, deck_number, deck["title"], ids)

    # Frequency units.
    lessons = [course.words[i:i + WORDS_PER_LESSON] for i in range(0, len(course.words), WORDS_PER_LESSON)]
    leftovers, sentence_count = [], 0
    for unit_index in range(0, len(lessons), LESSONS_PER_UNIT):
        unit_number = unit_index // LESSONS_PER_UNIT + 1
        unit_id = f"{lang}-{unit_number}"
        unit_lessons = lessons[unit_index:unit_index + LESSONS_PER_UNIT]
        preview = [course.display(w) for lesson in unit_lessons for w in lesson[:1]]
        db.execute("INSERT INTO unit VALUES (?,?,?,?,?)",
                   (unit_id, lang, unit_number, f"Unit {unit_number}", " · ".join(preview)))
        for number, words in enumerate(unit_lessons, start=1):
            level = unit_index + number
            ids = []
            for word in words:
                item_id = f"{lang}:w:{word}"
                glosses = [g for _, g, _ in course.senses[word]]
                db.execute("INSERT INTO item VALUES (?,?,?,?,?,?,?,?,?)",
                           (item_id, lang, "word", course.display(word), None, glosses[0], "\n".join(glosses[1:4]),
                            course.note(word), ""))
                ids.append(item_id)
            pool = sentences.get(level, [])
            chosen = pick_sentences(pool, SENTENCES_PER_LESSON)
            if len(chosen) < SENTENCES_PER_LESSON:
                chosen += pick_sentences(leftovers, SENTENCES_PER_LESSON - len(chosen))
            leftovers = (pool[:20] + leftovers)[:200]
            for sid, text, _, english, _, _ in chosen:
                item_id = f"{lang}:s:{sid}"
                db.execute("INSERT INTO item VALUES (?,?,?,?,?,?,?,?,?)",
                           (item_id, lang, "sentence", text, None, english[0], "\n".join(english[1:]), "",
                            token_field(course, text)))
                ids.append(item_id)
            sentence_count += len(chosen)
            add_lesson(unit_id, number, " · ".join(course.display(w) for w in words), ids)
    print(f"{lang}: {len(course.words)} words, {sentence_count} sentences, {lesson_ord} lessons")


# ---------------------------------------------------------------- Japanese

JMDICT = "https://github.com/scriptin/jmdict-simplified/releases/download/3.6.2%2B20261109214828/jmdict-eng-3.6.2%2B20261109214828.json.zip"
JA_NAMES = {"トム": "Tom", "メアリー": "Mary"}
JA_POS = {"n": "noun", "pn": "pronoun", "prt": "particle", "adv": "adverb", "adj-i": "adjective", "adj-na": "adjective",
          "adj-no": "adjective", "int": "interjection", "conj": "conjunction", "exp": "expression", "ctr": "counter",
          "cop": "copula", "suf": "suffix", "pref": "prefix"}
JA_BLOCK = {"vulg", "derog", "X", "arch", "obs", "rare"}
INDEX_TOKEN = re.compile(r"^([^(\[{~|]+)(?:\|\d+)?(?:\(([^)]*)\))?(?:\[(\d+)\])?(?:\{([^}]*)\})?~?$")
HIRAGANA = ("あa いi うu えe おo かka きki くku けke こko さsa しshi すsu せse そso たta ちchi つtsu てte とto "
            "なna にni ぬnu ねne のno はha ひhi ふfu へhe ほho まma みmi むmu めme もmo やya ゆyu よyo "
            "らra りri るru れre ろro わwa をwo んn")
KATAKANA = ("アa イi ウu エe オo カka キki クku ケke コko サsa シshi スsu セse ソso タta チchi ツtsu テte トto "
            "ナna ニni ヌnu ネne ノno ハha ヒhi フfu ヘhe ホho マma ミmi ムmu メme モmo ヤya ユyu ヨyo "
            "ラra リri ルru レre ロro ワwa ヲwo ンn")


def ja_gloss(sense: dict) -> str | None:
    """A short meaning from one JMdict sense."""
    texts = []
    for gloss in sense["gloss"]:
        text = re.sub(r"\s+", " ", re.sub(r"\([^)]*\)", "", gloss["text"])).strip(" .,")
        if text and len(text) <= 28 and text not in texts:
            texts.append(text)
    if not texts:
        return None
    pair = ", ".join(texts[:2])
    return pair if len(pair) <= 30 else texts[0]


class JapaneseDictionary:
    """JMdict, looked up the way the Tatoeba index names words: spelling, then optional reading and sense number."""

    def __init__(self):
        path = fetch(JMDICT, "jmdict-eng.json.zip")
        with zipfile.ZipFile(path) as archive:
            words = json.load(archive.open(archive.namelist()[0]))["words"]
        self.by_form = defaultdict(list)
        for order, word in enumerate(words):
            word["order"] = order
            for form in word["kanji"] + word["kana"]:
                self.by_form[form["text"]].append(word)

    def entry(self, headword: str, reading: str | None) -> dict | None:
        def score(word):
            kanji = {k["text"]: k for k in word["kanji"]}
            kana = {k["text"]: k for k in word["kana"]}
            form = kanji.get(headword) or kana[headword]
            written_in_kana = headword in kana and (not kanji or "uk" in word["sense"][0]["misc"])
            return (reading is None or reading in kana, form["common"], headword in kanji or written_in_kana,
                    -word["order"])
        candidates = self.by_form.get(headword, [])
        return max(candidates, key=score) if candidates else None

    def describe(self, headword: str, reading: str | None, sense_number: int | None):
        """(spelling, reading or None, meaning, part of speech) for a word, or None when it should not be taught.
        Words normally written in kana are spelled that way, whatever the index uses."""
        word = self.entry(headword, reading)
        if word is None:
            return None
        senses = word["sense"]
        sense = senses[sense_number - 1] if sense_number and sense_number <= len(senses) else senses[0]
        if JA_BLOCK & set(senses[0]["misc"]):
            return None
        gloss = ja_gloss(sense) or ja_gloss(senses[0])
        if gloss is None:
            return None
        if any(k["text"] == headword for k in word["kana"]):
            kana = None  # already written in kana
        else:
            kana = reading if reading and not reading.startswith("#") else next(
                (k["text"] for k in word["kana"] if "*" in k["appliesToKanji"] or headword in k["appliesToKanji"]), None)
        pos = sense["partOfSpeech"][0] if sense["partOfSpeech"] else ""
        label = JA_POS.get(pos, "verb" if pos.startswith("v") else "auxiliary" if pos.startswith("aux") else "")
        if kana and "uk" in sense["misc"]:
            return kana, None, gloss, label
        return headword, kana, gloss, label


def ja_segments(text: str, tokens: list) -> list | None:
    """The sentence cut into [surface, word key or None] pieces, or None if the index does not cover it."""
    pieces, position = [], 0
    for key, surface in tokens:
        found = text.find(surface, position)
        if found < 0:
            return None
        gap = re.sub(r"[\s、。！？!?「」『』・…]", "", text[position:found])
        if gap:
            if gap not in JA_NAMES:
                return None
            pieces.append((gap, None))
        pieces.append((surface, key))
        position = found + len(surface)
    rest = re.sub(r"[\s、。！？!?「」『』・…]", "", text[position:])
    if rest:
        if rest not in JA_NAMES:
            return None
        pieces.append((rest, None))
    return pieces


def build_japanese(db: sqlite3.Connection):
    lang, config = "ja", LANGS["ja"]
    base = "https://downloads.tatoeba.org/exports"
    index_path = fetch(f"{base}/jpn_indices.tar.bz2", "jpn_indices.tar.bz2")
    sentence_path = fetch(f"{base}/per_language/jpn/jpn_sentences.tsv.bz2", "jpn_sentences.tsv.bz2")
    reading_path = fetch(f"{base}/per_language/jpn/jpn_transcriptions.tsv.bz2", "jpn_transcriptions.tsv.bz2")
    link_path = fetch(f"{base}/per_language/jpn/jpn-eng_links.tsv.bz2", "jpn-eng_links.tsv.bz2")
    english_path = fetch(f"{base}/per_language/eng/eng_sentences.tsv.bz2", "eng_sentences.tsv.bz2")
    dictionary = JapaneseDictionary()
    overrides = load_overrides(lang)
    print("ja: reading index")

    # Every indexed sentence as [(word key, surface)], and how often each word occurs.
    indexed, counts, info = {}, defaultdict(int), {}
    with tarfile.open(index_path) as archive:
        handle = archive.extractfile(archive.getmembers()[0])
        for raw in handle:
            fields = raw.decode("utf-8").rstrip("\n").split("\t")
            if len(fields) != 3:
                continue
            tokens = []
            for token in fields[2].split():
                match = INDEX_TOKEN.match(token)
                if not match:
                    tokens = None
                    break
                headword, reading, sense, surface = match.groups()
                key = headword if not reading or reading.startswith("#") else f"{headword}|{reading}"
                if key not in info:
                    # For Japanese the third override column is the reading.
                    gloss, kana_override = overrides.get(headword, ("", ""))
                    described = dictionary.describe(headword, reading, int(sense) if sense else None)
                    if gloss == "-" or (described is None and not gloss):
                        info[key] = None
                    else:
                        spelling, kana, meaning, label = described or (headword, None, "", "")
                        if kana_override:
                            spelling, kana = headword, kana_override
                        info[key] = (spelling, kana, gloss or meaning, label, headword)
                counts[key] += 1
                tokens.append((key, surface or headword))
            if tokens:
                indexed[int(fields[0])] = (int(fields[1]), tokens)

    ranked = [k for k in sorted(counts, key=lambda k: -counts[k]) if info[k] is not None]
    pending, words, lesson = ranked[:COURSE_WORDS], [], []
    while pending:  # as for the other languages: no two words with one meaning in a lesson
        taken = {info[k][2] for k in lesson}
        position = next((i for i, k in enumerate(pending) if info[k][2] not in taken), 0)
        lesson.append(pending.pop(position))
        if len(lesson) == WORDS_PER_LESSON or not pending:
            words += lesson
            lesson = []
    level = {k: i // WORDS_PER_LESSON + 1 for i, k in enumerate(words)}

    def hint(key: str, surface: str) -> str:
        spelling, kana, meaning, _, headword = info[key]
        if surface in (spelling, headword):
            return f"{kana} · {meaning}" if kana and surface != kana else meaning
        return f"{spelling}（{kana}） · {meaning}" if kana else f"{spelling} · {meaning}"

    texts = {int(r[0]): r[2].strip() for r in read_tsv(sentence_path) if len(r) >= 3 and int(r[0]) in indexed}
    by_level, seen = defaultdict(list), set()
    for sid, (_, tokens) in indexed.items():
        text = texts.get(sid, "")
        if not 4 <= len(text) <= 24 or text in seen or re.search(r"[A-Za-z0-9０-９\"“”（）()]", text):
            continue
        if any(info[k] is None or k not in level for k, _ in tokens):
            continue
        pieces = ja_segments(text, tokens)
        if pieces is None or not 3 <= len(pieces) <= 9:
            continue
        seen.add(text)
        sentence_level = max(level[k] for k, _ in tokens)
        inflected = sum(1 for surface, key in pieces if key and surface not in (info[key][0], info[key][4]))
        has_name = any(key is None for _, key in pieces)
        by_level[sentence_level].append([sid, text, pieces, [], has_name, inflected])

    wanted = {s[0]: s for bucket in by_level.values() for s in bucket}
    readings = {}
    for row in read_tsv(reading_path):
        if len(row) >= 5 and int(row[0]) in wanted:
            readings[int(row[0])] = re.sub(r"\[[^|\]]*\|([^\]]*)\]", lambda m: m.group(1).replace("|", ""), row[4])
    english_ids = defaultdict(list)
    for sid in wanted:
        english_ids[indexed[sid][0]].append(sid)  # the translation the index was made against comes first
    for row in read_tsv(link_path):
        if len(row) == 2 and int(row[0]) in wanted and int(row[0]) not in english_ids[int(row[1])]:
            english_ids[int(row[1])].append(int(row[0]))
    primary = {sid: indexed[sid][0] for sid in wanted}
    for row in read_tsv(english_path):
        if len(row) >= 3 and int(row[0]) in english_ids:
            text = row[2].strip()
            if len(text) <= 70 and not re.search(r"[\d\"“”:;()\[\]]", text):
                for sid in english_ids[int(row[0])]:
                    translations = wanted[sid][3]
                    if primary[sid] == int(row[0]):
                        translations.insert(0, text)
                    elif text not in translations and len(translations) < 6:
                        translations.append(text)
    for bucket in by_level.values():
        bucket[:] = [s for s in bucket if s[3]]

    db.execute("INSERT INTO language VALUES (?,?,?,?,?)",
               (lang, config["name"], config["native"], config["flag"], config["locale"]))
    lesson_ord = 0

    def add_lesson(unit_id: str, number: int, summary: str, item_ids: list[str]):
        nonlocal lesson_ord
        lesson_ord += 1
        lesson_id = f"{unit_id}-{number}"
        db.execute("INSERT INTO lesson VALUES (?,?,?,?,?)", (lesson_id, unit_id, lang, lesson_ord, summary))
        db.executemany("INSERT INTO lesson_item VALUES (?,?,?)",
                       [(lesson_id, i, item_id) for i, item_id in enumerate(item_ids)])

    # Unit 0: starter phrases, taught whole.
    decks = json.loads((ROOT / "tools" / "phrases.json").read_text(encoding="utf-8"))[lang]
    db.execute("INSERT INTO unit VALUES (?,?,?,?,?)",
               ("ja-0", lang, 0, "Survival phrases", " · ".join(d["title"] for d in decks)))
    for deck_number, deck in enumerate(decks, start=1):
        ids = []
        for number, phrase in enumerate(deck["phrases"], start=1):
            item_id = f"ja:p:{deck_number}-{number}"
            db.execute("INSERT INTO item VALUES (?,?,?,?,?,?,?,?,?)",
                       (item_id, lang, "phrase", phrase["text"], phrase.get("reading"), phrase["meaning"], "", "", ""))
            ids.append(item_id)
        add_lesson("ja-0", deck_number, deck["title"], ids)

    # Units 1 and 2: the two kana syllabaries.
    for unit_number, (title, table) in enumerate((("Hiragana", HIRAGANA), ("Katakana", KATAKANA)), start=1):
        kana = [(entry[0], entry[1:]) for entry in table.split()]
        unit_id = f"ja-{unit_number}"
        db.execute("INSERT INTO unit VALUES (?,?,?,?,?)",
                   (unit_id, lang, unit_number, title, " ".join(k for k, _ in kana[:10])))
        chunks = [kana[i:i + 5] for i in range(0, len(kana), 5)]
        if len(chunks[-1]) < 3:  # too few for a lesson of their own
            chunks[-2:] = [chunks[-2] + chunks[-1]]
        for number, chunk in enumerate(chunks, start=1):
            ids = []
            for character, sound in chunk:
                item_id = f"ja:k:{character}"
                db.execute("INSERT INTO item VALUES (?,?,?,?,?,?,?,?,?)",
                           (item_id, lang, "word", character, None, sound, "", title.lower(), ""))
                ids.append(item_id)
            add_lesson(unit_id, number, " ".join(k for k, _ in chunk), ids)

    # Frequency units.
    lessons = [words[i:i + WORDS_PER_LESSON] for i in range(0, len(words), WORDS_PER_LESSON)]
    leftovers, sentence_count = [], 0
    for unit_index in range(0, len(lessons), LESSONS_PER_UNIT):
        unit_number = unit_index // LESSONS_PER_UNIT + 3
        unit_id = f"ja-{unit_number}"
        unit_lessons = lessons[unit_index:unit_index + LESSONS_PER_UNIT]
        db.execute("INSERT INTO unit VALUES (?,?,?,?,?)",
                   (unit_id, lang, unit_number, f"Unit {unit_number - 2}",
                    " · ".join(info[lesson[0]][0] for lesson in unit_lessons)))
        for number, keys in enumerate(unit_lessons, start=1):
            ids = []
            for key in keys:
                headword, kana, meaning, label, _ = info[key]
                item_id = f"ja:w:{key}"
                db.execute("INSERT INTO item VALUES (?,?,?,?,?,?,?,?,?)",
                           (item_id, lang, "word", headword, kana, meaning, "", label, ""))
                ids.append(item_id)
            pool = by_level.get(unit_index + number, [])
            chosen = pick_sentences(pool, SENTENCES_PER_LESSON)
            if len(chosen) < SENTENCES_PER_LESSON:
                chosen += pick_sentences(leftovers, SENTENCES_PER_LESSON - len(chosen))
            leftovers = (pool[:20] + leftovers)[:200]
            for sid, text, pieces, english, _, _ in chosen:
                item_id = f"ja:s:{sid}"
                lines = [f"{surface}\t{hint(key, surface) if key else '(name)'}" for surface, key in pieces]
                db.execute("INSERT INTO item VALUES (?,?,?,?,?,?,?,?,?)",
                           (item_id, lang, "sentence", text, readings.get(sid), english[0], "\n".join(english[1:]), "",
                            "\n".join(lines)))
                ids.append(item_id)
            sentence_count += len(chosen)
            add_lesson(unit_id, number, " · ".join(info[k][0] for k in keys), ids)
    print(f"ja: {len(words)} words, {sentence_count} sentences, {lesson_ord} lessons")


def main():
    langs = [a for a in sys.argv[1:] if a in LANGS] or list(LANGS)
    tmp = OUT.with_suffix(".db.tmp")
    tmp.unlink(missing_ok=True)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(tmp)
    db.executescript(SCHEMA)
    for lang in langs:
        build_language(db, lang)
    db.execute(f"PRAGMA user_version = {DB_VERSION}")
    db.commit()
    db.execute("VACUUM")
    db.close()
    tmp.replace(OUT)
    print(f"wrote {OUT} ({OUT.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
