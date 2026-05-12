# uEngine Keycloak Installed Package

This package contains Keycloak 26.6.1 with the `uengine` realm import file.

## Windows

```bat
start-keycloak-installed.bat
```

Default URL:

```text
http://localhost:8080
http://localhost:8080/admin
```

## Linux / Red Hat

```bash
chmod +x start-keycloak-installed.sh
./start-keycloak-installed.sh
```

## Defaults

Keycloak master admin:

```text
admin / admin
```

Imported `uengine` realm users:

```text
admin / admin
hong / 1234
```

Default port:

```text
8080
```

Override the port:

```bash
KEYCLOAK_HTTP_PORT=18080 ./start-keycloak-installed.sh
```

```bat
set KEYCLOAK_HTTP_PORT=18080
start-keycloak-installed.bat
```

