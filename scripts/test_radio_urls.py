"""Test default MotoRider radio station stream URLs."""
from __future__ import annotations

import re
import ssl
import time
import urllib.request
from urllib.error import HTTPError, URLError

STATIONS = [
    ("capital_fm", "Capital FM", "https://media-ssl.musicradio.com/CapitalMP3"),
    ("capital_dance", "Capital Dance", "https://media-ssl.musicradio.com/CapitalDanceMP3"),
    (
        "kiss_fm",
        "Kiss FM",
        "http://www.radiofeeds.net/playlists/bauer.pls?station=kissnational-mp3",
    ),
    ("heart_dance", "Heart Dance", "https://media-ssl.musicradio.com/HeartDanceMP3"),
    (
        "planet_rock",
        "Planet Rock",
        "http://www.radiofeeds.net/playlists/bauer.pls?station=planetrock-mp3",
    ),
    (
        "absolute_radio",
        "Absolute Radio",
        "https://stream-ar.hellorayo.co.uk/absoluteradiohigh.aac",
    ),
    ("radio_x_uk", "Radio X", "https://media-ssl.musicradio.com/RadioXUKMP3"),
    ("heart_london", "Heart London", "https://media-ssl.musicradio.com/HeartLondonMP3"),
    ("lbc_london", "LBC London", "https://media-ssl.musicradio.com/LBCLondonMP3"),
    ("smooth_london", "Smooth London", "https://media-ssl.musicradio.com/SmoothLondonMP3"),
]

CTX = ssl.create_default_context()
HEADERS = {"User-Agent": "MotoRider/1.0 (Android)", "Icy-MetaData": "1"}


def resolve_pls(url: str) -> str | None:
    req = urllib.request.Request(url, headers=HEADERS)
    text = urllib.request.urlopen(req, context=CTX, timeout=15).read().decode(
        "utf-8", errors="replace"
    )
    for line in text.splitlines():
        line = line.strip()
        if line.lower().startswith("file1="):
            return line.split("=", 1)[1].strip()
    return None


def refresh_stream_auth(url: str) -> str:
    hosts = ("hellorayo.co.uk", "planetradio.co.uk", "sharp-stream.com")
    if not any(h in url.lower() for h in hosts):
        return url
    epoch = str(int(time.time()))
    if re.search(r"aw_0_1st\.skey=", url, re.I):
        return re.sub(r"aw_0_1st\.skey=[^&]*", f"aw_0_1st.skey={epoch}", url, flags=re.I)
    sep = "&" if "?" in url else "?"
    return f"{url}{sep}aw_0_1st.skey={epoch}"


def test_stream(url: str) -> tuple[str, str]:
    try:
        req = urllib.request.Request(url, headers=HEADERS)
        with urllib.request.urlopen(req, context=CTX, timeout=12) as resp:
            ct = resp.headers.get("Content-Type", "")
            icy = resp.headers.get("icy-name", "")
            data = resp.read(8192)
            if len(data) < 100:
                return "WEAK", f"status={resp.status} only {len(data)} bytes type={ct}"
            return "OK", f"status={resp.status} type={ct[:50]} bytes={len(data)} icy={icy[:40]}"
    except HTTPError as exc:
        return "HTTP_FAIL", f"{exc.code} {exc.reason}"
    except URLError as exc:
        return "URL_FAIL", str(exc.reason)[:100]
    except Exception as exc:  # noqa: BLE001
        return "ERR", str(exc)[:100]


def main() -> None:
    print("Testing radio streams...\n")
    for _sid, name, url in STATIONS:
        test_url = url
        note = ""
        if ".pls" in url.lower() or "playlists/" in url.lower():
            try:
                resolved = resolve_pls(url)
                note = f" pls->{resolved}" if resolved else " pls->NONE"
                test_url = resolved or url
            except Exception as exc:  # noqa: BLE001
                print(f"PLS_FAIL    {name:18} {exc}")
                continue

        status, detail = test_stream(refresh_stream_auth(test_url))
        print(f"{status:10} {name:18} {detail}{note}")


PLS_FIXES = [
    (
        "planet_rock",
        "Planet Rock",
        "http://www.radiofeeds.net/playlists/bauer.pls?station=planetrock-mp3",
    ),
    (
        "absolute_radio",
        "Absolute Radio",
        "http://www.radiofeeds.net/playlists/bauer.pls?station=absoluteradiohigh-mp3",
    ),
]


def test_pls_resolved() -> None:
    print("\nPLS resolver check:\n")
    for _sid, name, pls in PLS_FIXES:
        try:
            resolved = resolve_pls(pls)
            if not resolved:
                print(f"NO_FILE1   {name}")
                continue
            status, detail = test_stream(refresh_stream_auth(resolved))
            print(f"{status:10} {name:18} {detail}")
        except Exception as exc:  # noqa: BLE001
            print(f"ERR        {name:18} {exc}")


if __name__ == "__main__":
    main()
    test_pls_resolved()
