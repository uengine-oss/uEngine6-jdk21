# Installed Keycloak Mode

This mode is for Red Hat VM / external WAS deployments where Keycloak is installed as a separate server instead of Docker.

Latest Keycloak checked on 2026-05-11: `26.6.1` from the official Keycloak downloads page.

## Keycloak Server

Install Keycloak `26.6.1`, then import the existing realm:

```bash
cp infra/keycloak/realm-export.json /opt/keycloak/data/import/realm-export.json
/opt/keycloak/bin/kc.sh start-dev --http-port=8080 --import-realm
```

For production, set Keycloak hostname/proxy options according to the real domain and TLS termination:

```bash
KC_BOOTSTRAP_ADMIN_USERNAME=admin
KC_BOOTSTRAP_ADMIN_PASSWORD=<initial admin password>
KC_HOSTNAME=https://keycloak.example.com
KC_PROXY_HEADERS=xforwarded
KC_HTTP_ENABLED=true
KC_DB=postgres
KC_DB_URL=jdbc:postgresql://db.example.com:5432/keycloak
KC_DB_USERNAME=keycloak
KC_DB_PASSWORD=<db password>
```

`KC_BOOTSTRAP_ADMIN_USERNAME` and `KC_BOOTSTRAP_ADMIN_PASSWORD` are used only when the master realm is created for the first time.

## Required Client Values

In the `uengine` realm, update the `uengine` client:

```text
Root URL: https://bpm.example.com
Home URL: https://bpm.example.com
Valid Redirect URIs: https://bpm.example.com/*
Web Origins: https://bpm.example.com
```

The backend gateway uses the same client id and secret:

```text
KEYCLOAK_CLIENT_ID=uengine
KEYCLOAK_CLIENT_SECRET=<client secret>
```

## keycloak-gateway

Use the installed profile and point it at the external Keycloak:

```bash
export SPRING_PROFILES_ACTIVE=keycloak-installed
export GATEWAY_URI=https://bpm.example.com
export KEYCLOAK_URI=https://keycloak.example.com
export KEYCLOAK_REALM=uengine
export KEYCLOAK_CLIENT_ID=uengine
export KEYCLOAK_CLIENT_SECRET=<client secret>
```

Optional route overrides:

```bash
export FRONTEND_URI=http://localhost:5173
export PROCESS_SERVICE_URI=http://localhost:9094
export DEFINITION_SERVICE_URI=http://localhost:9093
export EXECUTION_SERVICE_URI=http://localhost:8000
```

## process-service

The service reads Keycloak Admin API values from environment variables:

```bash
export KEYCLOAK_URI=https://keycloak.example.com
export KEYCLOAK_REALM=uengine
export KEYCLOAK_ADMIN_REALM=master
export KEYCLOAK_ADMIN_CLIENT_ID=admin-cli
export KEYCLOAK_ADMIN_USERNAME=admin
export KEYCLOAK_ADMIN_PASSWORD=<admin password>
```

## process-gpt-vue3-hli

Set one mode variable plus the normal Keycloak values:

```bash
export VITE_KEYCLOAK_MODE=installed
export VITE_KEYCLOAK_URL=https://keycloak.example.com
export VITE_KEYCLOAK_REALM=uengine
export VITE_KEYCLOAK_CLIENT_ID=uengine
```

If `VITE_KEYCLOAK_URL` is omitted and `VITE_KEYCLOAK_MODE=installed`, the frontend defaults to `window.location.origin`.
