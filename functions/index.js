/**
 * CHECKOT — AI Car-Check Cloud Function
 *
 * A Callable function the Android app invokes with a car photo. It relays the
 * image to Gemini and returns a simple verdict + one-line reason.
 *
 * The Gemini API key NEVER ships in the app. It lives only in Secret Manager
 * as GEMINI_KEY and is read here on the server. Set it with:
 *   firebase functions:secrets:set GEMINI_KEY --project checkot-14700
 */

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");

admin.initializeApp();

// The Gemini API key, read from Secret Manager (never hard-coded).
const GEMINI_KEY = defineSecret("GEMINI_KEY");

// Anti-abuse: cap how many checks one signed-in user can run per day, so a
// shared APK can't spam the Gemini API. Enforced server-side (the app can't
// bypass it). Counts are kept in Firestore /ai_usage/{uid}_{day}; the Admin SDK
// bypasses security rules, and clients are denied that collection by default.
const DAILY_LIMIT = 10;

// Calendar day in Manila (UTC+8) as YYYY-MM-DD, so the quota resets at local
// midnight rather than 8am.
function manilaDay() {
  return new Date(Date.now() + 8 * 60 * 60 * 1000).toISOString().slice(0, 10);
}

// Reads how many checks the caller has already used today. Cheap read used to
// reject over-limit callers BEFORE spending a Gemini call.
async function getUsedToday(uid) {
  const db = admin.firestore();
  const ref = db.collection("ai_usage").doc(`${uid}_${manilaDay()}`);
  const snap = await ref.get();
  return snap.exists ? (snap.data().count || 0) : 0;
}

// Atomically increments the caller's daily count. Called only AFTER a
// successful diagnosis, so failed/timed-out calls don't burn the user's quota.
// Returns the new count so we can tell the user how many checks remain.
async function recordUsage(uid) {
  const db = admin.firestore();
  const ref = db.collection("ai_usage").doc(`${uid}_${manilaDay()}`);
  let newCount = 0;
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const count = snap.exists ? (snap.data().count || 0) : 0;
    newCount = count + 1;
    tx.set(
      ref,
      {
        uid,
        day: manilaDay(),
        count: newCount,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true },
    );
  });
  return newCount;
}

// Cheap, image-capable, current-gen (3.x) model. We use 3.1-flash-lite, NOT
// 3.5: the 3.5/3.6 flash-lite line forces heavy "thinking" that makes it take
// 80-90s+ even on a one-word prompt (measured: gemini-flash-lite-latest = 81.9s
// on plain text) and it timed out. 3.1-flash-lite is the same 3.x generation,
// answers in <1s, and has good longevity. This one string swaps the model.
const MODEL = "gemini-3.1-flash-lite";

const GEMINI_URL =
  `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent`;

// What we ask Gemini to judge. Kept tight to the agreed scope: cleanliness only
// (exterior body + visible seats). Scratches, dents, and other damage are
// explicitly OUT of scope so they aren't mistaken for dirt.
const PROMPT = `You are a car-wash assistant. Look ONLY at the car's exterior ` +
  `body/paint and any visible interior seats, and judge ONLY how dirty it is. ` +
  `"Dirty" means removable grime a wash would clean off — dust, mud, road ` +
  `film, stains, bird droppings, or water spots. ` +
  `Do NOT treat scratches, scuffs, swirl marks, dents, paint chips, fading, ` +
  `rust, or any physical/cosmetic damage as dirt — those are OUTSIDE what you ` +
  `assess. A car that is scratched or damaged but not dirty is "Clean". ` +
  `Ignore the engine and mechanical parts entirely. ` +
  `If the image does not clearly show a car, use the "Not a car" verdict. ` +
  `If the main thing you notice is scratches or damage rather than dirt, judge ` +
  `the dirtiness on its own and add a brief note in your reason that ` +
  `scratches/damage are outside this cleanliness check. ` +
  `Keep the reason to one short, friendly sentence a customer would understand.`;

exports.checkCar = onCall(
  {
    region: "asia-southeast1",
    secrets: [GEMINI_KEY],
    // Generous timeout so a slow Gemini response (or cold start) doesn't get
    // killed mid-call. Small memory + capped instances keep cost in check.
    memory: "256MiB",
    timeoutSeconds: 120,
    maxInstances: 5,
  },
  async (request) => {
    // Only signed-in users may call this (the app is always authenticated).
    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "Please sign in to use the car check.",
      );
    }

    const data = request.data || {};
    const imageBase64 = data.imageBase64;
    const rawMime = data.mimeType;

    if (typeof imageBase64 !== "string" || imageBase64.length === 0) {
      throw new HttpsError("invalid-argument", "No car photo was provided.");
    }

    // Guardrail: reject oversized payloads before spending a Gemini call.
    // ~7M base64 chars ≈ a ~5 MB image, which is plenty for a phone photo.
    if (imageBase64.length > 7_000_000) {
      throw new HttpsError(
        "invalid-argument",
        "That photo is too large. Please use a smaller image.",
      );
    }

    const mimeType =
      typeof rawMime === "string" && rawMime.startsWith("image/")
        ? rawMime
        : "image/jpeg";

    // Reject over-limit callers before spending a Gemini call. The count itself
    // is only incremented after a successful diagnosis (see recordUsage below),
    // so timeouts/errors never eat the user's daily quota.
    if ((await getUsedToday(request.auth.uid)) >= DAILY_LIMIT) {
      throw new HttpsError(
        "resource-exhausted",
        `You've reached today's limit of ${DAILY_LIMIT} car checks. ` +
          `Please try again tomorrow.`,
      );
    }

    const body = {
      contents: [
        {
          parts: [
            { text: PROMPT },
            { inline_data: { mime_type: mimeType, data: imageBase64 } },
          ],
        },
      ],
      generationConfig: {
        temperature: 0.2,
        // Ask Gemini to reply as strict JSON matching this shape, so the app
        // always gets a clean { verdict, reason } with no parsing surprises.
        responseMimeType: "application/json",
        responseSchema: {
          type: "object",
          properties: {
            verdict: {
              type: "string",
              enum: ["Clean", "Lightly dirty", "Needs a wash", "Not a car"],
            },
            reason: { type: "string" },
          },
          required: ["verdict", "reason"],
        },
      },
    };

    let response;
    const controller = new AbortController();
    const abortTimer = setTimeout(() => controller.abort(), 90_000);
    const t0 = Date.now();
    try {
      response = await fetch(GEMINI_URL, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-goog-api-key": GEMINI_KEY.value(),
        },
        body: JSON.stringify(body),
        signal: controller.signal,
      });
      console.log(`Gemini responded in ${Date.now() - t0}ms (${MODEL})`);
    } catch (err) {
      console.error(
        `Network/timeout error calling Gemini after ${Date.now() - t0}ms:`,
        err,
      );
      throw new HttpsError(
        "unavailable",
        "The AI took too long to respond. Please try again.",
      );
    } finally {
      clearTimeout(abortTimer);
    }

    if (!response.ok) {
      const errText = await response.text();
      console.error("Gemini returned", response.status, errText);
      throw new HttpsError(
        "internal",
        "The AI service returned an error. Please try again.",
      );
    }

    const json = await response.json();
    const text =
      json &&
      json.candidates &&
      json.candidates[0] &&
      json.candidates[0].content &&
      json.candidates[0].content.parts &&
      json.candidates[0].content.parts[0] &&
      json.candidates[0].content.parts[0].text;

    if (!text) {
      console.error("Empty Gemini response:", JSON.stringify(json));
      throw new HttpsError(
        "internal",
        "The AI could not analyze that photo. Please try another.",
      );
    }

    let parsed;
    try {
      parsed = JSON.parse(text);
    } catch (err) {
      console.error("Gemini returned non-JSON:", text);
      throw new HttpsError(
        "internal",
        "The AI returned an unexpected response. Please try again.",
      );
    }

    // Success — now count it against the daily quota.
    const usedCount = await recordUsage(request.auth.uid);
    const remaining = Math.max(0, DAILY_LIMIT - usedCount);

    return {
      verdict: String(parsed.verdict || "Not a car"),
      reason: String(parsed.reason || ""),
      remaining: remaining,
      dailyLimit: DAILY_LIMIT,
    };
  },
);
