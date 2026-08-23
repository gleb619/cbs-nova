#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
REGISTRY="$ROOT/app/ui/local-registry"

mkdir -p "$REGISTRY"

# Clean previous tarballs
rm -f "$REGISTRY"/cbs-components-*.tgz "$REGISTRY"/cbs-admin-ui-plugin-*.tgz

# Pack the components library.
COMPONENTS_TARBALL=$(cd "$ROOT/frontend/components" && pnpm pack --pack-destination "$REGISTRY" 2>> /dev/null | tail -n1)
COMPONENTS_NAME=$(basename "$COMPONENTS_TARBALL")
echo "Packed components: $COMPONENTS_NAME"

# Build pre-transpiled server routes so the plugin works when installed from a tarball.
(cd "$ROOT/frontend/admin-ui-plugin" && pnpm build:server)

# Pack the admin UI plugin. pnpm pack will rewrite workspace:* to the concrete
# version (0.0.0), which the host app then satisfies with its direct tarball
# dependency on @cbs/components.
ADMIN_TARBALL=$(cd "$ROOT/frontend/admin-ui-plugin" && pnpm pack --pack-destination "$REGISTRY" 2>> /dev/null | tail -n1)
ADMIN_NAME=$(basename "$ADMIN_TARBALL")
echo "Packed admin-ui-plugin: $ADMIN_NAME"

# Update host package.json to point to the freshly packed tarballs.
sed -i "s|\"@cbs/components\": \"file:./local-registry/cbs-components-.*\"|\"@cbs/components\": \"file:./local-registry/$COMPONENTS_NAME\"|" "$ROOT/app/ui/package.json"
sed -i "s|\"@cbs/admin-ui-plugin\": \"file:./local-registry/cbs-admin-ui-plugin-.*\"|\"@cbs/admin-ui-plugin\": \"file:./local-registry/$ADMIN_NAME\"|" "$ROOT/app/ui/package.json"
# Keep pnpm.overrides in sync so transitive @cbs/components resolutions (from the
# packed cbs-admin-ui-plugin tarball, which has workspace:* rewritten to a
# concrete version) hit the local tarball instead of the npm registry.
if grep -q '"@cbs/components": "file:./local-registry/cbs-components-' "$ROOT/app/ui/package.json"; then
  sed -i "s|\"@cbs/components\": \"file:./local-registry/cbs-components-[^\"]*\"|\"@cbs/components\": \"file:./local-registry/$COMPONENTS_NAME\"|" "$ROOT/app/ui/package.json"
fi

echo "Local registry updated:"
echo "  $COMPONENTS_NAME"
echo "  $ADMIN_NAME"
echo "Run 'cd app/ui && pnpm install' to consume the new tarballs."
