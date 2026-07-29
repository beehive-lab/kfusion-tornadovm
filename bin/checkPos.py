#!/usr/bin/env python3
# Copyright (c) 2014 University of Edinburgh, Imperial College, University of Manchester.
# Developed in the PAMELA project, EPSRC Programme Grant EP/K008730/1
#
# This code is licensed under the MIT License.
#
# Compares a KFusion benchmark log against an ICL-NUIM ground-truth trajectory and reports the
# absolute trajectory error (ATE) plus per-phase runtime statistics.
#
# usage: checkPos.py <kfusion-log> <livingRoomN.gt.freiburg>

import math
import re
import statistics
import sys

kfusion_log_regex = r"([0-9]+[\s]*)\t"
kfusion_log_regex += 8 * r"([0-9.]+)\t"
kfusion_log_regex += 3 * r"([-0-9.]+)\t"
kfusion_log_regex += r"([01])\s+([01])"

nuim_log_regex = r"([0-9]+)"
nuim_log_regex += 7 * r"\s+([-0-9e.]+)\s*"

if len(sys.argv) != 3:
    print("I need two parameters, the benchmark log file and the original scene camera position file.")
    sys.exit(1)

print("Get KFusion output data.")
framesDropped = 0
validFrames = 0
lastFrame = -1
untracked = -4
lastValid = None
kfusion_traj = []

with open(sys.argv[1], 'r') as fileref:
    data = fileref.read()

lines = data.split("\n")
headers = lines[0].split("\t")
fulldata = {}
if len(headers) == 15 and headers[14] == "":
    del headers[14]
if len(headers) != 14:
    print("Wrong KFusion log file. Expected 14 columns but found " + str(len(headers)))
    sys.exit(1)
for variable in headers:
    fulldata[variable] = []

for line in lines[1:]:
    matching = re.match(kfusion_log_regex, line)
    if not matching:
        break

    dropped = int(matching.group(1)) - lastFrame - 1
    if dropped > 0:
        framesDropped += dropped
        for _ in range(dropped):
            kfusion_traj.append(lastValid)

    kfusion_traj.append((matching.group(10), matching.group(11), matching.group(12), matching.group(13), 1))
    lastValid = (matching.group(10), matching.group(11), matching.group(12), matching.group(13), 0)
    if int(matching.group(13)) == 0:
        untracked += 1
    validFrames += 1
    for elem_idx in range(len(headers)):
        fulldata[headers[elem_idx]].append(float(matching.group(elem_idx + 1)))

    lastFrame = int(matching.group(1))

nuim_traj = []
with open(sys.argv[2], 'r') as fileref:
    data = fileref.read()

for line in data.split("\n"):
    matching = re.match(nuim_log_regex, line)
    if not matching:
        break
    nuim_traj.append((matching.group(2), matching.group(3), matching.group(4)))

working_position = min(len(kfusion_traj), len(nuim_traj))
print("KFusion valid frames " + str(validFrames) + ",  dropped frames: " + str(framesDropped))
print("KFusion result      : " + str(len(kfusion_traj)) + " positions.")
print("NUIM  result        : " + str(len(nuim_traj)) + " positions.")
print("Working position is : " + str(working_position))
print("Untracked frames    : " + str(untracked))
nuim_traj = nuim_traj[0:working_position]
kfusion_traj = kfusion_traj[0:working_position]

if working_position == 0:
    print("No comparable positions found.")
    sys.exit(1)

print("Shift KFusion trajectory...")

first = nuim_traj[0]
fulldata["ATE"] = []
# ATE_wrt_kfusion ignores frames that were dropped when not running in process-every-frame mode
fulldata["ATE_wrt_kfusion"] = []
distance_since_valid = 0.0
lastValid = nuim_traj[0]

for p in range(working_position):
    kfusion_traj[p] = (float(kfusion_traj[p][0]) + float(first[0]),
                       -(float(kfusion_traj[p][1]) + float(first[1])),
                       float(kfusion_traj[p][2]) + float(first[2]),
                       int(kfusion_traj[p][3]),
                       int(kfusion_traj[p][4]))
    diff = (abs(kfusion_traj[p][0] - float(nuim_traj[p][0])),
            abs(kfusion_traj[p][1] - float(nuim_traj[p][1])),
            abs(kfusion_traj[p][2] - float(nuim_traj[p][2])))
    ate = math.sqrt(diff[0] * diff[0] + diff[1] * diff[1] + diff[2] * diff[2])

    if p == 1:
        lastValid = nuim_traj[p]

    dx = float(nuim_traj[p][0]) - float(lastValid[0])
    dy = float(nuim_traj[p][1]) - float(lastValid[1])
    dz = float(nuim_traj[p][2]) - float(lastValid[2])
    distA = math.sqrt((dx * dx) + (dz * dz))
    dist = math.sqrt((dy * dy) + (distA * distA))
    distance_since_valid += dist
    lastValid = nuim_traj[p]
    if kfusion_traj[p][4] == 1:
        distance_since_valid = 0.0

    if kfusion_traj[p][4] == 1:
        fulldata["ATE_wrt_kfusion"].append(ate)
    fulldata["ATE"].append(ate)

print("\nA detailed statistical analysis is provided.")
print("Runtimes are in seconds and the absolute trajectory error (ATE) is in meters.")
print("The ATE measures accuracy, check this number to see how precise your computation is.")
print("Acceptable values are in the range of few centimeters.")

for variable in sorted(fulldata.keys()):
    if any(token in variable for token in ("X", "Z", "Y", "frame", "tracked", "integrated")):
        continue
    if framesDropped == 0 and variable == "ATE_wrt_kfusion":
        continue
    values = fulldata[variable]
    if not values:
        continue
    print("%20.20s\tMin : %6.6f\tMax : %0.6f\tMean : %0.6f\tTotal : %0.8f" %
          (variable, min(values), max(values), statistics.fmean(values), sum(values)))

ate_rmse = math.sqrt(statistics.fmean([a * a for a in fulldata["ATE"]]))
computation = statistics.fmean(fulldata["computation"]) if fulldata.get("computation") else float('nan')
total = statistics.fmean(fulldata["total"]) if fulldata.get("total") else float('nan')

print("\nATE RMSE            : %0.6f m" % ate_rmse)
print("ATE max             : %0.6f m" % max(fulldata["ATE"]))
print("Mean computation    : %0.6f s (%.1f FPS)" % (computation, 1.0 / computation if computation else 0.0))
print("Mean total          : %0.6f s (%.1f FPS)" % (total, 1.0 / total if total else 0.0))
# machine readable summary for regression gating
print("MRkey:,logfile,ate_mean,ate_rmse,ate_max,computation_mean,total_mean,dropped,untracked")
print("MRdata:,%s,%6.6f,%6.6f,%6.6f,%6.6f,%6.6f,%d,%d" %
      (sys.argv[1], statistics.fmean(fulldata["ATE"]), ate_rmse, max(fulldata["ATE"]), computation, total,
       framesDropped, untracked))
