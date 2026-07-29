#!/usr/bin/env python3
#
# Compares two KFusion trajectory tables line by line (e.g. the OpenCL reference against a CUDA run,
# or a baseline against an optimized CUDA run).
#
# usage: compareRuns.py <reference.table> <candidate.table> [--pos-tol 1e-6] [--quiet]
#
# Exit status is 0 when every frame matches within the tolerance and the tracked/integrated flags are
# identical, 1 otherwise. Timing columns are reported but never gate the comparison.

import argparse
import math
import sys

POSITION_COLUMNS = ("X", "Y", "Z")
FLAG_COLUMNS = ("tracked", "integrated")
TIMING_COLUMNS = ("acquisition", "preprocessing", "tracking", "integration", "raycasting", "rendering", "computation", "total")


def read_table(path):
    rows = []
    header = None
    with open(path) as handle:
        for line in handle:
            line = line.rstrip("\n")
            if not line.strip():
                continue
            fields = [f.strip() for f in line.split("\t")]
            if header is None:
                if fields[0] != "frame":
                    continue
                header = fields
                continue
            if not fields[0].isdigit():
                continue
            rows.append(dict(zip(header, fields)))
    if header is None:
        sys.exit("no trajectory header found in " + path)
    return header, rows


def mean(values):
    return sum(values) / len(values) if values else float("nan")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("reference")
    parser.add_argument("candidate")
    parser.add_argument("--pos-tol", type=float, default=1e-6,
                        help="max allowed absolute per-axis position difference in metres (default 1e-6)")
    parser.add_argument("--quiet", action="store_true", help="only print the verdict")
    args = parser.parse_args()

    _, reference = read_table(args.reference)
    _, candidate = read_table(args.candidate)

    print("reference : %s (%d frames)" % (args.reference, len(reference)))
    print("candidate : %s (%d frames)" % (args.candidate, len(candidate)))

    frames = min(len(reference), len(candidate))
    if len(reference) != len(candidate):
        print("WARNING: frame count differs, comparing the first %d frames" % frames)
    if frames == 0:
        sys.exit("nothing to compare")

    worst = {column: (0.0, -1) for column in POSITION_COLUMNS}
    worst_distance = (0.0, -1)
    flag_mismatches = []
    first_divergence = None

    for index in range(frames):
        ref, cand = reference[index], candidate[index]
        if ref["frame"] != cand["frame"]:
            print("frame numbering diverges at row %d: %s vs %s" % (index, ref["frame"], cand["frame"]))
            break

        squared = 0.0
        for column in POSITION_COLUMNS:
            delta = abs(float(ref[column]) - float(cand[column]))
            squared += delta * delta
            if delta > worst[column][0]:
                worst[column] = (delta, int(ref["frame"]))
            if delta > args.pos_tol and first_divergence is None:
                first_divergence = (int(ref["frame"]), column, float(ref[column]), float(cand[column]), delta)

        distance = math.sqrt(squared)
        if distance > worst_distance[0]:
            worst_distance = (distance, int(ref["frame"]))

        for column in FLAG_COLUMNS:
            if ref[column] != cand[column]:
                flag_mismatches.append((int(ref["frame"]), column, ref[column], cand[column]))

    if not args.quiet:
        print("\nposition deltas (metres)")
        for column in POSITION_COLUMNS:
            delta, frame = worst[column]
            print("  %s: max %.3e at frame %d" % (column, delta, frame))
        print("  euclidean: max %.3e at frame %d" % worst_distance)

        print("\ntiming means (seconds, reference -> candidate)")
        for column in TIMING_COLUMNS:
            if column not in reference[0]:
                continue
            ref_mean = mean([float(row[column]) for row in reference[:frames]])
            cand_mean = mean([float(row[column]) for row in candidate[:frames]])
            speedup = (ref_mean / cand_mean) if cand_mean else float("nan")
            print("  %-14s %.6f -> %.6f  (%.2fx)" % (column, ref_mean, cand_mean, speedup))

    if flag_mismatches:
        print("\ntracked/integrated mismatches: %d (first 10)" % len(flag_mismatches))
        for frame, column, ref_value, cand_value in flag_mismatches[:10]:
            print("  frame %d: %s %s vs %s" % (frame, column, ref_value, cand_value))

    if first_divergence:
        frame, column, ref_value, cand_value, delta = first_divergence
        print("\nfirst divergence beyond %.1e: frame %d %s %.9f vs %.9f (delta %.3e)" %
              (args.pos_tol, frame, column, ref_value, cand_value, delta))

    ok = (first_divergence is None) and not flag_mismatches and len(reference) == len(candidate)
    print("\nVERDICT: %s (tolerance %.1e m)" % ("MATCH" if ok else "DIFFER", args.pos_tol))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
