#!/usr/bin/env python3
"""Generate correlated, realistic demo readings through the public ingest API."""

from __future__ import annotations

import argparse
import json
import math
import random
import signal
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


PROCESS_POINTS = {
    "TEMP-001": ("temperature", "C", 1176.0, 1),
    "TEMP-002": ("temperature", "C", 1182.0, 1),
    "TEMP-003": ("temperature", "C", 1169.0, 1),
    "PRESS-001": ("pressure", "Pa", -18.0, 1),
    "LEVEL-001": ("level", "PCT", 72.6, 2),
    "SPEED-001": ("speed", "M_PER_MIN", 32.4, 2),
    "DIAM-ROD-001": ("diameter", "MM", 8.000, 3),
    "SPEED-003": ("speed", "M_PER_SEC", 23.4, 2),
    "TENSION-003": ("tension", "N", 20.2, 2),
    "DIAM-WIRE-002": ("diameter", "MM", 1.821, 3),
}

ENERGY_UNITS = {
    "EM-E-SITE": ("KWH", "KW"), "EM-E-ROD": ("KWH", "KW"),
    "EM-E-WIRE": ("KWH", "KW"), "EM-E-SMELT": ("KWH", "KW"),
    "EM-E-HOLD": ("KWH", "KW"), "EM-E-DRAW1": ("KWH", "KW"),
    "EM-E-DRAW2": ("KWH", "KW"),
    "EM-W-SITE": ("M3", "M3_PER_H"), "EM-W-ROD": ("M3", "M3_PER_H"),
    "EM-W-WIRE": ("M3", "M3_PER_H"), "EM-W-CAST": ("M3", "M3_PER_H"),
    "EM-W-DRAW": ("M3", "M3_PER_H"),
    "EM-G-SITE": ("NM3", "NM3_PER_H"), "EM-G-ROD": ("NM3", "NM3_PER_H"),
    "EM-G-SMELT": ("NM3", "NM3_PER_H"), "EM-G-HOLD": ("NM3", "NM3_PER_H"),
}

FALLBACK_TOTALS = {
    "EM-E-SITE": 128600.0, "EM-E-ROD": 78600.0, "EM-E-WIRE": 48200.0,
    "EM-E-SMELT": 32600.0, "EM-E-HOLD": 14600.0, "EM-E-DRAW1": 12600.0,
    "EM-E-DRAW2": 11800.0, "EM-W-SITE": 23860.0, "EM-W-ROD": 10260.0,
    "EM-W-WIRE": 13600.0, "EM-W-CAST": 8860.0, "EM-W-DRAW": 9200.0,
    "EM-G-SITE": 638420.0, "EM-G-ROD": 632840.0, "EM-G-SMELT": 398420.0,
    "EM-G-HOLD": 234420.0,
}

STOP = False


def request_json(url: str, payload: dict | None = None, timeout: float = 10.0) -> dict:
    body = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=body, headers={"Content-Type": "application/json; charset=utf-8"})
    with urllib.request.urlopen(req, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def initial_totals(base_url: str, timeout: float) -> dict[str, float]:
    codes = ",".join(ENERGY_UNITS)
    url = f"{base_url}/api/v1/measurements/latest?{urllib.parse.urlencode({'sensorCodes': codes, 'parameterCodes': 'cumulative'})}"
    totals = dict(FALLBACK_TOTALS)
    try:
        response = request_json(url, timeout=timeout)
        for item in response.get("data", {}).get("items", []):
            if item.get("sensorCode") in totals and item.get("value") is not None:
                totals[item["sensorCode"]] = float(item["value"])
    except (OSError, ValueError, KeyError):
        pass
    return totals


def process_values(step: int, rng: random.Random, scenario: str) -> tuple[dict[str, float], str]:
    load = 1.0 + 0.035 * math.sin(step / 8.0) + rng.uniform(-0.006, 0.006)
    values = {
        "TEMP-001": 1176 + (load - 1) * 150 + rng.gauss(0, 1.1),
        "TEMP-002": 1182 + (load - 1) * 170 + rng.gauss(0, 0.9),
        "TEMP-003": 1169 + (load - 1) * 140 + rng.gauss(0, 0.8),
        "PRESS-001": -18 - (load - 1) * 12 + rng.gauss(0, 0.35),
        "LEVEL-001": 72.6 + 1.8 * math.sin(step / 13.0) + rng.gauss(0, 0.18),
        "SPEED-001": 32.4 * load + rng.gauss(0, 0.08),
        "DIAM-ROD-001": 8.000 - (load - 1) * 0.025 + rng.gauss(0, 0.006),
        "SPEED-003": 23.4 * load + rng.gauss(0, 0.07),
        "TENSION-003": 20.2 + (load - 1) * 8 + rng.gauss(0, 0.16),
        "DIAM-WIRE-002": 1.821 + (load - 1) * 0.012 + rng.gauss(0, 0.0015),
    }
    phase = step % 48
    state = "正常生产"
    if scenario == "showcase" and 12 <= phase < 18:
        values["TENSION-003"] = 25.1 + rng.gauss(0, 0.18)
        state = "拉丝张力预警"
    elif scenario == "showcase" and 18 <= phase < 24:
        values["DIAM-WIRE-002"] = 1.838 + rng.gauss(0, 0.001)
        state = "线径报警"
    elif scenario == "showcase" and 24 <= phase < 30:
        values["TENSION-003"] = 22.4 + rng.gauss(0, 0.12)
        values["DIAM-WIRE-002"] = 1.824 + rng.gauss(0, 0.001)
        state = "参数恢复"
    return values, state


def energy_rates(step: int, rng: random.Random) -> dict[str, float]:
    load = 1.0 + 0.045 * math.sin(step / 10.0) + rng.uniform(-0.008, 0.008)
    smelt = 1260 * load + rng.gauss(0, 8)
    hold = 286 * load + rng.gauss(0, 3)
    draw1 = 368 * load + rng.gauss(0, 4)
    draw2 = 300 * load + rng.gauss(0, 4)
    rod = (smelt + hold + 205 * load) * 1.010
    wire = (draw1 + draw2 + 390 * load) * 1.010
    electricity = {
        "EM-E-SMELT": smelt, "EM-E-HOLD": hold, "EM-E-DRAW1": draw1,
        "EM-E-DRAW2": draw2, "EM-E-ROD": rod, "EM-E-WIRE": wire,
        "EM-E-SITE": (rod + wire) * 1.018,
    }
    cast_water = 11.2 * load + rng.gauss(0, 0.08)
    draw_water = 18.6 * load + rng.gauss(0, 0.10)
    rod_water, wire_water = cast_water + 3.3, draw_water + 5.2
    water = {
        "EM-W-CAST": cast_water, "EM-W-DRAW": draw_water, "EM-W-ROD": rod_water,
        "EM-W-WIRE": wire_water, "EM-W-SITE": (rod_water + wire_water) * 1.020,
    }
    gas_smelt = 286 * load + rng.gauss(0, 1.6)
    gas_hold = 140 * load + rng.gauss(0, 1.0)
    gas_rod = (gas_smelt + gas_hold) * 1.010
    gas = {"EM-G-SMELT": gas_smelt, "EM-G-HOLD": gas_hold,
           "EM-G-ROD": gas_rod, "EM-G-SITE": gas_rod * 1.015}
    return {key: max(0.0, value) for key, value in {**electricity, **water, **gas}.items()}


def build_payload(step: int, interval: float, totals: dict[str, float], rng: random.Random,
                  scenario: str) -> tuple[dict, str]:
    now = datetime.now(timezone.utc).astimezone().isoformat(timespec="milliseconds")
    unique = f"{int(time.time() * 1000)}-{step}"
    values, state = process_values(step, rng, scenario)
    readings = []
    for sensor, value in values.items():
        parameter, unit, _, places = PROCESS_POINTS[sensor]
        readings.append({"sourceRecordId": f"demo-{unique}-{sensor}", "sensorCode": sensor,
                         "parameterCode": parameter, "collectTime": now,
                         "valueNum": round(value, places), "sourceUnit": unit, "qualityCode": "GOOD"})
    for sensor, rate in energy_rates(step, rng).items():
        cumulative_unit, instantaneous_unit = ENERGY_UNITS[sensor]
        totals[sensor] += rate * interval / 3600.0
        readings.extend([
            {"sourceRecordId": f"demo-{unique}-{sensor}-c", "sensorCode": sensor,
             "parameterCode": "cumulative", "collectTime": now, "valueNum": round(totals[sensor], 4),
             "sourceUnit": cumulative_unit, "qualityCode": "GOOD"},
            {"sourceRecordId": f"demo-{unique}-{sensor}-i", "sensorCode": sensor,
             "parameterCode": "instantaneous", "collectTime": now, "valueNum": round(rate, 3),
             "sourceUnit": instantaneous_unit, "qualityCode": "GOOD"},
        ])
    return {"sourceSystem": "CUSTOMER_DEMO_GENERATOR", "readings": readings}, state


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Copper Twin realistic demo data generator")
    parser.add_argument("--base-url", default="http://localhost:8080", help="backend URL")
    parser.add_argument("--interval", type=float, default=10.0, help="seconds between batches")
    parser.add_argument("--rounds", type=int, default=0, help="0 means run until Ctrl+C")
    parser.add_argument("--scenario", choices=("normal", "showcase"), default="showcase")
    parser.add_argument("--seed", type=int, default=20260903, help="reproducible random seed")
    parser.add_argument("--timeout", type=float, default=15.0, help="HTTP timeout seconds")
    parser.add_argument("--dry-run", action="store_true", help="print one batch without sending")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.interval <= 0 or args.rounds < 0:
        raise SystemExit("--interval must be > 0 and --rounds must be >= 0")
    base_url = args.base_url.rstrip("/")
    rng = random.Random(args.seed)
    totals = initial_totals(base_url, args.timeout) if not args.dry_run else dict(FALLBACK_TOTALS)
    total_rounds = args.rounds or sys.maxsize
    print(f"Demo generator: {base_url}, scenario={args.scenario}, interval={args.interval:g}s")
    for step in range(total_rounds):
        if STOP:
            break
        payload, state = build_payload(step, args.interval, totals, rng, args.scenario)
        if args.dry_run:
            print(json.dumps(payload, ensure_ascii=False, indent=2))
            return 0
        try:
            response = request_json(f"{base_url}/api/v1/ingest/sensor-readings:batch", payload, args.timeout)
            data = response.get("data", {})
            stamp = datetime.now().astimezone().strftime("%H:%M:%S")
            print(f"[{stamp}] #{step + 1} {state} | accepted={data.get('acceptedCount', 0)} failed={data.get('failedCount', 0)}")
        except (urllib.error.URLError, TimeoutError, ValueError) as exc:
            print(f"Request failed: {exc}; retrying next round", file=sys.stderr)
        if step + 1 < total_rounds and not STOP:
            time.sleep(args.interval)
    print("Demo generator stopped.")
    return 0


def stop_handler(_signum: int, _frame: object) -> None:
    global STOP
    STOP = True


if __name__ == "__main__":
    signal.signal(signal.SIGINT, stop_handler)
    if hasattr(signal, "SIGTERM"):
        signal.signal(signal.SIGTERM, stop_handler)
    raise SystemExit(main())
