# CHECKOT — Feature Build Plan

Planning document for the remaining professor-requested features. **No code is
written from this doc until each item is confirmed.** Build order is smallest-risk
first so the app never breaks between milestones.

Grounded in the current codebase:
- Colors: `ui/theme/Color.kt` + `Theme.kt` (one fixed dark navy scheme, teal primary).
- Booking capacity: `ShopCustomization.bayCount` gated by `BookingUtils` + the atomic
  `DaySlotLedger`. **No staff concept exists yet.**
- Booking already stores a status timeline: `createdAt`, `confirmedAt`, `inProgressAt`,
  `completedAt`, `cancelledAt` (server timestamps).

---

## Build order (each milestone is independently shippable)

1. **Theme unification** — pure visual, zero logic risk.
2. **Staff (display only)** — additive field, does not touch bay math.
3. **Add-ons (price-only)** — additive, bounded so the ledger stays safe.
4. **Checkout + payment method + e-receipt + payment verification** — presents 2 & 3.
5. **AI car check** — last, behind a relay.

---

## 1. Theme unification (pale vs bright teal)

**Problem (diagnosed):** Screens already use `MaterialTheme.colorScheme` tokens, not
rogue hex. The inconsistency is two things:
- **Alpha dimming:** `.copy(alpha = 0.3f/0.5f/0.6f)` on teal (e.g. status chips in
  `OwnerBookingsTab.kt:327-330`) → washed-out "pale" look the professor dislikes.
- **Two teals used interchangeably:** `CheckotTeal` `0xFF00BFA5` (bright, mapped to
  `primary`) vs `CheckotTealDark` `0xFF00ACC1` (duller cyan, mapped to `secondary` /
  `primaryContainer`). Same visual element uses different tokens across screens.

**Plan:**
- Audit pass: list every teal usage + its token + alpha across all screens.
- Canonical rule: primary actions & accents = `primary` (bright) at full opacity;
  reduced alpha only for genuinely-muted secondary text.
- Fix by swapping tokens/alpha to match the rule. No new colors, no model changes.

**Decision needed:** should status chips (Pending/Confirmed/…) stay dimmed as subtle
badges, or go full brightness? (Professor's taste call.)

---

## 2. Staff on services (display only)

**Professor's ask:** "the client can see who is the person servicing his car" = a
**label**, not a capacity rule.

**The trap (avoided):** If staff *limited* concurrency (2 staff → only 2 cars even with
3 bays), capacity becomes `min(bays, staff)` and we'd have to rewrite the ledger. **We
do NOT do this.** The "3 bays but 2 staff breaks it" fear only exists under that design.

**Plan (Option A — safe):**
- Shop keeps a small `staff` list (owner types names in Owner settings; no staff logins).
- Add `servicedBy` (staff name/id) to `Booking`.
- Owner picks the staff when moving a booking to **In Progress**.
- Client sees "Serviced by: ___" on booking details + receipt.
- **Bay math untouched.** Nothing can break.

**Decision needed:** confirm Option A (display only). If the professor later insists staff
should *cap* concurrency, that's a separate, carefully-tested change — not now.

---

## 3. Add-ons during service (price-only)

**Professor's ask:** add extra services once the car is being serviced.

**The trap (bounded):** extending service *duration* mid-service can overrun the bay
window the next booking already reserved in the ledger → collision.

**Plan:**
- When a booking is **In Progress**, owner adds an add-on from the shop's service list.
- Add-on **appends a line item and increases `price`.** ✅ safe.
- Add-ons **add money, not time** — they do NOT extend the reserved bay window. This keeps
  the ledger honest so add-ons can never eat into the next car's slot.
  - Optional later: allow a time extension *only* if `hasFreeBay` shows the slot is free.
- Client sees the add-on + new total; revenue reporting picks it up automatically (reads
  `price`).

**Decision needed:** confirm the "price-only, don't extend bay time" rule is acceptable.

---

## 4. Checkout + payment + e-receipt + payment verification

**Goal:** make the app look legitimate (Shopee-style cart → confirm → receipt) **and**
survive a panel grilling on "how do you verify a cash transaction happened without you
there?"

### 4a. Checkout flow (extends `BookServiceScreen`)
- Add a **Review & Confirm** step: order summary (services + add-ons, each price, total),
  shop + date/time + car, payment method picker, "Place Booking".

### 4b. New `Booking` fields (additive, safe)
- `paymentMethod: String` — **Cash only** (app handles cash only).
- `paymentStatus: String` = `"unpaid" → "paid"`, flipped **by the owner**,
  **server-timestamped** (`paidAt`).
- `confirmationCode: String` — short code shown to the customer at booking time.

### 4c. Payment verification (the panel-proof part)
The premise "the app must witness the cash" is a trap — **no booking app witnesses cash.**
Grab/Foodpanda cash-on-delivery all rely on the merchant confirming. The app is a
**verifiable shared record**, not a payment processor. Mechanisms, strongest first:

1. **Owner marks "Paid"** (authority + server timestamp + attributed to shop account).
2. **Confirmation code** — required to complete/pay a booking. Only the real customer has
   it → **proves physical presence.** This is the key anti-grill mechanism.
3. **Two-sided confirmation** — customer sees "Marked paid by shop" and can confirm or
   dispute. A payment record needs the shop to assert it *and* the customer not to dispute.
4. *(Optional polish)* capture amount received / change / note for a POS-style record.

### 4d. E-receipt
- Generated **only after the shop confirms payment** → the receipt is the artifact proving
  the shop acknowledged the cash.
- Contents: receipt # (reuse `bookingId`), shop name/logo, date, line items + total,
  payment method + paid/unpaid, **"Serviced by,"** and a **status timeline** built from the
  existing server timestamps (Booked → Confirmed → In Progress → Paid → Completed).
- Optional export: share/save as image or PDF (nice-to-have; in-app screen alone sells it).

---

## 5. AI car check (last)

**Professor's ask:** client photographs their car → AI recommends whether it needs a
service (exterior scratches/dents + interior seats; **not** engines).

**Architecture — the API key must NEVER ship in the APK:**
```
[Android app] → photo → [Cloudflare Worker relay] → [Google Gemini 2.5 Flash]
                                 ↑ key lives ONLY here
```
- **Relay = Cloudflare Worker (free, no card).** Avoids the Blaze plan entirely.
- **No model training** — Gemini is pre-trained; just send photo + prompt.
- **$0** on Cloudflare + Gemini free tiers.
- Swappable to Claude later by editing only the Worker (app never changes).

**Flow:** pick/take photo → compress (~1024px, JPEG ~80%) → POST to relay → Gemini returns
**structured JSON** → result card → optional "Book this service" tie-in.

**Structured JSON (guardrails live in the prompt):**
```json
{
  "isCarPhoto": true,
  "region": "exterior | interior | unclear",
  "needsService": true,
  "severity": "none | minor | moderate | severe",
  "summary": "short human-readable explanation",
  "suggestedServices": ["Exterior wash", "Scratch buffing"]
}
```
- Only assess body/paint/scratches/dents + interior seats. Refuse engines / non-car photos.
- Always allowed to say `needsService: false` (protects against "AI just upsells").
- Handle 429 quota ("AI busy, try again"), blurry/non-car photos, loading spinner.
- Small disclaimer: "AI suggestion, not a professional inspection."

**Decision needed:** where does "Check my car" live — standalone Home button, or inside the
booking flow as a "not sure what you need?" helper?

---

## 6. Real auth (Google + phone OTP) + MFA

**Ask:** real sign-up/login with verified identity — "login with Gmail," phone-number
SMS OTP, and **required MFA**. Plan only; no code until §6 decisions are locked.

### 6a. Baseline today (`AuthViewModel.kt`)
- Email + password via Firebase Auth. Phone number is **collected but never verified**
  (plain string on the `users` doc). Role in Firestore. **No email verification, no OAuth,
  no MFA.** So this is *adding verified identity + a second factor* on top — not a rewrite.

### 6b. Building blocks
| # | Feature | Firebase mechanism | Free? |
|---|---------|-------------------|-------|
| A | "Login with Gmail" | **Google Sign-In (OAuth)** | ✅ free |
| B | Verified email | verification link (native) *or* custom 6-digit email OTP | link free; OTP needs email service |
| C | Phone sign-up/login (SMS OTP) | **Firebase Phone Auth** | 💰 per-SMS |
| D | Required MFA | Firebase MFA 2nd factor — needs **Identity Platform upgrade** | TOTP free; SMS 2nd factor 💰 |

**Two clarifications:**
- **"Gmail OTP" ≠ a native feature.** Firebase's Gmail path is **Google Sign-In** (one tap,
  Google handles the code). A typed 6-digit email code needs a Cloud Function + email service
  (SendGrid/Mailgun). Recommend **Google Sign-In + email verification link** (free, no infra).
- **MFA requires upgrading the project to *Firebase Auth with Identity Platform*** (one-click
  console toggle; free tier 50K MAU, so still $0).

### 6c. Cost reality (for the team)
- Firebase Auth base: **free to 50K MAU** → $0 for us.
- Google Sign-In, email verification: **free.**
- **Phone/SMS OTP: never free** — ~**$0.01–0.06 per SMS** (region-dependent; PH is mid-range),
  after a **10 SMS/day** free allowance. Requires Blaze (✅ we have it).
- **MFA: TOTP (authenticator app) = free; SMS 2nd factor = per-SMS on every login.**
- **Budget insight:** SMS-based *required* MFA = an SMS charge on **every login** (recurring).
  **TOTP-based MFA = $0 forever.** → strongly prefer **TOTP**.
- Sources: metacto / RapidNative / Logto Firebase-auth-pricing 2026 guides.

### 6d. Decisions to lock (recommendations)
1. **Who must have MFA?** → *Recommend required for **owners & admins**, optional for
   customers.* Owners control shops/revenue, admins approve shops — high-value accounts.
   Forcing every casual customer through MFA is heavy friction (and SMS cost).
2. **MFA factor?** → *Recommend **TOTP** (authenticator app) — free.* SMS only if the
   per-login cost is accepted.
3. **"Gmail" flow?** → *Recommend **Google Sign-In** + email verification.*
4. **Phone auth = sign-in method or just verification?** → *Recommend start with phone-number
   **verification** during signup* (SMS costs money per use).

### 6e. Phased build (after decisions)
| Phase | What | Notes |
|-------|------|-------|
| 0 | Enable providers + **upgrade to Identity Platform**; add test phone numbers | config only |
| 1 | **Google Sign-In** + account linking | biggest UX win, free |
| 2 | **Email verification** gate for email/password signups | free |
| 3 | **Phone number verification** (SMS OTP at signup) | first SMS costs |
| 4 | **MFA enrollment** (TOTP) — enroll + enter-code flows | needs verified 1st factor |
| 5 | **MFA enforcement** — owner/admin at login + in Firestore rules | uses `sign_in_second_factor` claim |
| 6 | **Migration** for existing accounts + testing | see 6g |

### 6f. Data model + rules
- `users` doc gains: `emailVerified`, `phoneVerified`, `authProvider`
  (`password`/`google`/`phone`), `mfaEnrolled`.
- **Firestore rules can enforce MFA** on sensitive actions via
  `request.auth.token.firebase.sign_in_second_factor != null` (e.g. owner advancing bookings,
  admin approving shops) → MFA becomes real, not just UI.
- `AuthViewModel` grows Google/phone/MFA flows; the email+password path stays.

### 6g. Migration & gotchas
- **Existing users** need a one-time "finish securing your account" prompt (verify email;
  owners/admins enroll MFA) on next login.
- **Account linking:** same email via password *and* Google must be **linked** or it errors.
- **Phone-auth anti-abuse:** wire **Play Integrity/reCAPTCHA** + **test phone numbers** so we
  can demo without spending SMS.
- **MFA needs a verified first factor** → Phase 2 must precede Phase 4.
- **10 SMS/day free** will throttle testing → use test numbers.

### 6h. Open questions for professor/team
1. MFA required for **everyone**, or **owners/admins-required, customers-optional**?
2. **TOTP** acceptable, or specifically **SMS** MFA (recurring cost)?
3. Real **6-digit email OTP** needed, or is **Google Sign-In + email verification** enough?

**Recommended shape for a school project:** Google Sign-In + email verification + phone
*verification* + **TOTP MFA required for owners/admins** → a genuine "secure auth with MFA"
demo at **~$0**, no SMS bill per login.

---

## Open decisions checklist (confirm before coding each item)

- [ ] Theme: status chips dimmed or full brightness?
- [ ] Staff: confirm Option A (display only)?
- [ ] Add-ons: confirm price-only (no bay-time extension)?
- [ ] Payment: owner marks paid, or auto on Completed?
- [ ] Receipt: in-app only, or share-as-image/PDF?
- [ ] AI: "Check my car" entry point location?
- [ ] Auth: MFA required for everyone, or owners/admins-required + customers-optional?
- [ ] Auth: MFA factor — TOTP (free) or SMS (per-login cost)?
- [ ] Auth: real email OTP, or Google Sign-In + email verification?
- [ ] Auth: phone as a full sign-in method, or verification-only at signup?

## Before building the AI feature
Send the professor one clarifying message confirming the photo → recommendation scope
matches what he wants, and whether he expects anything specific. Cheap, and it turns
"I hope this is right" into "he confirmed it."
