# plp-fido2

A minimal, self-hosted **FIDO2 / WebAuthn** server — single JAR, SQLite storage, zero infrastructure.

Drop it behind a reverse proxy and your users can register and log in with a passkey.  
No passwords. No external database. No cloud dependency.

---

## Why plp-fido2?

WebAuthn is the modern authentication standard supported by every browser and every platform — Windows Hello, Touch ID, Face ID, hardware security keys. Despite being a W3C standard since 2019, many developers still avoid it because setting up a WebAuthn server feels complex.

**It isn't.** plp-fido2 proves it.

| What you get | Details |
|---|---|
| Full WebAuthn server | Registration + authentication, fully spec-compliant via [webauthn4j](https://github.com/webauthn4j/webauthn4j) |
| Admin UI | Browser-based management, protected by a secret token |
| Controlled onboarding | Users can only register after the admin pre-authorises them with a one-time token |
| Session management | HttpOnly cookie after login — ready to protect your own pages |
| Ready-to-use demo pages | `register.html`, `login.html`, `dashboard.html` — drop them into any web root |
| Zero infrastructure | SQLite — no Postgres, no Redis, no external service |
| Single JAR | One file to deploy, one file to update |
| Free forever | No licence, no SaaS, no usage limits |
| Tiny footprint | 6 dependencies, ~10 MB JAR |

---

## The complete flow

```
Admin                           User                          Server
  │                              │                              │
  ├─ open /webauthn/admin/users ─────────────────────────────► │
  ├─ create user → one-time token generated ◄──────────────────┤
  ├─ share token with user ──────►│                             │
  │                               ├─ open register.html         │
  │                               ├─ enter email + token ──────►│
  │                               │                    validate token
  │                               ├─ browser prompts biometric  │
  │                               ├─ passkey created ──────────►│
  │                               │                    token marked used
  │                               ├─ open login.html            │
  │                               ├─ browser authenticates ────►│
  │                               │◄──────────── session cookie─┤
  │                               ├─ dashboard.html loaded       │
  │                               │◄─────── welcome, display name┤
  │                               ├─ Sign out ──────────────────►│
  │                               │                    cookie cleared
```

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
        device:      navigator.userAgent,
        token:       'ABCD1234'            // one-time token from admin
      })
    }).then(r => r.json());

    // 2. Let the browser/authenticator do the work
    publicKey.challenge = base64urlToBytes(publicKey.challenge);
    publicKey.user.id   = base64urlToBytes(publicKey.user.id);
    const cred = await navigator.credentials.create({ publicKey });

    // 3. Send result back to server
    await fetch(API_BASE + '/register/finish', { method: 'POST', ... });
  }
</script>
```

That's it. The browser handles biometrics, cryptography, and device binding — your code just calls two endpoints.

Fully worked demo pages (with login redirect and session check) are in the [`html/`](html/) folder.

---

## API

### WebAuthn endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/` | Built-in demo page |
| `POST` | `/register/start` | Begin registration — validates one-time token, returns `PublicKeyCredentialCreationOptions` |
| `POST` | `/register/finish` | Complete registration — stores credential, marks token as used |
| `POST` | `/login/options` | Begin authentication — returns `PublicKeyCredentialRequestOptions` |
| `POST` | `/login/verify` | Complete authentication — verifies signature, sets session cookie |
| `GET` | `/session` | Returns `{userId, email, displayName}` for the current session, or `401` |
| `POST` | `/logout` | Clears the session cookie |

### Admin endpoints (require `?token=<admin.token>`)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/admin/users` | Admin UI — manage pending users and registered credentials |
| `POST` | `/admin/users/create` | Pre-register a user, generates a one-time token |
| `POST` | `/admin/users/delete` | Remove a pending registration |
| `POST` | `/admin/credential/rename` | Update a credential's display name |
| `POST` | `/admin/credential/delete` | Revoke a registered credential |

---

## Setup

### One-line install (Debian / Ubuntu / Raspberry Pi OS)

```bash
curl -sSL https://raw.githubusercontent.com/phraselock/plp-fido2/main/install.sh | sudo bash
```

The installer downloads the latest release from GitHub, installs Java 21 if needed, walks you through the configuration with a dialog UI, and registers a `plp-fido2` systemd service. Re-running the same command upgrades to the latest version while preserving your existing configuration and admin token.

---

### Manual setup

### 1. Build

```bash
./gradlew shadowJar
# → build/libs/plp-fido2-*.jar
```

Requires Java 21.

### 2. Configure

Create `application.properties` next to the JAR:

```properties
# HTTP port
server.port=8081

# Only accept connections from these IPs (keep the service behind a proxy)
allowed.ips=127.0.0.1,::1,[0:0:0:0:0:0:0:1]

# Admin UI token — protects /admin/ routes
# Generate one with: openssl rand -hex 32
admin.token=your-secret-token-here

# nginx location prefix (used for browser-level redirects in the admin UI)
app.path.prefix=/webauthn
```

### 3. Run

```bash
java -jar plp-fido2-*.jar
```

The SQLite database (`phraselockWebAuthn.db`) is created automatically next to the JAR on first start.

### 4. nginx

```nginx
location /webauthn/ {
    proxy_pass          http://localhost:8081/;   # trailing slash strips the prefix
    proxy_set_header    Host              $host;
    proxy_set_header    X-Real-IP         $remote_addr;
    proxy_set_header    X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header    X-Forwarded-Proto $scheme;
}
```

The trailing slash in `proxy_pass` strips the location prefix — the service serves from root internally, so the same JAR works both locally (`localhost:8081/`) and behind nginx (`your.domain/webauthn/`).

### 5. Deploy the demo pages (optional)

Copy the [`html/`](html/) folder to any directory your web server serves:

```bash
cp -r html/* /var/www/html/fdo/
```

Then open `https://your.domain/fdo/register.html` — everything is wired up and ready to use.

---

## Admin UI

Open `https://your.domain/webauthn/admin/users?token=<your-admin-token>` after the service starts.

The admin UI has two tabs:

**Users tab**
- Add a user by e-mail → a one-time 8-character registration token is generated
- Share the token with the user out-of-band (e-mail, message, etc.)
- The token is shown as long as registration is pending and becomes "Registered" after the passkey is created
- Full-text search across all users

**Credentials tab**
- Lists all registered passkeys with user, display name, and domain
- Edit the display name inline
- Revoke any credential with one click
- Full-text search across all credentials

The token is never sent automatically — the admin chooses how to deliver it to the user. The user can only register once per token.

---

## Session management

After a successful login, the server creates a session in the `sessions` table and sets an **HttpOnly, SameSite=Strict** cookie (`plp_fido2_session`, 30-day expiry).

Your pages can call `GET /session` to check whether a visitor is authenticated:

```javascript
const res = await fetch('/webauthn/session', { credentials: 'same-origin' });
if (res.status === 401) {
  window.location.href = 'login.html'; // not logged in
} else {
  const { displayName, email } = await res.json();
  // show personalised content
}
```

Call `POST /logout` to clear the session.

---

## Generating an admin token

```bash
openssl rand -hex 32
```

Paste the output into `application.properties` as `admin.token`. The service reads the value on every request — no restart needed after a change.

---

## Storage

All data lives in a single SQLite file. The schema is created automatically on first start:

| Table | Purpose |
|---|---|
| `credentials` | Registered passkeys — credential ID, public key, sign counter, display name |
| `pending_users` | Pre-registered users waiting to complete registration |
| `sessions` | Active login sessions — tied to the session cookie |

---

## Tech stack

| Library | Purpose |
|---|---|
| [Javalin 7](https://javalin.io) | HTTP server (embedded Jetty) |
| [webauthn4j](https://github.com/webauthn4j/webauthn4j) | FIDO2 / WebAuthn protocol |
| [SQLite JDBC](https://github.com/xerial/sqlite-jdbc) | Credential + session storage |
| [Jackson](https://github.com/FasterXML/jackson) | JSON |
| [j2html](https://j2html.com) | Admin UI |
| [SLF4J](https://www.slf4j.org) | Logging |

---

## Part of the PhraseLock ecosystem

plp-fido2 was extracted from [PhraseLock](https://phraselock.com) — a self-hosted credential wallet that uses FIDO2 as its authentication layer.

© 2026 iPoxo IT GmbH — All rights reserved
