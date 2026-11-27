# ArrowCursor

**বাংলা:** ফিজিক্যাল কীবোর্ডের অ্যারো কি (↑ ↓ ← →) দিয়ে স্ক্রিনে মাউস কার্সর নাড়ান, Space দিয়ে ক্লিক করুন — মাউস ছাড়াই ফোন চালান। ভেতরে Gemini অ্যাসিস্ট্যান্ট আছে: বাংলায় বা ইংরেজিতে বললেই Gemini নিজে সেটিং বদলে দেবে।

**English:** Move an on-screen mouse cursor with your physical keyboard arrow keys and click with Space — drive your phone with no mouse. Includes an in-app Gemini assistant that changes settings for you when you ask in Bengali or English.

## Features / সুবিধা

- Arrow keys move the cursor anywhere on screen (অ্যারো কি দিয়ে কার্সর যেকোনো জায়গায় নড়ে)
- Space (or Enter) clicks where the cursor is (Space/Enter দিয়ে ক্লিক)
- Auto-pause while typing, so Space types normally in text fields (টাইপ করার সময় Space স্বাভাবিকভাবে কাজ করে)
- Cursor speed, size and colour settings (গতি, সাইজ, রং বদলানো যায়)
- Notification toggle to turn the cursor on/off quickly (নোটিফিকেশন থেকে চালু/বন্ধ)
- In-app Gemini assistant with function calling — e.g. "কার্সর চালু করো", "স্পিড ৪০ করো" (Gemini নিজে বাটন/সেটিং করে দেয়)
- Bengali + English UI, switchable in Settings (বাংলা/ইংলিশ ভাষা বদলানো যায়; Ridmik কীবোর্ডে বাংলা লেখা যায়)

## Setup / সেটআপ

1. Install the APK from the [Releases](../../releases) page.
2. Open ArrowCursor → tap **Open Accessibility Settings** → turn on **ArrowCursor**.
3. Android 13+ sideloaded apps: if the service looks greyed out, go to App info → ⋮ menu → **Allow restricted settings**, then enable it.
4. Tap **Enable cursor**. Press an arrow key — the cursor moves. Press Space on an icon — it clicks.
5. While typing in a text field, the cursor keys pause automatically so Space types a space.

## Gemini API key / জেমিনি Key

1. Get a free key from Google AI Studio: https://aistudio.google.com/apikey
2. Open the **Gemini** tab in the app, paste the key and tap **Save key**.
3. The key is stored encrypted on your device only (EncryptedSharedPreferences). It is never sent anywhere except to Google's Gemini API, and never put in code or git.
4. Chat in Bengali (Ridmik keyboard works — just type Bengali in the message box) or English. Examples: "কার্সর চালু করো", "স্পিড ৪০ করো", "ক্লিক Space করো".

## How it works / কীভাবে কাজ করে

An `AccessibilityService` draws a cursor overlay (`TYPE_ACCESSIBILITY_OVERLAY`) and intercepts hardware key events (`flagRequestFilterKeyEvents`): `DPAD_UP/DOWN/LEFT/RIGHT` move the cursor, `SPACE`/`ENTER` dispatch a tap gesture at the cursor position. When an editable field is focused, keys are not consumed, so typing works normally.

## Auto-release / অটো রিলিজ

Every push to `main` builds the debug APK on GitHub Actions and publishes it as a GitHub Release (`v1.0.<run_number>`). No keystore or secret is needed — the debug APK is auto-signed.

## Privacy / গোপনীয়তা

No ads, no analytics, no tracking. The Gemini key stays on your device. The accessibility service only moves the cursor and taps where you point it with the keyboard.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
