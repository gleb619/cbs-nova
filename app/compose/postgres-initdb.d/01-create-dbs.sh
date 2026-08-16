#!/bin/bash
#
# Postgres first-boot init: creates the per-domain databases and roles used
# by Keycloak, Gitea, Bugsink and Temporal. Mounted into
# /docker-entrypoint-initdb.d/ and executed once on a fresh data volume.
#
# Credentials default to the historical per-service values so the upstream
# compose files do not need to change their connection settings beyond the
# hostname. Override via the matching *DB_PASSWORD env vars on the postgres
# service if you want stronger defaults in production.

set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<EOSQL
-- keycloak
CREATE DATABASE keycloak;
CREATE USER keycloak WITH ENCRYPTED PASSWORD '${KEYCLOAK_DB_PASSWORD:-keycloak}';
GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak;

-- gitea
CREATE DATABASE gitea;
CREATE USER gitea WITH ENCRYPTED PASSWORD '${GITEA_DB_PASSWORD:-gitea}';
GRANT ALL PRIVILEGES ON DATABASE gitea TO gitea;

-- bugsink
CREATE DATABASE bugsink;
CREATE USER bugsinkuser WITH ENCRYPTED PASSWORD '${BUGSINK_DB_PASSWORD:-change_me_secure_password}';
GRANT ALL PRIVILEGES ON DATABASE bugsink TO bugsinkuser;

-- temporal
CREATE DATABASE temporal;
CREATE USER temporal WITH ENCRYPTED PASSWORD '${TEMPORAL_DB_PASSWORD:-temporal}';
GRANT ALL PRIVILEGES ON DATABASE temporal TO temporal;
EOSQL

# Grant schema-level rights on the public schema; newer Postgres versions no
# longer do this automatically for newly created users.
for db_user in 'keycloak:keycloak' 'gitea:gitea' 'bugsinkuser:bugsink' 'temporal:temporal'; do
    user="${db_user%%:*}"
    db="${db_user##*:}"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$db" <<EOSQL
GRANT ALL ON SCHEMA public TO ${user};
ALTER SCHEMA public OWNER TO ${user};
EOSQL
done