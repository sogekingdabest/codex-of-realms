# Local observability

M7 provides an optional Prometheus and Grafana overlay. It consumes application metrics that already describe retrieval duration, authorized result counts, distance distributions, QA outcomes, and QA duration. It never enables prompt, chunk, answer, token, or source-content logging.

## Start

Set `GRAFANA_ADMIN_PASSWORD` alongside the other local passwords in `.env`, then run:

```powershell
docker compose -f compose.yaml -f compose.observability.yaml --profile observability up --build --wait
```

Open:

- Prometheus: <http://localhost:9090>
- Grafana: <http://localhost:3000> (user `admin`)
- Raw application scrape: <http://localhost:8080/actuator/prometheus>

Grafana provisions the Prometheus data source and the **Codex of Realms — quality and retrieval** dashboard automatically.

## Security boundary

The Prometheus endpoint is unauthenticated so the local scraper can reach it. It exposes aggregate process and application metrics, not lore content. Do not publish ports 8080, 9090, or 3000 directly to an untrusted network. A deployed environment should isolate the management network or add an authenticated reverse proxy.

Use low-cardinality labels only. Never add realm IDs, user IDs, questions, source titles, chunks, answers, bearer tokens, or exception payloads as metric labels.

## Stop and remove local metric history

Stopping is non-destructive:

```powershell
docker compose -f compose.yaml -f compose.observability.yaml --profile observability down
```

The `prometheus-data` and `grafana-data` volumes retain local history. Add `--volumes` only when you intentionally want to delete all Compose-managed data, including application database, source, and model volumes.
