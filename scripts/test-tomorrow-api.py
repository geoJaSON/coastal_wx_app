#!/usr/bin/env python3
"""Tomorrow.io API test script.

Hits the same /v4/weather/forecast endpoint the app uses, so you can verify
what's actually being returned for a location and inspect every field.

Usage examples:
  python scripts/test-tomorrow-api.py
  python scripts/test-tomorrow-api.py --api-key "your_key"
  python scripts/test-tomorrow-api.py --location "25.7617,-80.1918"
  python scripts/test-tomorrow-api.py --units imperial --out-file out.json

You can also set TOMORROW_API_KEY in your environment to skip the prompt.
"""

from __future__ import annotations

import argparse
import getpass
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


ENDPOINT = "https://api.tomorrow.io/v4/weather/forecast"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Test the Tomorrow.io forecast endpoint.")
    parser.add_argument("--api-key", dest="api_key", help="Tomorrow.io API key")
    parser.add_argument(
        "--location",
        default="29.3013,-94.7977",
        help='Location query, e.g. "29.3013,-94.7977" (default: Galveston, TX)',
    )
    parser.add_argument(
        "--units",
        choices=("metric", "imperial"),
        default="metric",
        help="Units to request from Tomorrow.io",
    )
    parser.add_argument("--out-file", dest="out_file", help="Path to write the full JSON response")
    return parser.parse_args()


def resolve_api_key(explicit_key: str | None) -> str:
    api_key = os.getenv("TOMORROW_API_KEY")
    if api_key:
        return api_key

    api_key = getpass.getpass("Tomorrow.io API key: ").strip()
    if not api_key:
        print("No API key supplied.", file=sys.stderr)
        raise SystemExit(1)
    return api_key


def build_url(location: str, api_key: str, units: str) -> str:
    query = urllib.parse.urlencode(
        {
            "location": location,
            "apikey": api_key,
            "units": units,
        }
    )
    return f"{ENDPOINT}?{query}"


def fetch_forecast(url: str) -> dict[str, Any]:
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/json",
            "User-Agent": "aeroweather-test-script/1.0",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            charset = response.headers.get_content_charset("utf-8")
            payload = response.read().decode(charset)
    except urllib.error.HTTPError as exc:
        print(f"Request failed: HTTP {exc.code} {exc.reason}", file=sys.stderr)
        try:
            body = exc.read().decode("utf-8", errors="replace")
        except Exception:
            body = ""
        if body:
            print("--- Response body ---", file=sys.stderr)
            print(body, file=sys.stderr)
        raise SystemExit(1) from exc
    except urllib.error.URLError as exc:
        print(f"Request failed: {exc.reason}", file=sys.stderr)
        raise SystemExit(1) from exc

    try:
        data = json.loads(payload)
    except json.JSONDecodeError as exc:
        print(f"Response was not valid JSON: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc

    if not isinstance(data, dict):
        print("Unexpected response shape: expected a JSON object.", file=sys.stderr)
        raise SystemExit(1)

    return data


def print_mapping(title: str, values: dict[str, Any]) -> None:
    print(title)
    if not isinstance(values, dict):
        print("  <missing or non-object values>")
        print()
        return

    for key in sorted(values):
        print(f"  {key}: {values[key]}")
    print()


def main() -> int:
    args = parse_args()
    api_key = resolve_api_key(args.api_key)
    url = build_url(args.location, api_key, args.units)

    # Don't print the full URL because it contains the API key.
    print()
    print(f"Endpoint: {ENDPOINT}")
    print(f"Location: {args.location}")
    print(f"Units:    {args.units}")
    print()

    response = fetch_forecast(url)

    timelines = response.get("timelines") or {}
    minutely = timelines.get("minutely") or []
    hourly = timelines.get("hourly") or []
    daily = timelines.get("daily") or []

    print(f"Minutely entries returned: {len(minutely)}")
    print(f"Hourly entries returned:   {len(hourly)}")
    print(f"Daily entries returned:    {len(daily)}")
    print()

    # Nowcast check — summarise precipitation in the minutely window
    if minutely:
        precip_minutes = [
            m for m in minutely
            if isinstance(m.get("values"), dict)
            and (m["values"].get("rainIntensity") or 0) >= 0.1
        ]
        if precip_minutes:
            first_ts = precip_minutes[0].get("time", "?")
            last_ts  = precip_minutes[-1].get("time", "?")
            print(f"Nowcast: rain detected in {len(precip_minutes)} of {len(minutely)} minutes")
            print(f"  First rain minute : {first_ts}")
            print(f"  Last  rain minute : {last_ts}")
        else:
            print("Nowcast: no precipitation detected in minutely window (banner will not show)")
        print()
    else:
        print("Nowcast: *** minutely array is EMPTY — banner can never fire ***")
        print()

    if hourly:
        first_hour = hourly[0]
        print_mapping(
            f"=== Current conditions (first hourly entry @ {first_hour.get('time')}) ===",
            first_hour.get("values") or {},
        )

    if daily:
        first_day = daily[0]
        print_mapping(
            f"=== Today (first daily entry @ {first_day.get('time')}) ===",
            first_day.get("values") or {},
        )

    if hourly and isinstance(hourly[0].get("values"), dict):
        null_fields = [key for key, value in hourly[0]["values"].items() if value is None]
        if null_fields:
            print("Fields returned as null in current hour:")
            for field in null_fields:
                print(f"  - {field}")
            print()

    out_file = Path(args.out_file) if args.out_file else Path(__file__).with_name("response.json")
    out_file.parent.mkdir(parents=True, exist_ok=True)
    out_file.write_text(json.dumps(response, indent=2), encoding="utf-8")
    print(f"Full JSON response written to: {out_file.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
