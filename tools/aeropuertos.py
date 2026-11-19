#!/usr/bin/env python3
"""Genera app/src/main/assets/aeropuertos.csv desde OurAirports.
Uso: python3 -I tools/aeropuertos.py airports.csv app/src/main/assets/aeropuertos.csv"""
import csv, sys
src, dst = sys.argv[1], sys.argv[2]
rows = []
with open(src, encoding="utf-8") as f:
    for r in csv.DictReader(f):
        if r["type"] not in ("large_airport", "medium_airport"): continue
        if r["scheduled_service"] != "yes" or not r["iata_code"]: continue
        rows.append([r["iata_code"], r["icao_code"] or r["ident"], r["name"], r["municipality"],
                     r["iso_country"], "%.4f" % float(r["latitude_deg"]), "%.4f" % float(r["longitude_deg"]),
                     "1" if r["type"] == "large_airport" else "0"])
rows.sort(key=lambda x: (x[7] != "1", x[0]))
with open(dst, "w", encoding="utf-8", newline="") as f:
    w = csv.writer(f, delimiter="|", quoting=csv.QUOTE_NONE, escapechar="\\")
    for r in rows: w.writerow([c.replace("|", "/") for c in r])
print(len(rows), "aeropuertos")
