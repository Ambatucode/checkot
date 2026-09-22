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

async function listCustomersOnly() {
  try {
    let accessToken = getAccessToken();
    console.log("🔍 Fetching Customer Accounts from Checkot Firestore database...\n");

    const url = `https://firestore.googleapis.com/v1/projects/checkot-14700/databases/(default)/documents/users?pageSize=300`;
    let res = await fetch(url, { headers: { 'Authorization': `Bearer ${accessToken}` } });

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
    const rawDocs = data.documents || [];
    const allUsers = rawDocs.map(parseFirestoreDoc);

    const customers = allUsers.filter(u => {
      const role = (u.role || '').toLowerCase();
      return role === 'customer' || role === 'client' || (!role && !u.ownedShopId);
    });

    console.log("==========================================================================================");
    console.log(`📱 REGISTERED CUSTOMERS ONLY (${customers.length} Accounts)`);
    console.log("==========================================================================================\n");

    customers.forEach((u, i) => {
      const name = u.name || u.fullName || u.displayName || 'N/A';
      const email = u.email || 'No email registered';
      const phone = u.phone || u.phoneNumber || 'N/A';
      console.log(` [${i + 1}] ${name}`);
      console.log(`     📧 Gmail: ${email}`);
      if (phone !== 'N/A') console.log(`     📞 Phone: ${phone}`);
      console.log(`     🔑 User ID: ${u.id}`);
      console.log("------------------------------------------------------------------------------------------");
    });

    console.log(`\n✅ Total Customers Found: ${customers.length}`);

  } catch (err) {
    console.error("❌ Failed to fetch customers:", err.message);
  }
}

listCustomersOnly();
