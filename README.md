# pico-webxr-kiosk

A tiny Android launcher that boots a Pico 4 Ultra Enterprise headset straight
into a WebXR experience and keeps it there. Built to mimic what Pico Business
Center does internally when you tap a "WebXR link" tile, but driven from a
config you control.

> **Pico for Business is required.** Auto-launch on boot only works on Pico
> Enterprise headsets where you can push apps via Device Manager and enable
> kiosk mode. Consumer Pico devices block third-party apps from being started
> by the boot broadcast (`FEAT_PROCESS_INTERCEPT`), so this project has no
> useful role there.

---

## What it does

When the kiosk app is started:

1. Kills any restored Pico browser tabs from the previous session.
2. Fires a Chrome Custom Tabs intent at `com.pico.browser.overseas` (the
   Pico-flavoured Chromium browser) targeting the URL you configured, with
   `?xr=1` appended.
3. The page sees that query parameter and calls
   `navigator.xr.requestSession('immersive-vr')` on first paint.
4. The browser promotes the tab to `XrHostActivity` — the headset is in WebXR.

Pico's Business Center uses a more elaborate path (Trusted Web Activity +
`org.pico.twaapk.shellapk.picoXRButtonSelector` meta-data) to auto-click an
"Enter VR" button after page load. Several pages — including the included
`sonnensystem-webxr` demo — short-circuit that by checking `?xr=1` themselves.
If your target page does not, see [Adapting your page](#adapting-your-page).

## Hardware / OS support

Tested on:

- Pico 4 Ultra Enterprise, Pico OS 5.x, Pico Browser `com.pico.browser.overseas`

Likely-also-works:

- Other Pico for Business devices that ship `com.pico.browser.overseas` and
  Device Manager. The browser-package name is hardcoded; change `BROWSER_PKG`
  in `MainActivity.java` if your device ships a differently-named build.

Does **not** work on:

- Consumer Pico 4 / Pico 4 Ultra (no Device Manager, no kiosk mode, boot
  broadcast is intercepted).
- Any non-Pico WebXR headset.

## Configure

Open `res/values/strings.xml` and set:

```xml
<string name="target_url">https://your-webxr-experience.example.com</string>
<string name="target_label">My WebXR App</string>
```

That's the only thing you need to change for a single-app kiosk.

## Build

Requirements:

- Android SDK with `build-tools/35.0.0` and `platforms/android-34`
- JDK 17 (the build script uses `javac -source 1.8 -target 1.8`, which 17
  supports)
- macOS / Linux shell

```bash
./build.sh
```

Output: `build/app.apk` — debug-signed with an ephemeral keystore
(`debug.keystore`, generated on first build).

For production deployment you should re-sign with your own key. The provided
keystore is throwaway and is not checked into the repo.

## Deploy via Device Manager

1. Open the Pico for Business admin console.
2. Devices → upload `build/app.apk` as a custom app.
3. Push to your headset(s).
4. Device → Kiosk Mode → pick **`works.huse.picoxr`** as the kiosk
   application. Save.
5. Reboot the headset. It should boot directly into your WebXR experience.

If you'd rather just sideload for testing without enabling kiosk:

```bash
adb install build/app.apk
adb shell am start -n works.huse.picoxr/.MainActivity
```

## Adapting your page

For the `?xr=1` auto-enter to work, your page needs to read that query
parameter and call `navigator.xr.requestSession('immersive-vr')` itself on
load. The demo page in this repo uses logic similar to:

```js
function isXRLaunchRequested() {
  const params = new URLSearchParams(window.location.search);
  return params.get('xr') === '1' || params.get('enterxr') === '1';
}

document.addEventListener('DOMContentLoaded', async () => {
  if (isXRLaunchRequested()) {
    try {
      await navigator.xr.requestSession('immersive-vr', {
        optionalFeatures: ['local-floor', 'hand-tracking'],
      });
    } catch (err) {
      console.warn('Auto-enter failed; user can tap the Enter VR button.', err);
    }
  }
});
```

If you cannot modify the page, you can switch the launcher to drive Pico's
internal "auto-click selector" path by setting the `picoXRButtonSelector`
intent extra. See the inline comments in `MainActivity.java` for the right
extra keys — but the practical answer is to use a `?xr=1` query and call
`requestSession` directly, which is roughly an order of magnitude less
fragile.

## How it stays in WebXR

- The activity is declared `android:launchMode="singleInstance"` so kiosk
  relaunches from Device Manager always reuse the same task.
- A 4-second dedup window inside `onCreate` ignores duplicate launches that
  some boot paths trigger.
- `ActivityManager.killBackgroundProcesses("com.pico.browser.overseas")` is
  called before each launch so any tabs the browser would otherwise restore
  from disk get wiped.
- The actual "if the user exits, bring them back" behaviour is handled by
  Pico Device Manager's kiosk mode, not by this app. That's deliberate —
  earlier versions had a `UsageStatsManager`-based watchdog service inside
  the APK, but it races with the system kiosk relaunch and ends up spawning
  duplicate browser tabs. The simplest robust setup is: this app fires the
  intent once, Pico keeps relaunching the app.

## Files

```
AndroidManifest.xml          single activity, no boot receiver, no services
build.sh                     aapt2 + javac + d8 + apksigner, no Gradle
res/values/strings.xml       URL + label
src/.../MainActivity.java    builds & fires the Custom Tabs intent
```

The build deliberately does not use Gradle — total build time is ~2 seconds.

## License

MIT, see [LICENSE](LICENSE).
