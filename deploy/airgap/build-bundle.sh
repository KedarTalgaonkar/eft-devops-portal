#!/usr/bin/env bash
# Build the offline bundle on a machine WITH internet access (repository root or anywhere):
#   bash deploy/airgap/build-bundle.sh [version]
# Output: dist/eft-portal-airgap-<version>.tar.gz (+ .sha256). Copy both to the air-gapped server.
# Optional: MAVEN_MIRROR_URL / NPM_REGISTRY as for docker compose build; PLATFORM (default linux/amd64).
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"

VERSION=${1:-$(git describe --tags --always --dirty 2>/dev/null || date +%Y%m%d)}
PLATFORM=${PLATFORM:-linux/amd64}
NAME=eft-portal-airgap-$VERSION
OUT=dist/$NAME
POSTGRES_IMAGE=postgres:17-alpine

if [[ "$VERSION" == *-dirty ]]; then
  echo "Warning: uncommitted changes are included in $VERSION" >&2
fi

rm -rf "$OUT" && mkdir -p "$OUT/images"

echo "== Building portal images $VERSION ($PLATFORM)"
docker build --platform "$PLATFORM" --build-arg MAVEN_MIRROR_URL="${MAVEN_MIRROR_URL:-}" \
  -t "eft-devops-portal-backend:$VERSION" backend
docker build --platform "$PLATFORM" --build-arg NPM_REGISTRY="${NPM_REGISTRY:-}" \
  -t "eft-devops-portal-frontend:$VERSION" frontend

echo "== Pulling $POSTGRES_IMAGE"
docker pull --platform "$PLATFORM" "$POSTGRES_IMAGE"

echo "== Saving images"
save() { docker save "$1" | gzip -6 > "$OUT/images/$2.tar.gz"; echo "  $2.tar.gz"; }
save "eft-devops-portal-backend:$VERSION" backend
save "eft-devops-portal-frontend:$VERSION" frontend
save "$POSTGRES_IMAGE" postgres

echo "== Adding deployment files"
cp deploy/airgap/compose.yml deploy/airgap/env.example deploy/airgap/install.sh deploy/airgap/README.md "$OUT/"
echo "$VERSION" > "$OUT/VERSION"
{
  echo "version: $VERSION"
  echo "built: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "commit: $(git rev-parse HEAD 2>/dev/null || echo unknown)"
  echo "platform: $PLATFORM"
  for img in "eft-devops-portal-backend:$VERSION" "eft-devops-portal-frontend:$VERSION" "$POSTGRES_IMAGE"; do
    echo "image: $img $(docker image inspect --format '{{.Id}}' "$img")"
  done
} > "$OUT/MANIFEST"

(cd "$OUT" && sha256sum VERSION MANIFEST compose.yml env.example install.sh README.md images/*.tar.gz > SHA256SUMS)

echo "== Packing"
tar -C dist -czf "dist/$NAME.tar.gz" "$NAME"
(cd dist && sha256sum "$NAME.tar.gz" > "$NAME.tar.gz.sha256")
ls -lh "dist/$NAME.tar.gz"
echo "Done: dist/$NAME.tar.gz"
