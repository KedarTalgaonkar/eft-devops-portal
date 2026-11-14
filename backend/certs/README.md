# Corporate root certificates (optional)

If HTTPS traffic from the containers goes through a TLS-inspecting proxy, Java will not trust it and calls to
Microsoft Entra ID, Jenkins, GitHub, SonarQube and others fail with "unable to find valid certification path".

Put the corporate root CA (and intermediates) here as `.crt` or `.pem` files (PEM, Base-64 encoded) and rebuild:

```bash
docker compose build backend
```

The Dockerfile imports every file in this folder into the JRE trust store. Only public CA certificates belong here,
never private keys.
