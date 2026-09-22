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

async function listOwnersOnly() {
  try {
    let accessToken = getAccessToken();
    console.log("🔍 Fetching Shop Owner Accounts and Car Wash Shops from Firestore...\n");

    const userUrl = `https://firestore.googleapis.com/v1/projects/checkot-14700/databases/(default)/documents/users?pageSize=300`;
    let uRes = await fetch(userUrl, { headers: { 'Authorization': `Bearer ${accessToken}` } });

    if (uRes.status === 401) {
      console.log("🔄 Token expired. Force refreshing authentication token...");
      execSync('npx firebase-tools projects:list', { stdio: 'ignore' });
      accessToken = getAccessToken();
      uRes = await fetch(userUrl, { headers: { 'Authorization': `Bearer ${accessToken}` } });
    }

    const uData = await uRes.json();
    const allUsers = (uData.documents || []).map(parseFirestoreDoc);

    const shopUrl = `https://firestore.googleapis.com/v1/projects/checkot-14700/databases/(default)/documents/shop_services?pageSize=300`;
    const sRes = await fetch(shopUrl, { headers: { 'Authorization': `Bearer ${accessToken}` } });
    const sData = await sRes.json();
    const shops = (sData.documents || []).map(parseFirestoreDoc);

    const shopMap = new Map();
    shops.forEach(s => shopMap.set(s.id, s));

    const owners = allUsers.filter(u => {
      const role = (u.role || '').toLowerCase();
      return role === 'owner' || role === 'shop_owner' || (u.ownedShopId && u.ownedShopId !== '');
    });

    console.log("==========================================================================================");
    console.log(`🏪 REGISTERED SHOP OWNERS & CAR WASH SHOPS (${owners.length} Owner Accounts)`);
    console.log("==========================================================================================\n");

    owners.forEach((u, i) => {
      const name = u.name || u.fullName || u.displayName || 'N/A';
      const email = u.email || 'No email registered';
      const shopId = u.ownedShopId || 'N/A';
      const shop = shopMap.get(shopId);

      const shopName = shop ? (shop.shopName || shop.name || shopId) : 'N/A (No shop doc)';
      const shopStatus = shop ? (shop.status || 'unknown') : 'N/A';
      const bayCount = shop ? (shop.bayCount || 4) : 'N/A';

      console.log(` [${i + 1}] Owner: ${name}`);
      console.log(`     📧 Gmail: ${email}`);
      console.log(`     🏪 Shop Name: ${shopName} (Status: ${shopStatus.toUpperCase()})`);
      console.log(`     🧼 Wash Bays: ${bayCount}`);
      console.log(`     🆔 Shop ID: ${shopId}`);
      console.log(`     🔑 User ID: ${u.id}`);
      console.log("------------------------------------------------------------------------------------------");
    });

    console.log(`\n✅ Total Shop Owners Found: ${owners.length}`);

  } catch (err) {
    console.error("❌ Failed to fetch owners:", err.message);
  }
}

listOwnersOnly();
