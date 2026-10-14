#!/usr/bin/env bash
# Consistent database + uploads checkpoint; app writes are paused briefly.
set -Eeuo pipefail
umask 077
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root"
compose() { docker compose --env-file .env.oracle -f compose.oracle.yaml "$@"; }
command -v docker >/dev/null
[[ -f .env.oracle ]] || { echo 'Configure .env.oracle primeiro.' >&2; exit 1; }
exec 9>data-oracle-backup.lock
flock -n 9 || { echo 'Outro backup está em andamento.' >&2; exit 1; }
backup_dir=${1:-"$root/data/oracle-backups"}
mkdir -p -- "$backup_dir"
backup_dir=$(cd -- "$backup_dir" && pwd)
stamp=$(date -u +%Y%m%dT%H%M%SZ)
partial=$(mktemp -d "$backup_dir/.partial-$stamp-XXXXXX")
was_running=$(compose ps --status running --services app)
restore_app() {
    local result=$?
    trap - EXIT
    if [[ "$was_running" == app ]]; then
        compose start app || { echo 'Reinicie a aplicação com docker compose start app.' >&2; result=1; }
    fi
    if [[ $result -ne 0 ]]; then echo "Backup incompleto: $partial" >&2; fi
    exit "$result"
}
trap restore_app EXIT
if [[ "$was_running" == app ]]; then compose stop app; fi
compose exec -T postgres sh -c 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' > "$partial/postgres.dump"
compose run --rm --no-deps -T --user 10001:10001 --entrypoint tar app -C /app/data -czf - uploads > "$partial/uploads.tar.gz"
[[ -s "$partial/postgres.dump" && -s "$partial/uploads.tar.gz" ]]
printf '%s\n' "Created: $stamp" "Commit: $(git rev-parse HEAD)" > "$partial/metadata.txt"
(cd "$partial" && sha256sum postgres.dump uploads.tar.gz > SHA256SUMS)
final="$backup_dir/$stamp"
[[ ! -e "$final" ]] || { echo 'Já existe um backup com esse horário.' >&2; exit 1; }
mv -- "$partial" "$final"
printf 'Backup concluído: %s\n' "$final"
printf '%s\n' 'Copie para outro dispositivo. O backup não inclui .env.oracle nem certificados.'
