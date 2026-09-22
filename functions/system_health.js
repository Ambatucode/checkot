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

async function runHealthCheck() {
  try {
    let token = getAccessToken();
    console.log("🩺 Running Checkot Database Integrity & Health Scanner...\n");

    const bUrl = `https://firestore.googleapis.com/v1/projects/checkot-14700/databases/(default)/documents/bookings?pageSize=300`;
    let bRes = await fetch(bUrl, { headers: { 'Authorization': `Bearer ${token}` } });

    if (bRes.status === 401) {
      console.log("🔄 Token expired. Force refreshing authentication token...");
      execSync('npx firebase-tools projects:list', { stdio: 'ignore' });
      token = getAccessToken();
      bRes = await fetch(bUrl, { headers: { 'Authorization': `Bearer ${token}` } });
    }

    const bDocs = ((await bRes.json()).documents || []).map(parseFirestoreDoc);

    const uUrl = `https://firestore.googleapis.com/v1/projects/checkot-14700/databases/(default)/documents/users?pageSize=300`;
    const uDocs = ((await (await fetch(uUrl, { headers: { 'Authorization': `Bearer ${token}` } })).json()).documents || []).map(parseFirestoreDoc);

    const sUrl = `https://firestore.googleapis.com/v1/projects/checkot-14700/databases/(default)/documents/shop_services?pageSize=300`;
    const sDocs = ((await (await fetch(sUrl, { headers: { 'Authorization': `Bearer ${token}` } })).json()).documents || []).map(parseFirestoreDoc);

    const validUserIds = new Set(uDocs.map(u => u.id));
    const validShopIds = new Set(sDocs.map(s => s.id));

    let issuesCount = 0;
    const issuesList = [];

    bDocs.forEach(b => {
      const uId = b.userId || b.customerId;
      const sId = b.shopId;

      if (!uId || !validUserIds.has(uId)) {
        issuesCount++;
        issuesList.push(`Booking [${b.id}] has missing/orphaned User ID (${uId || 'null'})`);
      }

      if (!sId || !validShopIds.has(sId)) {
        issuesCount++;
        issuesList.push(`Booking [${b.id}] has missing/orphaned Shop ID (${sId || 'null'})`);
      }

      if (!b.status) {
        issuesCount++;
        issuesList.push(`Booking [${b.id}] is missing status field`);
      }
    });

    console.log("==========================================================================================");
    console.log(`🩺 DATABASE HEALTH & INTEGRITY REPORT`);
    console.log("==========================================================================================");
    console.log(`  - Total Bookings Scanned: ${bDocs.length}`);
    console.log(`  - Total User Profiles Scanned: ${uDocs.length}`);
    console.log(`  - Total Shops Scanned: ${sDocs.length}`);
    console.log(`  - Schema / Integrity Anomalies Found: ${issuesCount}`);
    console.log("==========================================================================================\n");

    if (issuesCount === 0) {
      console.log("✨ EXCELLENT! 100% Database Integrity. No orphaned documents or missing fields found.");
    } else {
      console.log("⚠️ ISSUES DETECTED:");
      issuesList.forEach(iss => console.log(`  - ${iss}`));
    }

  } catch (err) {
    console.error("❌ Failed health check:", err.message);
  }
}

runHealthCheck();
