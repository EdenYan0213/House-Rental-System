# Android APK Build Guide (Vue + Capacitor)

## 1. Build web assets

```bash
npm run build
```

## 2. Sync assets and plugins to Android

```bash
npm run android:sync
```

## 3. Open Android Studio project

```bash
npm run android:open
```

Then in Android Studio:
- wait for Gradle sync
- choose Build > Build Bundle(s) / APK(s) > Build APK(s)

## 4. CLI debug APK build (Windows)

```bash
npm run android:apk:debug
```

Output APK path:
- `android/app/build/outputs/apk/debug/app-debug.apk`

## Local backend connection

Because APK has no Vite proxy, set API base URL to a reachable backend address:
- Emulator: `http://10.0.2.2:8080/api`
- Real phone (same Wi-Fi): `http://<PC_LAN_IP>:8080/api`

You can configure this in app:
- Mobile `我的` page > `连接设置`

## HTTP cleartext

For local HTTP backend testing, Android cleartext is enabled via:
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/res/xml/network_security_config.xml`

## Push skeleton status

Current app includes Capacitor push registration skeleton:
- token registration callback
- notification click route handling

If backend endpoints are not ready, app keeps local fallback and will not block usage.
