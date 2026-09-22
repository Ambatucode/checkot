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

async function cleanOrphanedBookings() {
  try {
    let accessToken = getAccessToken();
    console.log("🩺 Scanning database for orphaned booking documents...\n");

    const bUrl = `https://firestore.googleapis.com/v1/projects/checkot-14700/databases/(default)/documents/bookings?pageSize=300`;
    let bRes = await fetch(bUrl, { headers: { 'Authorization': `Bearer ${accessToken}` } });

    if (bRes.status === 401) {
      console.log("🔄 Token expired. Force refreshing authentication token...");
      execSync('npx firebase-tools projects:list', { stdio: 'ignore' });
      accessToken = getAccessToken();
      bRes = await fetch(bUrl, { headers: { 'Authorization': `Bearer ${accessToken}` } });
    }

    const bDocs = ((await bRes.json()).documents || []).map(parseFirestoreDoc);

    const uUrl = `https://firestore.googleapis.com/v1/projects/checkot-14700/databases/(default)/documents/users?pageSize=300`;
    const uDocs = ((await (await fetch(uUrl, { headers: { 'Authorization': `Bearer ${accessToken}` } })).json()).documents || []).map(parseFirestoreDoc);

    const sUrl = `https://firestore.googleapis.com/v1/projects/checkot-14700/databases/(default)/documents/shop_services?pageSize=300`;
    const sDocs = ((await (await fetch(sUrl, { headers: { 'Authorization': `Bearer ${accessToken}` } })).json()).documents || []).map(parseFirestoreDoc);

    const validUserIds = new Set(uDocs.map(u => u.id));
    const validShopIds = new Set(sDocs.map(s => s.id));

    const orphanedDocIds = [];

    bDocs.forEach(b => {
      const uId = b.userId || b.customerId;
      const sId = b.shopId;
      if ((uId && !validUserIds.has(uId)) || (sId && !validShopIds.has(sId))) {
        orphanedDocIds.push(b.id);
      }
    });

    if (orphanedDocIds.length === 0) {
      console.log("✨ EXCELLENT! No orphaned booking documents found. Database is 100% clean!");
      return;
    }

    console.log(`Found ${orphanedDocIds.length} orphaned document(s) to remove:`);

    for (const docId of orphanedDocIds) {
      console.log(` Deleting document: bookings/${docId}...`);
      const delRes = await fetch(`https://firestore.googleapis.com/v1/projects/checkot-14700/databases/(default)/documents/bookings/${docId}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${accessToken}` }
      });

      if (delRes.ok) {
        console.log(`   ✅ Successfully deleted bookings/${docId}`);
      } else {
        console.error(`   ❌ Failed to delete bookings/${docId}:`, await delRes.text());
      }
    }

    console.log("\n🎉 Cleanup complete!");
  } catch (err) {
    console.error("❌ Error during cleanup:", err.message);
  }
}

cleanOrphanedBookings();
