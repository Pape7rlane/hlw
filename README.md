Package the standalone Python server that originally runs on a computer/Proot into a mobile app (Chaquopy with embedded CPython).

After startup, it listens on the local machine:

| Port | Purpose |
|---|---|
| HTTP 8080 | Server list, sessions, configuration, level entry, battle settlement |
| TCP 9000 | Game binary protocol |

The game client connects to `127.0.0.1`, so it only needs to be installed on the same phone.

## Build

After pushing to GitHub, it is automatically built by Actions (see `.github/workflows/build.yml`),
The artifact is in **Artifacts → huluwa-localserver-apk** on the Actions page.

> The first build will download Gradle / Android SDK / Chaquopy's Python, taking approximately 5-10 minutes.

## Structure

```
app/src/main/python/      Python server (server.py + data seeds)
app/src/main/java/...     MainActivity (launch screen) + Ser
