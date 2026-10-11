# Money Manager

A personal Android expense tracker that reads your bank SMS, sorts spending into categories and compares months.
India-only (₹, Indian banks), single user, sideloaded. **The app has no internet permission**: nothing leaves your phone.

- **Home**: pick a month; switch between *All*, *Bank accounts* and *Credit cards*. Shows:
  - **Spent so far**, compared with the same days of last month (1–11 Oct vs 1–11 Sep, so a half-finished month isn't compared with a whole one).
  - Money in, money out and net.
  - **Monthly budget**: what's left, a daily allowance, and where the month is heading.
  - **Where it went** (top categories), **Biggest changes** vs last month.
  - **Coming up** (bills due in the next 2 weeks) and **Recent transactions** with account chips.
- **Spending details** (tap the total or any category):
  - Trend as *Pace* (running total against last month), *6 months* (with average) or *Daily*.
  - Category breakdown, or for a category its top merchants and transactions.
  - Filter by account.
- **Categories**: every category for the month with its share and change vs last month.
- **Compare**: stacked bars for the last 6 or 12 months, plus a category × month table with ▲/▼ changes.
- **Transactions**: search, filter by category / account / dates, tap to change category, note or exclude.
- **Bills**: calendar of subscriptions, rent, SIPs and EMIs (detected automatically): paid, due, or expected but not seen.
- **Settings**: monthly budget, rules editor, rescan inbox, unparsed SMS, JSON backup/restore, CSV export, app lock, theme.

---

## 1. Build the APK (Windows, PowerShell)

```powershell
cd $HOME\OneDrive\Desktop\money_manager
.\gradlew assembleDebug
```

The APK is written to:

```
app\build\outputs\apk\debug\app-debug.apk
```

The first build downloads Gradle and libraries (a few minutes). Later builds take about a minute.

**Optional, faster app:** `.\gradlew assembleRelease` builds `app\build\outputs\apk\release\app-release.apk`.
It's minified (about 3 MB instead of 22 MB) and runs smoother, but is otherwise identical. It's signed with the
same key, so you can switch between debug and release builds without losing data.

### Run the tests

```powershell
.\gradlew test
```

This runs about 100 tests (parser, categoriser, analysis, bill calendar, spending/budget maths), plus your own
`samples.txt` if it exists (see section 5).

> ⚠️ `.\gradlew connectedDebugAndroidTest` runs the same tests on a connected phone or emulator. **Never run it
> with your own phone plugged in**: Android's test runner uninstalls the app afterwards, which deletes its data.

### What was installed on this PC (no admin rights needed)

| What | Where |
|---|---|
| JDK 17 (Temurin) | `%USERPROFILE%\.jdks\jdk-17.0.20.1+1` (your system Java 26 is untouched) |
| Android SDK (platform 37, build-tools 37, platform-tools) | `%LOCALAPPDATA%\Android\Sdk` |
| SDK location for this project | `local.properties` (not committed) |

`gradle/gradle-daemon-jvm.properties` tells Gradle to use JDK 17 automatically, so no `JAVA_HOME` changes are needed.
To get `adb` and the right `JAVA_HOME`/`ANDROID_HOME` in a PowerShell window, run `. .\tools\env.ps1` (note the leading dot).

---

## Try it on this laptop first (Android emulator, no phone)

```powershell
.\tools\emulator.ps1 -Seed
```

This builds the app, opens a virtual Android 16 phone in a window, installs Money Manager, and gives it about
3 months of realistic bank SMS (salary, rent, Netflix, SIP, Swiggy, Uber, credit card spends and a card bill, plus
an OTP, a promo, a failed payment and one unreadable message) so every screen has something to show. Click with the mouse like
a finger; the onboarding asks for the same permissions as on a real phone.

| Command | What it does |
|---|---|
| `.\tools\send-sms.ps1` | a random bank SMS arrives *now*: you get the "₹450 at Swiggy → Food & Dining" notification |
| `.\tools\send-sms.ps1 -From HDFCBK -Text "Rs.450.00 debited from a/c **1234 ..."` | send your own text |
| `.\tools\send-sms.ps1 -File samples.txt` | run your real SMS formats through the app (unreadable ones land in Settings → Unparsed) |
| `.\tools\emulator.ps1 -Fresh -Seed` | after you change code: rebuild, reinstall from scratch, re-seed |
| `.\tools\emulator.ps1` | rebuild and update the app, keeping its data |
| `.\tools\emulator.ps1 -Wipe` | factory-reset the whole virtual phone |

The first start takes 2–4 minutes; after that it's quick. Close the emulator window when done. The emulator
is completely separate from your phone. (`-Seed` and `-File` use a small hook that exists only in debug builds
and only works on an emulator, because the emulator can only deliver SMS dated "now".) App lock can't be tried
on the emulator unless you set a screen PIN in its Android settings first.

---

## 2. Install on your phone

**A. Copy the APK (no cable tools needed)**
1. Copy `app-debug.apk` to the phone (USB file transfer, or any cloud drive / chat app).
2. On the phone, open it from the Files app → allow "Install unknown apps" for that app when asked → Install.
3. Android 13+ treats file-installed apps as untrusted for SMS. If the SMS permission is blocked, go to
   **Settings → Apps → Money Manager → ⋮ (top-right) → Allow restricted settings**, then grant SMS.
   The app shows this help itself, with a button that opens the right screen.

**B. `adb install -r` over USB (developer route)**
1. On the phone: Settings → About phone → tap *Build number* 7 times, then enable **USB debugging** in Developer options.
2. Plug in, accept the "Allow USB debugging" prompt, then on the PC:
   ```powershell
   .\tools\install.ps1                  # or: adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```
   `-r` means "replace": it updates the installed app and keeps its data. Apps installed this way
   don't hit the "restricted settings" block.

---

## 3. Updating without losing data

Your data survives updates as long as two things stay the same:

1. **The app id** `app.moneymanager` (in `app/build.gradle.kts`). Never change it.
2. **The signing key** `keystore/debug.keystore` (password `android`). It lives only on your PC: it's
   git-ignored so it never reaches GitHub, where anyone could use it to sign a fake "update" to your app.
   **Keep a copy of this file somewhere safe** (it's also in your OneDrive, since this folder is). If it's lost, a new
   build can't update the installed app and you'd have to export → uninstall → import. A fresh clone without the
   file still builds, using Android's standard debug key, but that APK can't update the app on your phone.

Updates that add features upgrade the data already on your phone the first time they start (e.g. the Bank /
Credit cards split re-reads stored SMS to tell your cards apart); no rescan needed.

So: build, then install over the top (method A or B). **Don't uninstall first.** `versionCode` can stay the same or go up,
never down. Export a JSON backup before big updates anyway (Settings → Export everything).

**Changing phones:** old phone Settings → *Export everything (JSON)* → move the file → new phone Settings →
*Import backup (JSON)*. It restores every transaction, rule, note and category.

---

## 4. How it works

```
core/   pure Kotlin, no Android, fast unit tests
  parse/       SmsParser (pipeline), BankRules (regex rule list), Filters (OTP/promo/balance/failed/reminder),
               Extract (amount, account, ref, VPA, merchant), Dates, Senders, MerchantNames
  categorize/  Categories, SeedRules (merchant keyword → category), Categorizer
  analysis/    Dedupe, RecurringDetector, BillCalendar, Summaries
  format/      Inr (₹1,23,456 formatting)
app/    Android: Compose UI, Room database, SMS receiver, WorkManager sync, notifications, backup
  ui/common/SpendMath.kt   same-days comparison, Bank/Cards split, budget, trend series (unit-tested in app/src/test)
```

- **Live SMS**: `SmsReceiver` parses each new SMS, stores it, and notifies "₹450 at Swiggy → Food & Dining".
- **Inbox scans**: on first run and from "Rescan inbox" (whole inbox), plus every ~6 hours (last few days) to catch
  anything missed. Already-imported SMS are skipped by hash; previously unparsed SMS are re-checked.
- **Ignored**: OTPs, promotions, balance-only alerts, failed/declined transactions, collect requests, bill/statement
  reminders, "will be debited" mandate notices.
- **Duplicates** (same payment as a bank SMS and a card/UPI SMS): same direction and amount, and either the same
  reference/UPI ref, or (without refs) within 2 minutes on the same account.
- **Credit-card bill payments** are excluded from totals automatically (otherwise you'd count every card purchase twice).
- **Bank accounts vs credit cards**: a card is a credit card when an SMS says so ("Credit Card", "Avl Limit",
  "BLOCK CC", a card-only issuer like SBI Card or Amex). Once any SMS calls card ••5678 a credit card, its plain
  "Card 5678" messages count as credit card too. Debit cards and cash count with bank accounts.
- **Budget**: one monthly amount for everything. "Where the month is heading" adds your everyday rate (food,
  groceries, travel…) for the remaining days to what's spent so far; rent, SIPs, bills and transfers aren't
  extrapolated, because they come once a month.
- **Categories**: your rules → refund/income for credits → ATM → built-in merchant keywords → person-to-person UPI
  → Other. Changing a category with "Apply to all from this merchant" saves a rule and updates past transactions.

---

## 5. `samples.txt`: test the parser against your real SMS

Create `samples.txt` in this folder (it's git-ignored, so it never gets pushed). Paste one SMS per block:

```
From: VM-HDFCBK
Sent Rs.450.00
From HDFC Bank A/C *1234
To SWIGGY
On 05/10/25
Ref 527812345678
=> debit 450.00 merchant=Swiggy acct=1234 ref=527812345678

Dear UPI user A/C X1234 debited by 450.0 on date 05Oct25 trf to SWIGGY Refno 527812345678 -SBI

123456 is OTP for txn of INR 1,234.00 at AMAZON ...
=> ignore
```

- Separate messages with an empty line or `---`. Lines starting with `#` are comments.
- `From:` (sender ID) and `=> …` (expected result) are optional. Without `=>`, a message only fails if it can't be
  read at all. Use `_` for spaces in values: `merchant=John_Doe`.
- Run `.\gradlew test`. Every message's result is in `core\build\reports\samples-report.txt`.

The **Unparsed** screen's *Copy all* / *Share* produces exactly this format.

---

## 6. Adding a new bank SMS format

1. Add the message as a test first, in `core/src/test/kotlin/app/moneymanager/core/BankRulesTest.kt`:
   ```kotlin
   @Test fun mybank_upi_debit() {
       txn("VM-MYBANK", "Rs 450.00 paid to SWIGGY from A/c XX1234 ref 527812345678")
           .check(DEBIT, 450.0, account = "1234", merchant = "Swiggy", ref = "527812345678")
   }
   ```
2. Run `.\gradlew test`. The generic extractors often handle it already. If not, add an `SmsRule` to
   `core/src/main/kotlin/app/moneymanager/core/parse/BankRules.kt`:
   ```kotlin
   SmsRule(
       "mybank_upi_paid",
       """\bpaid\s+to\s+(?<merchant>.+?)\s+from\s+a/c\s*$ACCT""" ,   // named groups: amount, account, merchant, vpa, ref, balance
       type = TxnType.DEBIT,
       sender = "MYBANK",                                          // optional: only for this sender header
   ),
   ```
   Rules are tried in order and the first match wins. Anything a rule doesn't capture is filled in by the generic
   extractors, so a rule usually only needs to pin down the merchant. The `$AMT`, `$ACCT` and `$DATE` fragments are
   at the top of `BankRules`.
3. Avoid regex tricks that desktop Java accepts but Android's ICU engine rejects (e.g. `\b?`). If you have an
   emulator, `.\gradlew connectedDebugAndroidTest` runs the same tests on Android (see the warning in section 1).
4. Rebuild, install over the old app, then **Settings → Rescan inbox**: previously unparsed SMS are picked up.

New merchant → category keywords go in `core/.../categorize/SeedRules.kt`, or add them in the app (Settings → Rules).

---

## 7. Plan B: build on GitHub instead

If the local build ever stops working, GitHub can build the APK for you with `.github/workflows/build.yml`.
The code lives at https://github.com/atharvgk/money_manager. Every push to `main` runs the tests and builds
`app-debug.apk`. Once you've added your signing key as a secret (below), it also publishes the APK as a GitHub Release.

Never committed (see `.gitignore`): the signing key, `samples.txt`, app exports, `local.properties`, build output.

**One-time: give GitHub Actions your signing key, privately.** Without this, Actions builds can't update the app
on your phone, so they aren't published.
1. In PowerShell, in this folder, copy the key to the clipboard as text:
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore\debug.keystore")) | Set-Clipboard
   ```
2. On GitHub: repository → **Settings → Secrets and variables → Actions → New repository secret**.
   Name: `SIGNING_KEYSTORE_BASE64`, Secret: paste. Save. (Secrets are encrypted and never shown in logs, even on a public repo.)
3. **Actions** tab → "Build APK" → **Run workflow** (or push any change). Wait for green (about 5–8 minutes).
4. Open **Releases** on the repository page from your phone, download `app-debug.apk` and install it as in section 2A.
   Every later push produces a new release that installs over the previous one.

**Pushing changes:** `git add -A`, then `git commit -m "what changed"`, then `git push`.

---

## Troubleshooting

- **Build errors like "unable to delete file" / "file in use"**: this folder is inside OneDrive. Pause OneDrive
  syncing while building, or move the project out of OneDrive (e.g. `C:\dev\money_manager`).
- **Downloads time out** during the first build: `gradle.properties` already forces IPv4, which fixed this on this PC.
  Re-run the command; downloads resume.
- **No new transactions appearing**: check Settings → *SMS access* and *Battery optimisation*, then *Rescan inbox*.
- **A bank SMS wasn't picked up**: Settings → *Unparsed messages* → *Copy all*, add them to `samples.txt` (section 5).
