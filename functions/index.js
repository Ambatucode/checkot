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
const PROMPT = `You are a car-wash assistant. Look at the photo and judge ONLY how ` +
  `dirty the car is. "Dirty" means removable grime a wash or vacuum would clean ` +
  `off — dust, mud, road film, stains, crumbs, bird droppings, or water spots. ` +
  `Do NOT treat scratches, scuffs, swirl marks, dents, paint chips, fading, ` +
  `rust, or any physical/cosmetic damage as dirt — those are OUTSIDE what you ` +
  `assess. A car that is scratched or damaged but not dirty is "Clean". ` +
  `Ignore the engine and mechanical parts entirely. ` +
  `Also decide WHERE the dirt is and set "dirtyArea": ` +
  `"Exterior" if the dirty part is the outside body/paint; ` +
  `"Interior" if the dirty part is the seats/cabin; ` +
  `"Both" if both are clearly visible AND both are dirty; ` +
  `"None" if the car is clean, or the image is not a car. ` +
  `This tool is ONLY for ordinary four-wheeled vehicles that get serviced at ` +
  `a car wash — cars, SUVs, vans, pickups, and jeepneys. OUT of scope: ` +
  `motorcycles, tricycles, tuktuks / auto-rickshaws, bicycles, and other two- ` +
  `or three-wheeled vehicles (even large or enclosed ones); AND very large or ` +
  `heavy vehicles such as cargo trucks (six or more wheels), trailers, and ` +
  `buses, which use specialized wash bays. Ordinary pickups and SUVs stay IN ` +
  `scope. ` +
  `If the image is too blurry, dark, out of focus, heavily pixelated, extremely occluded (e.g. car is mostly covered or blocked by trees/objects), or has lighting/quality so poor that you cannot reliably evaluate whether the car is clean or dirty, set the verdict to "Photo unclear" and set "dirtyArea" to "None". Make the reason explain why it is unclear and politely prompt the user to retake the photo in better lighting or focus, e.g. "The photo is too blurry or dark to analyze. Please take a clearer picture in good lighting." ` +
  `If the image focuses on parts of the car that are outside of your scope — such as the undercarriage/underbody, engine bay, suspension, or mechanical components under the hood or chassis — set the verdict to "Out of scope" and set "dirtyArea" to "None". Make the reason explain that you only assess the cleanliness of the exterior paint and the interior cabin, e.g. "This image shows the undercarriage of a vehicle, which is not part of the exterior paint or interior cabin that I assess for cleanliness." ` +
  `If the image is not an in-scope four-wheeled vehicle — including a ` +
  `motorcycle, tricycle, or tuktuk, a large truck or bus, a person, animal, ` +
  `object, screenshot, meme, or any inappropriate, explicit, or unrelated ` +
  `content — do NOT describe, rate, or engage with what it shows. Instead set ` +
  `verdict "Not a car", dirtyArea "None". For a motorcycle, tricycle, tuktuk, ` +
  `large truck, or bus, make the reason politely explain the tool is for cars ` +
  `and similar everyday four-wheeled vehicles, e.g. "This tool is for cars and ` +
  `similar vehicles — motorcycles, tricycles, and large trucks aren't ` +
  `covered." For anything else, give a short friendly redirect, e.g. "That's ` +
  `not a car — send me a photo of your actual car so I can check if it needs ` +
  `a wash." ` +
  `If the main thing you notice is scratches or damage rather than dirt, judge ` +
  `the dirtiness on its own and add a brief note in your reason that ` +
  `scratches/damage are outside this cleanliness check. ` +
  `Keep the reason to one short, friendly sentence a customer would understand.`;

// Force redeploy to reset instances after billing unfreeze
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
              enum: ["Clean", "Lightly dirty", "Needs a wash", "Not a car", "Photo unclear", "Out of scope"],
            },
            reason: { type: "string" },
            // Where the dirt is, so the app can recommend the matching service
            // (Exterior -> Exterior Wash, Interior -> Interior Vacuum).
            dirtyArea: {
              type: "string",
              enum: ["Exterior", "Interior", "Both", "None"],
            },
          },
          required: ["verdict", "reason", "dirtyArea"],
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
    const candidate = json && json.candidates && json.candidates[0];
    const text =
      candidate &&
      candidate.content &&
      candidate.content.parts &&
      candidate.content.parts[0] &&
      candidate.content.parts[0].text;

    // Gemini's own safety filter blocks explicit/unsafe images: those come back
    // with no text and either a promptFeedback.blockReason (input blocked) or a
    // finishReason that isn't a normal completion. Treat that as a polite
    // "not a car" redirect instead of surfacing a scary error — and still count
    // it below, so bad images can't be spammed past the daily limit for free.
    const blocked =
      (json && json.promptFeedback && json.promptFeedback.blockReason) ||
      (candidate &&
        candidate.finishReason &&
        candidate.finishReason !== "STOP" &&
        candidate.finishReason !== "MAX_TOKENS");

    const REDIRECT_REASON =
      "That doesn't look like a car. Please send a photo of your actual car " +
      "so I can check if it needs a wash.";

    let parsed;
    if (text) {
      try {
        parsed = JSON.parse(text);
      } catch (err) {
        console.error("Gemini returned non-JSON:", text);
        throw new HttpsError(
          "internal",
          "The AI returned an unexpected response. Please try again.",
        );
      }
    } else if (blocked) {
      console.log(
        `Gemini blocked the image (${(json.promptFeedback &&
          json.promptFeedback.blockReason) ||
          (candidate && candidate.finishReason)}). Sending redirect.`,
      );
      parsed = { verdict: "Not a car", dirtyArea: "None", reason: REDIRECT_REASON };
    } else {
      console.error("Empty Gemini response:", JSON.stringify(json));
      throw new HttpsError(
        "internal",
        "The AI could not analyze that photo. Please try another.",
      );
    }

    // Success — now count it against the daily quota.
    const usedCount = await recordUsage(request.auth.uid);
    const remaining = Math.max(0, DAILY_LIMIT - usedCount);

    return {
      verdict: String(parsed.verdict || "Not a car"),
      reason: String(parsed.reason || ""),
      dirtyArea: String(parsed.dirtyArea || "None"),
      remaining: remaining,
      dailyLimit: DAILY_LIMIT,
    };
  },
);

// Cheap, Gemini-free endpoint the app calls when the car-check screen opens, so
// it can show "X of N checks left today" up front instead of only after the
// first check. Reads the same daily counter but never spends a Gemini call.
exports.getCarCheckUsage = onCall(
  {
    region: "asia-southeast1",
    memory: "256MiB",
    timeoutSeconds: 30,
    maxInstances: 5,
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "Please sign in to use the car check.",
      );
    }
    const used = await getUsedToday(request.auth.uid);
    return {
      remaining: Math.max(0, DAILY_LIMIT - used),
      dailyLimit: DAILY_LIMIT,
    };
  },
);

exports.sendPushNotification = onCall(
  {
    region: "asia-southeast1",
    memory: "256MiB",
    timeoutSeconds: 60,
    maxInstances: 10,
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "Please sign in to send notifications.",
      );
    }

    const { targetToken, title, body, data } = request.data || {};

    if (!targetToken) {
      throw new HttpsError(
        "invalid-argument",
        "The targetToken parameter is required.",
      );
    }
    if (!title || !body) {
      throw new HttpsError(
        "invalid-argument",
        "The title and body parameters are required.",
      );
    }

    // Build the FCM payload
    const message = {
      token: targetToken,
      notification: {
        title: title,
        body: body,
      },
      data: data ? Object.keys(data).reduce((acc, key) => {
        // FCM data payload values must be strings
        acc[key] = String(data[key]);
        return acc;
      }, {}) : {},
      android: {
        priority: "high",
        notification: {
          channelId: "checkot_bookings",
          sound: "default",
        },
      },
    };

    try {
      const response = await admin.messaging().send(message);
      console.log("Successfully sent message:", response);
      return { success: true, messageId: response };
    } catch (error) {
      console.error("Error sending push notification:", error);
      throw new HttpsError(
        "internal",
        `Failed to send push notification: ${error.message}`,
      );
    }
  }
);

const DEFAULT_SERVICE_PRICES = {
  EXTERIOR_WASH: 150.0,
  UNDERWASH: 200.0,
  WAX: 350.0,
  INTERIOR_VACUUM: 200.0,
  TIRE_SHINE: 150.0,
  ENGINE_WASH: 500.0,
  CUSTOM: 0.0
};

const DEFAULT_SERVICE_DURATIONS = {
  EXTERIOR_WASH: 30,
  UNDERWASH: 30,
  WAX: 45,
  INTERIOR_VACUUM: 30,
  TIRE_SHINE: 30,
  ENGINE_WASH: 60,
  CUSTOM: 0
};

function parseTimeSlotToMinutes(slot) {
  const parts = slot.split(" ");
  const t = parts[0].split(":");
  let h = parseInt(t[0], 10);
  const m = parseInt(t[1], 10);
  if (parts[1] === "PM" && h !== 12) h += 12;
  if (parts[1] === "AM" && h === 12) h = 0;
  return h * 60 + m;
}

function busyRangesFromLedger(entries, bayCount) {
  const busyRanges = {};
  for (let i = 0; i < bayCount; i++) {
    busyRanges[i] = [];
  }
  if (entries && Array.isArray(entries)) {
    for (const entry of entries) {
      if (entry.bay !== undefined && busyRanges[entry.bay]) {
        busyRanges[entry.bay].push({ start: entry.start, end: entry.end });
      }
    }
  }
  return busyRanges;
}

function findFreeBayIndex(busyRanges, bayCount, start, end) {
  for (let bay = 0; bay < bayCount; bay++) {
    const ranges = busyRanges[bay] || [];
    const hasOverlap = ranges.some(range => start < range.end && end > range.start);
    if (!hasOverlap) {
      return bay;
    }
  }
  return null;
}

exports.createBooking = onCall(
  {
    region: "asia-southeast1",
    memory: "256MiB",
    timeoutSeconds: 60,
    maxInstances: 10,
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "Please sign in to book a service.",
      );
    }
    const userId = request.auth.uid;

    const {
      shopId,
      bookingDate,
      timeSlot,
      services,
      customServiceNames,
      carId,
      carDetails,
      carSize,
      carPlateNumber,
      carBrandModel,
      notes = "",
    } = request.data || {};

    if (!shopId || !bookingDate || !timeSlot || !services || !carSize) {
      throw new HttpsError(
        "invalid-argument",
        "Missing required booking parameters.",
      );
    }

    const firestore = admin.firestore();
    const shopRef = firestore.collection("shop_services").doc(shopId);
    const ledgerRef = firestore.collection("day_slots").doc(`${shopId}_${bookingDate}`);
    const bookingRef = firestore.collection("bookings").doc();

    // Check if this vehicle has an active booking
    const activeSnapshot = await firestore.collection("bookings")
      .where("carId", "==", carId)
      .where("status", "in", ["PENDING", "CONFIRMED", "IN_PROGRESS"])
      .get();
    
    if (!activeSnapshot.empty) {
      throw new HttpsError(
        "failed-precondition",
        "This car already has an active booking in the queue. You cannot book the same car twice.",
      );
    }

    let totalPrice = 0;
    let duration = 0;
    let ownerFcmToken = "";
    let shopName = "the shop";

    try {
      await firestore.runTransaction(async (transaction) => {
        const shopSnap = await transaction.get(shopRef);
        if (!shopSnap.exists) {
          throw new Error("Shop configuration not found.");
        }
        const shopData = shopSnap.data();
        shopName = shopData.shopName || shopData.name || "the shop";
        ownerFcmToken = shopData.ownerFcmToken || "";
        const bayCount = Math.max(1, parseInt(shopData.bayCount || 1, 10));

        if (shopData.status !== "active") {
          throw new HttpsError("failed-precondition", "This shop is not currently accepting bookings.");
        }
        if (shopData.isClosed) {
          throw new HttpsError("failed-precondition", "This shop is temporarily closed and not accepting bookings.");
        }

        const shopServices = shopData.services || [];
        const startMin = parseTimeSlotToMinutes(timeSlot);

        let customCounter = 0;
        for (const serviceName of services) {
          let config = null;
          if (serviceName === "CUSTOM") {
            const customName = customServiceNames[customCounter] || "";
            customCounter++;
            config = shopServices.find(s => s.isCustom && s.customName === customName);
          } else {
            config = shopServices.find(s => s.serviceName === serviceName);
          }

          let servicePrice = 0;
          if (config && config.pricing && config.pricing[carSize] !== undefined && config.pricing[carSize] > 0) {
            servicePrice = config.pricing[carSize];
          } else {
            const basePrice = (config && config.customPrice > 0) ? config.customPrice : (DEFAULT_SERVICE_PRICES[serviceName] || 0);
            let sizeAdjustment = 0;
            switch (carSize) {
              case "M": sizeAdjustment = 50.0; break;
              case "L": sizeAdjustment = 100.0; break;
              case "XL": sizeAdjustment = 150.0; break;
              case "XXL": sizeAdjustment = 200.0; break;
            }
            servicePrice = basePrice + sizeAdjustment;
          }
          totalPrice += servicePrice;

          let serviceDuration = 30;
          if (config && parseInt(config.durationMinutes, 10) > 0) {
            serviceDuration = parseInt(config.durationMinutes, 10);
          } else {
            serviceDuration = DEFAULT_SERVICE_DURATIONS[serviceName] !== undefined ? DEFAULT_SERVICE_DURATIONS[serviceName] : 30;
          }
          duration += serviceDuration;
        }

        const endMin = startMin + duration;

        const ledgerSnap = await transaction.get(ledgerRef);
        let ledger = ledgerSnap.exists ? ledgerSnap.data() : { shopId, date: bookingDate, entries: [] };
        if (!ledger.entries) ledger.entries = [];

        const busyRanges = busyRangesFromLedger(ledger.entries, bayCount);
        const freeBay = findFreeBayIndex(busyRanges, bayCount, startMin, endMin);
        if (freeBay === null) {
          throw new Error("fully-booked");
        }

        const bookingData = {
          bookingId: bookingRef.id,
          userId: userId,
          shopId: shopId,
          carId: carId,
          carDetails: carDetails,
          carSize: carSize,
          carPlateNumber: carPlateNumber,
          carBrandModel: carBrandModel,
          services: services,
          customServiceNames: customServiceNames || [],
          bookingDate: bookingDate,
          timeSlot: timeSlot,
          status: "PENDING",
          price: totalPrice,
          durationMinutes: duration,
          notes: notes,
          createdAt: Date.now(),
          paymentMethod: "Cash",
          paymentStatus: "unpaid",
        };
        transaction.set(bookingRef, bookingData);

        ledger.entries.push({
          bay: freeBay,
          start: startMin,
          end: endMin,
          bookingId: bookingRef.id,
        });
        transaction.set(ledgerRef, ledger);
      });

      if (ownerFcmToken) {
        try {
          const serviceSummary = services.map(s => {
            if (s === "CUSTOM") return "Custom Service";
            switch(s) {
              case "EXTERIOR_WASH": return "Exterior Wash";
              case "UNDERWASH": return "Underwash";
              case "WAX": return "Wax";
              case "INTERIOR_VACUUM": return "Interior Vacuum";
              case "TIRE_SHINE": return "Tire Shine";
              case "ENGINE_WASH": return "Engine Wash";
              default: return s;
            }
          }).join(", ");

          const message = {
            token: ownerFcmToken,
            notification: {
              title: "New Booking Received!",
              body: `New booking: ${serviceSummary} — ${carDetails}`,
            },
            data: {
              bookingId: bookingRef.id,
            },
            android: {
              priority: "high",
              notification: {
                channelId: "checkot_bookings",
                sound: "default",
              },
            },
          };
          await admin.messaging().send(message);
          console.log("Successfully sent push notification to shop owner:", ownerFcmToken);
        } catch (pushError) {
          console.error("Failed to send push notification to owner:", pushError);
        }
      }

      return { success: true, bookingId: bookingRef.id, price: totalPrice };

    } catch (error) {
      console.error("Transaction failed:", error);
      if (error instanceof HttpsError) {
        throw error;
      }
      if (error.message === "fully-booked") {
        throw new HttpsError(
          "resource-exhausted",
          "This time slot is no longer available. All bays are occupied.",
        );
      }
      throw new HttpsError(
        "internal",
        `Booking failed: ${error.message}`,
      );
    }
  }
);



