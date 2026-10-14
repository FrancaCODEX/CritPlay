#!/usr/bin/env bash
# Run on the VM from oracle-cloud. Back up before fetching/building new code.
set -Eeuo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root"
[[ $(git branch --show-current) == oracle-cloud ]] || { echo 'Use a branch oracle-cloud.' >&2; exit 1; }
[[ -z $(git status --porcelain) ]] || { echo 'Há alterações locais. Salve-as antes de atualizar.' >&2; exit 1; }
bash deploy/oracle/backup.sh
git pull --ff-only origin oracle-cloud
docker compose --env-file .env.oracle -f compose.oracle.yaml up -d --build --wait --wait-timeout 180
printf '%s\n' 'Atualização concluída. Confira o site e /api/health por HTTPS.'
