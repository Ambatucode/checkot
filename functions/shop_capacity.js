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

  if (!tokens.expires_at || Date.now() >= (tokens.expires_at - 60000)) {
    console.log("🔄 Access token expired. Refreshing token via Firebase CLI...");
    try {
      execSync('npx firebase-tools projects:list', { stdio: 'ignore' });
      config = JSON.parse(fs.readFileSync(tokenPath, 'utf8'));
      tokens = config.tokens.user || config.tokens;
    } catch (e) {}
  }

  return tokens.access_token;
}

function parseFirestoreDoc(doc) {
  const fields = doc.fields || {};
  const obj = { id: doc.name.split('/').pop() };
  for (const [k, v] of Object.entries(fields)) {
    obj[k] = parseValue(v);
  }
  return obj;
}

function parseValue(val) {
  if (val.stringValue !== undefined) return val.stringValue;
  if (val.integerValue !== undefined) return parseInt(val.integerValue, 10);
  if (val.doubleValue !== undefined) return parseFloat(val.doubleValue);
  if (val.booleanValue !== undefined) return val.booleanValue;
  if (val.nullValue !== undefined) return null;
  if (val.arrayValue !== undefined) return (val.arrayValue.values || []).map(parseValue);
  if (val.mapValue !== undefined) {
    const map = {};
    for (const [k, v] of Object.entries(val.mapValue.fields || {})) map[k] = parseValue(v);
    return map;
  }
  return JSON.stringify(val);
}

async function analyzeShopCapacity() {
  try {
    let token = getAccessToken();
    console.log("🏪 Fetching Car Wash Shop Bay Capacities & Status...\n");

    const shopUrl = `https://firestore.googleapis.com/v1/projects/checkot-14700/databases/(default)/documents/shop_services?pageSize=300`;
    let sRes = await fetch(shopUrl, { headers: { 'Authorization': `Bearer ${token}` } });

    if (sRes.status === 401) {
      console.log("🔄 Token expired. Force refreshing authentication token...");
      execSync('npx firebase-tools projects:list', { stdio: 'ignore' });
      token = getAccessToken();
      sRes = await fetch(shopUrl, { headers: { 'Authorization': `Bearer ${token}` } });
    }

    const sData = await sRes.json();
    const shops = (sData.documents || []).map(parseFirestoreDoc);

    const bookingUrl = `https://firestore.googleapis.com/v1/projects/checkot-14700/databases/(default)/documents/bookings?pageSize=300`;
    const bRes = await fetch(bookingUrl, { headers: { 'Authorization': `Bearer ${token}` } });
    const bookings = ((await bRes.json()).documents || []).map(parseFirestoreDoc);

    const shopBookingCounts = new Map();
    bookings.forEach(b => {
      const sId = b.shopId;
      if (sId) {
        shopBookingCounts.set(sId, (shopBookingCounts.get(sId) || 0) + 1);
      }
    });

    console.log("==========================================================================================");
    console.log(`🏪 SHOP CAPACITY & BAY OVERVIEW (${shops.length} total shops on platform)`);
    console.log("==========================================================================================\n");

    shops.forEach((s, idx) => {
      const shopName = s.shopName || s.name || s.id;
      const status = (s.status || 'pending').toUpperCase();
      const bayCount = s.bayCount || 4;
      const totalBookings = shopBookingCounts.get(s.id) || 0;
      const servicesList = Array.isArray(s.services) ? s.services.length : 0;

      console.log(` [${idx + 1}] ${shopName}`);
      console.log(`     Approval Status: ${status}`);
      console.log(`     Wash Bay Capacity: ${bayCount} bays`);
      console.log(`     Available Services: ${servicesList} package(s)`);
      console.log(`     Total Received Bookings: ${totalBookings} bookings`);
      console.log("------------------------------------------------------------------------------------------");
    });

  } catch (err) {
    console.error("❌ Failed to fetch shop capacity:", err.message);
  }
}

analyzeShopCapacity();
