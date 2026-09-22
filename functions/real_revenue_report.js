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

async function runRealRevenueReport() {
  try {
    let token = getAccessToken();
    console.log("💰 Generating Real Shops Revenue Report (Excluding Demo Shop)...\n");

    const bookingUrl = `https://firestore.googleapis.com/v1/projects/checkot-14700/databases/(default)/documents/bookings?pageSize=300`;
    let bRes = await fetch(bookingUrl, { headers: { 'Authorization': `Bearer ${token}` } });

    if (bRes.status === 401) {
      console.log("🔄 Token expired. Force refreshing authentication token...");
      execSync('npx firebase-tools projects:list', { stdio: 'ignore' });
      token = getAccessToken();
      bRes = await fetch(bookingUrl, { headers: { 'Authorization': `Bearer ${token}` } });
    }

    const bData = await bRes.json();
    const bookings = (bData.documents || []).map(parseFirestoreDoc);

    const shopUrl = `https://firestore.googleapis.com/v1/projects/checkot-14700/databases/(default)/documents/shop_services?pageSize=300`;
    const sRes = await fetch(shopUrl, { headers: { 'Authorization': `Bearer ${token}` } });
    const sData = await sRes.json();
    const shops = (sData.documents || []).map(parseFirestoreDoc);
    const shopMap = new Map();
    const demoShopIds = new Set();

    shops.forEach(s => {
      const sName = s.shopName || s.name || '';
      if (sName.toLowerCase().includes('demo') || s.id === 'sF0Pd3eA14vcF1woPPza') {
        demoShopIds.add(s.id);
      } else {
        shopMap.set(s.id, sName || s.id);
      }
    });

    let totalRealRevenue = 0;
    let completedRealCount = 0;
    const shopRevenueMap = new Map();

    bookings.forEach(b => {
      const status = (b.status || '').toUpperCase();
      const shopId = b.shopId || 'unknown_shop';
      const shopName = b.shopName || '';

      // Skip demo shop bookings
      if (demoShopIds.has(shopId) || shopName.toLowerCase().includes('demo')) {
        return;
      }

      const price = parseFloat(b.price || b.totalPrice || 0);

      if (status === 'COMPLETED') {
        totalRealRevenue += price;
        completedRealCount++;

        if (!shopRevenueMap.has(shopId)) {
          shopRevenueMap.set(shopId, { revenue: 0, count: 0, name: shopMap.get(shopId) || shopName || shopId });
        }
        const sData = shopRevenueMap.get(shopId);
        sData.revenue += price;
        sData.count++;
      }
    });

    console.log("==========================================================================================");
    console.log(`💵 REAL SHOPS REVENUE SUMMARY (EXCLUDING DEMO SHOPS)`);
    console.log("==========================================================================================");
    console.log(`  - Total Real Completed Revenue: ₱${totalRealRevenue.toLocaleString('en-US', { minimumFractionDigits: 2 })}`);
    console.log(`  - Total Completed Car Washes (Real Shops): ${completedRealCount}`);
    console.log(`  - Average Real Transaction Value: ₱${completedRealCount > 0 ? (totalRealRevenue / completedRealCount).toFixed(2) : '0.00'}`);
    console.log("==========================================================================================\n");

    console.log("🏪 REVENUE BREAKDOWN BY REAL CAR WASH SHOP:");
    console.log("------------------------------------------------------------------------------------------");
    let idx = 1;
    for (const [shopId, data] of shopRevenueMap.entries()) {
      console.log(` [${idx++}] Shop: ${data.name}`);
      console.log(`     Total Completed Revenue: ₱${data.revenue.toLocaleString('en-US', { minimumFractionDigits: 2 })}`);
      console.log(`     Completed Wash Count: ${data.count} bookings`);
      console.log("------------------------------------------------------------------------------------------");
    }

  } catch (err) {
    console.error("❌ Failed to generate real revenue report:", err.message);
  }
}

runRealRevenueReport();
