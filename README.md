# plp-fido2

A minimal, self-hosted **FIDO2 / WebAuthn** server — single JAR, SQLite storage, zero infrastructure.

Drop it behind a reverse proxy and your users can register and log in with a passkey. No passwords. No external database. No cloud dependency.

---

## Why plp-fido2?

WebAuthn is the modern authentication standard supported by every browser and every platform — Windows Hello, Touch ID, Face ID, hardware security keys. Despite being a W3C standard since 2019, many developers still avoid it because setting up a WebAuthn server feels complex.

**It isn't.** plp-fido2 proves it.

| What you get | Details |
|---|---|
| Full WebAuthn server | Registration + authentication, fully spec-compliant via [webauthn4j](https://github.com/webauthn4j/webauthn4j) |
| Zero infrastructure | SQLite — no Postgres, no Redis, no external service |
| Single JAR | One file to deploy, one file to update |
| Free forever | No licence, no SaaS, no usage limits |
| Tiny footprint | 6 dependencies, ~10 MB JAR |

---

## How easy is the integration?

This is all the HTML you need to add a **Register** button to any page:

```html
<button onclick="doRegister()">Register with passkey</button>

<script>
  const API_BASE = '/webauthn';   // your nginx location

  async function doRegister() {
    // 1. Get challenge from server
    const publicKey = await fetch(API_BASE + '/register/start', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        displayname: 'Jane Doe',
        user:        'jane@example.com',
        rpid:        window.location.hostname,
        device:      navigator.userAgent
      })
    }).then(r => r.json());

    // 2. Let the browser/authenticator do the work
    publicKey.challenge = base64urlToBytes(publicKey.challenge);
    publicKey.user.id   = base64urlToBytes(publicKey.user.id);
    const cred = await navigator.credentials.create({ publicKey });

    // 3. Send result back to server
    await fetch(API_BASE + '/register/finish', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id: cred.id, rawId: bytesToBase64url(cred.rawId),
                             type: cred.type, response: { ... } })
    });
  }
</script>
```

That's it. The browser handles biometrics, cryptography, and device binding — your code just calls two endpoints.

Ready-to-use demo pages are in the [`html/`](html/) folder.

---

## API

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/` | Demo page |
| `POST` | `/register/start` | Begin registration — returns WebAuthn `PublicKeyCredentialCreationOptions` |
| `POST` | `/register/finish` | Complete registration — stores credential in SQLite |
| `POST` | `/login/options` | Begin authentication — returns `PublicKeyCredentialRequestOptions` |
| `POST` | `/login/verify` | Complete authentication — verifies credential signature |

---

## Setup

### 1. Build

```bash
./gradlew shadowJar
# → build/libs/plp-fido2-1.0.0.jar
```

Requires Java 21.

### 2. Configure

Create `application.properties` next to the JAR:

```properties
server.port=8081
allowed.ips=127.0.0.1,::1
```

The service only accepts connections from listed IPs — keep it behind a reverse proxy.

### 3. Run

```bash
java -jar plp-fido2-1.0.0.jar
```

The SQLite database (`phraselockWebAuthn.db`) is created automatically next to the JAR on first start.

### 4. nginx

```nginx
location /webauthn/ {
    proxy_pass          http://localhost:8081/;
    proxy_set_header    Host              $host;
    proxy_set_header    X-Forwarded-Proto $scheme;
}
```

The trailing slash in `proxy_pass` strips the location prefix — the service serves from root internally.

That's the full setup. No init scripts, no migrations, no config wizard.

---

## Credential storage

Credentials are stored in a local SQLite file — no network, no server, no cost. The schema is created automatically:

```sql
CREATE TABLE credentials (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id          TEXT NOT NULL,
    user             TEXT NOT NULL,
    display_name     TEXT,
    domain           TEXT,
    credential_id    TEXT,
    credential_record TEXT,
    public_key       TEXT,
    signature_counter INTEGER DEFAULT 0,
    discoverable     INTEGER DEFAULT 0
);
```

---

## Tech stack

| Library | Purpose |
|---|---|
| [Javalin 7](https://javalin.io) | HTTP server |
| [webauthn4j](https://github.com/webauthn4j/webauthn4j) | FIDO2 / WebAuthn protocol |
| [SQLite JDBC](https://github.com/xerial/sqlite-jdbc) | Credential storage |
| [Jackson](https://github.com/FasterXML/jackson) | JSON |
| [j2html](https://j2html.com) | Demo page HTML |
| [SLF4J](https://www.slf4j.org) | Logging |

---

## Part of the PhraseLock ecosystem

plp-fido2 was extracted from [PhraseLock](https://phraselock.com) — a self-hosted credential wallet that uses FIDO2 as its authentication layer.

© 2026 iPoxo IT GmbH — All rights reserved
