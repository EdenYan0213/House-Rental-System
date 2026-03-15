# Mobile App Local Backend Connection

## Why APK cannot use `/api`
- In browser development, Vite proxy maps `/api` to backend.
- In APK WebView, there is no Vite proxy, so `/api` is unresolved.
- You must point API to a real reachable backend URL.

## Project support added
- `src/api/index.js` now supports:
  - `VITE_API_BASE_URL` from env
  - runtime override via `setApiBaseUrl(url)`
- `vite.config.js` now supports:
  - `VITE_PROXY_TARGET` for proxy target
  - `server.host = true` for LAN access testing

## Typical setups

### 1) Browser on PC (existing way)
- keep `VITE_API_BASE_URL` empty
- use Vite proxy `/api` -> `http://localhost:8080`

### 2) Android Emulator (APK or WebView)
- set `VITE_API_BASE_URL=http://10.0.2.2:8080/api`

### 3) Real phone in same LAN
- start backend on PC and allow firewall inbound on 8080
- set `VITE_API_BASE_URL=http://<PC_LAN_IP>:8080/api`
  - example: `http://192.168.1.100:8080/api`

## Runtime switch (optional)
You can switch API base at runtime without rebuild:

```js
import { setApiBaseUrl } from '@/api'
setApiBaseUrl('http://192.168.1.100:8080/api')
```

Clear override and fallback to env/default:

```js
import { setApiBaseUrl } from '@/api'
setApiBaseUrl('')
```

## Backend check list
- Backend binds to accessible host (not blocked by firewall).
- CORS allows mobile origin. Current project already has CORS config in Spring Security/WebMvc.
- If using HTTP in Android APK, ensure cleartext traffic is allowed in Android manifest/network config.

## Quick verification
- Open app login page on phone/emulator.
- Try login.
- If request fails, verify backend URL and phone/emulator reachability:
  - same Wi-Fi
  - correct IP
  - firewall rule on 8080
