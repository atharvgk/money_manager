<#
  Fake bank SMS for the emulator started by .\tools\emulator.ps1.

      .\tools\send-sms.ps1                       one random bank SMS arriving "live" (you'll get the app's notification)
      .\tools\send-sms.ps1 -From HDFCBK -Text "Rs.450.00 debited from a/c **1234 on 05-10-25 to VPA swiggy@axisbank(UPI Ref No 527812345678)"
      .\tools\send-sms.ps1 -File samples.txt     run every message from your samples.txt through the app
                                                 (unreadable ones show up in Settings -> Unparsed messages)
      .\tools\send-sms.ps1 -History              ~3 months of realistic sample bank SMS, so Compare and
                                                 Bills have something to show (emulator.ps1 -Seed does this)

  -File and -History use a hook that exists only in debug builds and only works on the emulator: it runs each
  message through exactly the same parsing as an inbox scan, but with a date of our choosing. (The emulator
  itself can only deliver SMS dated "now", which is what plain .\tools\send-sms.ps1 does.)
#>
param([string]$From = "", [string]$Text = "", [string]$File = "", [switch]$History)

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$inv = [Globalization.CultureInfo]::InvariantCulture
if (-not (@(& $adb devices) -match "^emulator-\d+\s+device")) {
    Write-Error "No emulator running. Start it first: .\tools\emulator.ps1"
    exit 1
}

$script:refCounter = [int](Get-Date -UFormat %H%M%S)
function New-Ref { $script:refCounter++; return "6278" + $script:refCounter.ToString("D8") }

# Fills {DMY} {DMY4} {DSL} {DMON} {DMONY} {DM} {MDY} {ISO} {TIME} {REF} {AMT} placeholders.
function Format-Sms([string]$template, [datetime]$when, [string]$amount = "") {
    $t = $template
    $t = $t.Replace("{DMY4}", $when.ToString("dd-MM-yyyy", $inv)).Replace("{DMY}", $when.ToString("dd-MM-yy", $inv))
    $t = $t.Replace("{DSL}", $when.ToString("dd/MM/yy", $inv)).Replace("{DMONY}", $when.ToString("dd-MMM-yy", $inv))
    $t = $t.Replace("{DMON}", $when.ToString("ddMMMyy", $inv)).Replace("{DM}", $when.ToString("dd-MM", $inv))
    $t = $t.Replace("{MDY}", $when.ToString("MMM dd, yyyy", $inv)).Replace("{ISO}", $when.ToString("yyyy-MM-dd", $inv))
    $t = $t.Replace("{TIME}", $when.ToString("HH:mm:ss", $inv)).Replace("{AMT}", $amount)
    while ($t.Contains("{REF}")) { $i = $t.IndexOf("{REF}"); $t = $t.Substring(0, $i) + (New-Ref) + $t.Substring($i + 5) }
    return $t
}

# Runs a message with any date through the app's SMS pipeline (debug build + emulator only, no notification).
function Add-Past([string]$sender, [string]$body, [datetime]$when) {
    $ms = ([DateTimeOffset]$when).ToUnixTimeMilliseconds()
    $b = $body.Replace("`r", "").Replace("`n", "\n").Replace('"', "").Replace([string][char]0x20B9, "Rs.").Replace("'", "'\''")
    $cmd = "am broadcast -f 32 -n app.moneymanager/.debug.FakeSmsReceiver --es from '$sender' --es body '$b' --el time $ms"
    $out = & $adb shell $cmd 2>&1 | Out-String
    if ($out -notmatch "Broadcast completed") { Write-Warning "Not delivered: $($out.Trim())" }
}

# Delivers a message the way a real SMS arrives, so the app's live receiver and notification run.
function Send-Live([string]$sender, [string]$body) {
    $b = ($body -replace "\r?\n", " ").Replace('"', "").Replace([string][char]0x20B9, "Rs.")
    $s = $sender -replace "[^A-Za-z0-9]", ""
    & $adb emu sms send $s $b | Out-Null
}

# ------------------------------------------------------------------ live: one random message (or -From/-Text)
$live = @(
    @("VM-HDFCBK", "Rs.{AMT}.00 debited from a/c **1234 on {DMY} to VPA swiggy@axisbank(UPI Ref No {REF}). Not you? Call on 18002586161 to report"),
    @("JD-HDFCBK", "Spent Rs.{AMT}.00 On HDFC Bank Card 4321 At UBER INDIA On {ISO}:{TIME} Bal Rs.45,678.00 Not You? Call 18002586161/SMS BLOCK DC 4321 to 7308080808"),
    @("VM-KOTAKB", "Sent Rs.{AMT}.00 from Kotak Bank AC X7788 to blinkit@axisbank on {DMY}.UPI Ref {REF}. Not you, https://kotak.com/KBANKT/Fraud"),
    @("BZ-SBIUPI", "Dear UPI user A/C X4321 debited by {AMT}.0 on date {DMON} trf to ZOMATO LTD Refno {REF}. If not u? call 1800111109. -SBI"),
    @("VK-ICICIB", "INR {AMT}.00 spent using ICICI Bank Card XX5678 on {DMONY} on AMAZON. Avl Limit: INR 1,00,000.00. If not you, call 1800 2662."),
    @("AX-AXISBK", "INR {AMT}.00 debited A/c no. XX9876 {DMY}, {TIME} UPI/P2M/{REF}/RAPIDO Not you? SMS BLOCKUPI Cust ID to 919951860002 Axis Bank")
)

if ($Text) {
    $sender = if ($From) { $From } else { "VM-HDFCBK" }
    Send-Live $sender $Text
    Write-Host "Sent from $sender. Watch for the app's notification in the emulator." -ForegroundColor Green
    exit 0
}

if ($File) {
    if (-not (Test-Path $File)) { Write-Error "$File not found"; exit 1 }
    $blocks = New-Object System.Collections.Generic.List[object]
    $buf = New-Object System.Collections.Generic.List[string]
    foreach ($raw in @(Get-Content $File -Encoding UTF8) + @("")) {
        $line = $raw.TrimEnd()
        if ($line.Trim() -eq "" -or $line.Trim() -eq "---") {
            $lines = @($buf | Where-Object { -not $_.TrimStart().StartsWith("#") -and -not $_.TrimStart().StartsWith("=>") })
            if ($lines.Count -gt 0) {
                $sender = "BANKSMS"
                if ($lines[0] -match "^From:\s*(.+)$") { $sender = $Matches[1].Trim(); $lines = @($lines | Select-Object -Skip 1) }
                if ($lines.Count -gt 0) { $blocks.Add(@($sender, ($lines -join "`n"))) }
            }
            $buf.Clear()
        } else { $buf.Add($line) }
    }
    # Spread them over the last few minutes; 3 minutes apart so the 2-minute duplicate check doesn't merge them.
    $when = (Get-Date).AddMinutes(-3 * $blocks.Count)
    foreach ($b in $blocks) { Add-Past $b[0] $b[1] $when; $when = $when.AddMinutes(3) }
    Write-Host "Sent $($blocks.Count) messages to the app. Check Transactions, and Settings -> Unparsed messages." -ForegroundColor Green
    exit 0
}

if (-not $History) {
    $pick = $live[(Get-Random -Maximum $live.Count)]
    $amount = (Get-Random -Minimum 60 -Maximum 1500).ToString()
    Send-Live $pick[0] (Format-Sms $pick[1] (Get-Date) $amount)
    Write-Host "Sent a live SMS from $($pick[0]). Watch for the app's notification in the emulator." -ForegroundColor Green
    exit 0
}

# ------------------------------------------------------------------ history: ~3 months of realistic messages
$now = Get-Date
$monthly = @(
    # day, hour, sender, template, amount
    @(1, 9, "AX-AXISBK", "INR {AMT}.00 credited A/c no. XX9876 {DMY}, {TIME} IST NEFT/ACME CORP PVT LTD/SALARY Not you? SMS BLOCK to 919951860002 Axis Bank", "85000"),
    @(2, 10, "AX-AXISBK", "INR {AMT}.00 debited A/c no. XX9876 {DMY}, {TIME} UPI/P2A/{REF}/RAMESH KUMAR Not you? SMS BLOCKUPI Cust ID to 919951860002 Axis Bank", "18000"),
    @(5, 8, "VK-ICICIB", "ICICI Bank Credit Card XX5678 has been used for a transaction of INR {AMT}.00 on {MDY} at {TIME}. Info: NETFLIX. The Available Credit Limit on your card is INR 98,765.00", "649"),
    @(8, 11, "BZ-SBIUPI", "Dear UPI user A/C X4321 debited by {AMT}.0 on date {DMON} trf to AIRTEL Refno {REF}. If not u? call 1800111109. -SBI", "399"),
    @(10, 7, "VM-HDFCBK", "UPDATE: Your A/c XX1234 debited for INR {AMT}.00 on {DMONY}. Info: ACH D- GROWW-SIP-12345678. Avl bal:INR 52,000.00", "5000"),
    @(12, 21, "VM-HDFCBK", "Txn Rs.{AMT}.00`nOn HDFC Bank Card 7788`nAt SPOTIFY`nby UPI {REF}`nOn {DM}`nNot You?`nCall 18002586161/SMS BLOCK CC 7788 to 7308080808", "119"),
    @(18, 19, "VM-HDFCBK", "Rs.{AMT}.00 debited from a/c **1234 on {DMY} to VPA cred.club@axisbank(UPI Ref No {REF}). Not you? Call on 18002586161 to report", "12000"),
    @(18, 19, "VK-ICICIB", "Dear Customer, Payment of INR {AMT}.00 has been received on your ICICI Bank Credit Card Account XX5678 on {DMONY}. Thank you.", "12000")
)
$spends = @(
    @(3, 20, "VM-HDFCBK", "Rs.{AMT}.00 debited from a/c **1234 on {DMY} to VPA swiggy@axisbank(UPI Ref No {REF}). Not you? Call on 18002586161 to report", 349),
    @(9, 13, "VM-HDFCBK", "Sent Rs.{AMT}.00`nFrom HDFC Bank A/C *1234`nTo SWIGGY`nOn {DSL}`nRef {REF}`nNot You?`nCall 18002586161/SMS BLOCK UPI to 7308080808", 512),
    @(16, 21, "VM-HDFCBK", "Rs.{AMT}.00 debited from a/c **1234 on {DMY} to VPA swiggy@axisbank(UPI Ref No {REF}). Not you? Call on 18002586161 to report", 278),
    @(6, 20, "BZ-SBIUPI", "Dear UPI user A/C X4321 debited by {AMT}.0 on date {DMON} trf to ZOMATO LTD Refno {REF}. If not u? call 1800111109. -SBI", 389),
    @(20, 22, "BZ-SBIUPI", "Dear UPI user A/C X4321 debited by {AMT}.0 on date {DMON} trf to ZOMATO LTD Refno {REF}. If not u? call 1800111109. -SBI", 455),
    @(4, 18, "VM-KOTAKB", "Sent Rs.{AMT}.00 from Kotak Bank AC X7788 to blinkit@axisbank on {DMY}.UPI Ref {REF}. Not you, https://kotak.com/KBANKT/Fraud", 612),
    @(14, 19, "VM-KOTAKB", "Sent Rs.{AMT}.00 from Kotak Bank AC X7788 to blinkit@axisbank on {DMY}.UPI Ref {REF}. Not you, https://kotak.com/KBANKT/Fraud", 238),
    @(25, 17, "VM-KOTAKB", "Sent Rs.{AMT}.00 from Kotak Bank AC X7788 to zepto@ybl on {DMY}.UPI Ref {REF}. Not you, https://kotak.com/KBANKT/Fraud", 845),
    @(7, 9, "JD-HDFCBK", "Spent Rs.{AMT}.00 On HDFC Bank Card 4321 At UBER INDIA On {ISO}:{TIME} Bal Rs.45,678.00 Not You? Call 18002586161/SMS BLOCK DC 4321 to 7308080808", 186),
    @(15, 9, "JD-HDFCBK", "Spent Rs.{AMT}.00 On HDFC Bank Card 4321 At UBER INDIA On {ISO}:{TIME} Bal Rs.44,100.00 Not You? Call 18002586161/SMS BLOCK DC 4321 to 7308080808", 243),
    @(11, 8, "AX-AXISBK", "INR {AMT}.00 debited A/c no. XX9876 {DMY}, {TIME} UPI/P2M/{REF}/RAPIDO Not you? SMS BLOCKUPI Cust ID to 919951860002 Axis Bank", 74),
    @(13, 18, "AD-SBICRD", "Rs.{AMT}.00 spent on your SBI Credit Card ending 9012 at HPCL PETROL PUMP on {DSL}. Trxn. not done by you? Report at https://sbicard.com/Dispute", 2000),
    @(19, 15, "VK-ICICIB", "INR {AMT}.00 spent using ICICI Bank Card XX5678 on {DMONY} on AMAZON. Avl Limit: INR 1,00,000.00. If not you, call 1800 2662.", 1299),
    @(22, 12, "AX-AXISBK", "Your A/c no. XX9876 has been debited with INR {AMT}.00 on {DMY4} {TIME} at APOLLO PHARMACY. Avl Bal-INR 40,210.00", 540),
    @(17, 18, "VM-SBIINB", "Dear Customer, Rs.{AMT} withdrawn at SBI ATM S1BW000123 from A/cX4321 on {DMON} Transaction Number 4471. Available Balance Rs 21,000.00 -SBI", 3000),
    @(21, 20, "AX-AXISBK", "INR {AMT}.00 debited A/c no. XX9876 {DMY}, {TIME} UPI/P2A/{REF}/PRIYA SINGH Not you? SMS BLOCKUPI Cust ID to 919951860002 Axis Bank", 500),
    @(24, 21, "VM-HDFCBK", "Rs.{AMT}.00 credited to HDFC Bank A/c XX1234 on {DMY} from VPA rahul.verma@okicici (UPI {REF})", 750)
)
$extras = @(
    # days ago, sender, text: things the app must ignore or flag, so you can see that it does
    @(0, "VM-HDFCBK", "482913 is OTP for txn of INR 2,499.00 at FLIPKART on HDFC Bank card ending 4321. Do not share OTP for security reasons"),
    @(1, "VM-HDFCBK-P", "Get a Personal Loan of up to Rs 10 lakh in 10 seconds! Apply now: https://hdfc.bank/pl"),
    @(2, "VM-SBIINB", "Dear Customer, the Avl Bal in your A/c XX4321 is Rs 21,000.00 on {DSL}. -SBI"),
    @(3, "VM-HDFCBK", "Your UPI transaction of Rs 450.00 to swiggy@axisbank from A/c XX1234 has failed. Ref {REF}"),
    @(1, "VM-HDFCBK", "A/c XX1234 txn alert: INR 999.00 @ BIGBAZAAR {DMY}")
)

$count = 0
for ($m = 2; $m -ge 0; $m--) {
    $monthStart = (Get-Date -Day 1).Date.AddMonths(-$m)
    foreach ($e in $monthly + $spends) {
        $day = [int]$e[0]
        if ($e[4] -is [string]) {
            $amount = $e[4]                                  # bills, rent, salary: same amount every month
        } else {
            # Everyday spending: shift the day by up to +/-3 and the amount by up to +/-40% each month.
            $day = [Math]::Max(1, $day + (($m * 5 + $day) % 7) - 3)
            $amount = [string][Math]::Round([int]$e[4] * (0.6 + (($m * 37 + $day * 11) % 9) * 0.1))
        }
        $when = $monthStart.AddDays($day - 1).AddHours($e[1]).AddMinutes((Get-Random -Maximum 50))
        if ($when -gt $now) { continue }
        Add-Past $e[2] (Format-Sms $e[3] $when $amount) $when
        $count++
    }
}
foreach ($x in $extras) {
    $when = $now.AddDays(-$x[0]).AddHours(-1)
    Add-Past $x[1] (Format-Sms $x[2] $when) $when
    $count++
}
Write-Host "Sent $count sample bank SMS covering the last 3 months to the app." -ForegroundColor Green
