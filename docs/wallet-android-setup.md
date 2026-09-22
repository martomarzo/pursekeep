# Android setup — PurseKeep companion app

1. On the phone, download `pursekeep-<version>.apk` from
   https://github.com/martomarzo/money-maker/releases/latest and open it
   (allow installing from your browser once). The phone must reach your
   server (today: Tailscale connected).
2. In the web app open **Settings › Phone app & captured payments**, create a
   device (e.g. "Pixel") — a QR code appears.
3. In the app tap **Pair this phone → Scan QR code**. The app immediately
   sends a test capture; it shows up under **Captured payments** as
   *Needs account* (dismiss it).
4. Tap **Grant notification access** and enable PurseKeep in the system list.
   Optionally exempt it from battery optimisation.
5. Pay with a card. Within seconds the purchase is in your transactions (if
   the card is mapped) or under Captured payments as *Needs account* — assign
   the account there and tick "remember card".

Under **Apps to listen to** you can add Revolut, Wise or any app by package
name. Offline taps are queued and sent when the phone is back online.
If the web app says a device is revoked, the app shows "no longer paired" —
create a new device and scan again.
