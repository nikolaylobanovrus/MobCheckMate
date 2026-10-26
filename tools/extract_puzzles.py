"""Select a balanced set of puzzles from the Lichess puzzle database (CC0) for the offline app.

Input: lichess_db_puzzle.csv.zst from https://database.lichess.org/#puzzles
Output: TSV with id, FEN, moves (UCI, first move is the opponent's), rating, themes.
Needs the `zstandard` module. Deterministic for a given input.

Usage: python3 tools/extract_puzzles.py lichess_db_puzzle.csv.zst androidApp/src/main/assets/puzzles.tsv
"""
import csv
import io
import random
import sys

import zstandard

TARGET = 30_000
MIN_POPULARITY = 85
MAX_DEVIATION = 90
MIN_PLAYS = 500
BUCKET = 100
LOW, HIGH = 400, 3000

# Share of the set per rating bucket: most players are between 800 and 2000.
def weight(bucket_start):
    if bucket_start < 800:
        return 0.6
    if bucket_start < 2000:
        return 1.6
    if bucket_start < 2400:
        return 0.8
    return 0.4


def main(src, dst):
    buckets = {b: [] for b in range(LOW, HIGH, BUCKET)}
    seen = {b: 0 for b in buckets}
    rng = random.Random(2026)
    total_weight = sum(weight(b) for b in buckets)
    quota = {b: int(TARGET * weight(b) / total_weight) for b in buckets}

    with open(src, "rb") as raw:
        reader = csv.DictReader(io.TextIOWrapper(zstandard.ZstdDecompressor().stream_reader(raw), encoding="utf-8"))
        for row in reader:
            rating = int(row["Rating"])
            if not (LOW <= rating < HIGH):
                continue
            if int(row["Popularity"]) < MIN_POPULARITY or int(row["RatingDeviation"]) > MAX_DEVIATION:
                continue
            if int(row["NbPlays"]) < MIN_PLAYS:
                continue
            b = LOW + (rating - LOW) // BUCKET * BUCKET
            seen[b] += 1
            item = (row["PuzzleId"], row["FEN"], row["Moves"], rating, row["Themes"].replace(" ", ","))
            # Reservoir sampling keeps a uniform sample of each bucket in one pass.
            if len(buckets[b]) < quota[b]:
                buckets[b].append(item)
            else:
                j = rng.randrange(seen[b])
                if j < quota[b]:
                    buckets[b][j] = item

    rows = sorted((item for items in buckets.values() for item in items), key=lambda r: (r[3], r[0]))
    with open(dst, "w", encoding="utf-8") as out:
        out.write("# Lichess puzzle database, CC0 1.0. id\tfen\tmoves\trating\tthemes\n")
        for r in rows:
            out.write("\t".join(str(x) for x in r) + "\n")
    print(f"selected {len(rows)} puzzles; candidates per bucket: {sum(seen.values())}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
