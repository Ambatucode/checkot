const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

function getAccessToken() {
  const tokenPath = path.join(process.env.USERPROFILE || process.env.HOME, '.config', 'configstore', 'firebase-tools.json');
  if (!fs.existsSync(tokenPath)) {
    console.error("❌ Error: Firebase CLI login not found. Run 'npx firebase login' first.");
    process.exit(1);
  }

  let config = JSON.parse(fs.readFileSync(tokenPath, 'utf8'));
  let tokens = config.tokens.user || config.tokens;

  // Auto-refresh token if expired or about to expire in 60s
  if (!tokens.expires_at || Date.now() >= (tokens.expires_at - 60000)) {
    console.log("🔄 Access token expired. Refreshing token via Firebase CLI...");
    try {
      execSync('npx firebase-tools projects:list', { stdio: 'ignore' });
      config = JSON.parse(fs.readFileSync(tokenPath, 'utf8'));
      tokens = config.tokens.user || config.tokens;
    } catch (e) {
      // Ignore fallback warning
    }
  }

  return tokens.access_token;
}

function parseFirestoreDoc(doc) {
  const fields = doc.fields || {};
  const obj = { id: doc.name.split('/').pop() };
  for (const [key, val] of Object.entries(fields)) {
    obj[key] = parseValue(val);
  }
  return obj;
}

function parseValue(val) {
  if (val.stringValue !== undefined) return val.stringValue;
  if (val.integerValue !== undefined) return parseInt(val.integerValue, 10);
  if (val.doubleValue !== undefined) return parseFloat(val.doubleValue);
  if (val.booleanValue !== undefined) return val.booleanValue;
  if (val.nullValue !== undefined) return null;
  if (val.timestampValue !== undefined) return val.timestampValue;
  if (val.arrayValue !== undefined) return (val.arrayValue.values || []).map(parseValue);
  if (val.mapValue !== undefined) {
    const map = {};
    for (const [k, v] of Object.entries(val.mapValue.fields || {})) {
      map[k] = parseValue(v);
    }
    return map;
  }
  return JSON.stringify(val);
}

async function fetchAllBookings() {
  try {
    let accessToken = getAccessToken();
    let url = `https://firestore.googleapis.com/v1/projects/checkot-14700/databases/(default)/documents/bookings?pageSize=300`;

    console.log("🔍 Fetching live bookings from Checkot Firestore database...\n");
    let res = await fetch(url, { headers: { 'Authorization': `Bearer ${accessToken}` } });
    
    // If token returned 401, force refresh token and retry once
    if (res.status === 401) {
      console.log("🔄 Token expired. Force refreshing authentication token...");
      execSync('npx firebase-tools projects:list', { stdio: 'ignore' });
      accessToken = getAccessToken();
      res = await fetch(url, { headers: { 'Authorization': `Bearer ${accessToken}` } });
    }

    if (!res.ok) {
      throw new Error(`HTTP Error ${res.status}: ${await res.text()}`);
    }

    const data = await res.json();
    const bookings = (data.documents || []).map(parseFirestoreDoc);

    console.log("==================================================");
    console.log(`📊 TOTAL BOOKING DOCUMENTS IN DATABASE: ${bookings.length}`);
    console.log("==================================================");

    const statusCounts = {};
    const uniqueUsers = new Set();
    const uniqueShops = new Set();
    const uniqueSlotKeys = new Set();
    const activeSlotKeys = new Set();

    bookings.forEach(b => {
      const st = b.status || 'UNKNOWN';
      statusCounts[st] = (statusCounts[st] || 0) + 1;

      const userId = b.userId || b.customerId || 'unknown_user';
      uniqueUsers.add(userId);
      if (b.shopId) uniqueShops.add(b.shopId);

      const slotKey = `${userId}_${b.shopId}_${b.date || b.bookingDate}_${b.timeSlot || b.time}`;
      uniqueSlotKeys.add(slotKey);

      if (st !== 'CANCELLED') {
        activeSlotKeys.add(slotKey);
      }
    });

    console.log("\n📌 STATUS BREAKDOWN:");
    for (const [k, v] of Object.entries(statusCounts)) {
      console.log(`  - ${k}: ${v}`);
    }

    console.log("\n📈 SUMMARY COUNTS:");
    console.log(`  - Unique User Accounts: ${uniqueUsers.size}`);
    console.log(`  - Unique Shops Booked: ${uniqueShops.size}`);
    console.log(`  - Unique Active Sessions (Excluding Cancelled): ${activeSlotKeys.size}`);
    console.log(`  - Unique Total Sessions (All Statuses): ${uniqueSlotKeys.size}`);
    console.log("==================================================\n");

  } catch (err) {
    console.error("❌ Failed to query database:", err.message);
  }
}

fetchAllBookings();
