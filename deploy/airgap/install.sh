#!/bin/sh
# Run on the air-gapped server from inside the unpacked bundle folder:
#   sh install.sh            # verify, load images, create .env (first time)
#   sh install.sh --start    # same, then start the portal
set -eu
cd "$(dirname "$0")"

command -v docker >/dev/null || { echo "Docker is not installed" >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "Docker Compose v2 plugin is missing" >&2; exit 1; }

echo "Checking file integrity..."
sha256sum -c SHA256SUMS

echo "Loading images..."
for f in images/*.tar.gz; do
  echo "  $f"
  gunzip -c "$f" | docker load
done

VERSION=$(cat VERSION)
if [ ! -f .env ]; then
  sed "s/^PORTAL_VERSION=.*/PORTAL_VERSION=$VERSION/" env.example > .env
  chmod 600 .env
  echo "Created .env - set POSTGRES_PASSWORD and PORTAL_BOOTSTRAP_ADMIN_PASSWORD before starting."
else
  sed -i "s/^PORTAL_VERSION=.*/PORTAL_VERSION=$VERSION/" .env
  echo "Updated PORTAL_VERSION in the existing .env to $VERSION."
fi

if [ "${1:-}" = "--start" ]; then
  docker compose --env-file .env up -d
  docker compose --env-file .env ps
fi
