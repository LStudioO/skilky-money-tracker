# Skilky dev shortcuts. Run `just` to see the list.
# Requires: https://github.com/casey/just (brew install just).

default:
    @just --list

# Boot Postgres + Ollama in the background, then tail the model pull.
up:
    docker compose -f docker/docker-compose.yml up -d
    docker compose -f docker/docker-compose.yml logs -f ollama-pull

# Stop the local stack. Volumes (DB, model cache) are kept.
down:
    docker compose -f docker/docker-compose.yml down

# Start only Postgres. Use this with native Ollama on macOS.
db:
    docker compose -f docker/docker-compose.yml up -d postgres

# Run the native Ollama server in the foreground.
ollama:
    #!/usr/bin/env bash
    set -euo pipefail
    if [[ "$(uname -s)" == "Darwin" ]]; then
        for candidate in \
            "$HOME/Applications/Ollama.app/Contents/Resources/ollama" \
            "/Applications/Ollama.app/Contents/Resources/ollama"; do
            if [[ -x "$candidate" ]]; then
                exec "$candidate" serve
            fi
        done
    fi
    exec ollama serve

# Wipe the local stack including volumes. Destructive.
nuke:
    docker compose -f docker/docker-compose.yml down -v

# Run the Ktor server in the foreground.
server:
    ./gradlew :server:run

# Run server tests.
test:
    ./gradlew :server:test

# Format Kotlin sources.
fmt:
    ./gradlew spotlessApply

# Format, lint, and test in one shot. Run before pushing.
check:
    ./gradlew spotlessApply detekt :server:test

# GET /api/v1/health from the running server.
health:
    curl -s http://localhost:8080/api/v1/health

# Pull (or re-pull) a chat model. Defaults to gemma4:e4b.
pull-model model='gemma4:e4b':
    docker compose -f docker/docker-compose.yml run --rm -e OLLAMA_MODEL={{ model }} ollama-pull

# psql into the dev DB.
psql:
    docker exec -it skilky-postgres psql -U skilky -d skilky
