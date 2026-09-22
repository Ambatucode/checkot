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

async function analyzePopularServices() {
  try {
    let token = getAccessToken();
    console.log("🌟 Analyzing Most Popular Wash Services in Checkot...\n");

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

    const serviceCounts = {};
    let totalServiceSelections = 0;

    bookings.forEach(b => {
      const services = Array.isArray(b.services) 
        ? b.services.map(s => (typeof s === 'object' ? (s.name || s.id || JSON.stringify(s)) : s))
        : [b.serviceName || b.serviceId || 'Standard Wash'];

      services.forEach(sName => {
        if (!sName) return;
        const cleanName = sName.toString().trim().toUpperCase();
        serviceCounts[cleanName] = (serviceCounts[cleanName] || 0) + 1;
        totalServiceSelections++;
      });
    });

    const sortedServices = Object.entries(serviceCounts).sort((a, b) => b[1] - a[1]);

    console.log("==========================================================================================");
    console.log(`🧼 POPULAR SERVICES RANKING & DEMAND ANALYSIS (${totalServiceSelections} total selections)`);
    console.log("==========================================================================================\n");

    sortedServices.forEach(([service, count], index) => {
      const percentage = ((count / totalServiceSelections) * 100).toFixed(1);
      console.log(` [Rank ${index + 1}] ${service}`);
      console.log(`     Total Selections: ${count} times (${percentage}%)`);
      console.log("------------------------------------------------------------------------------------------");
    });

  } catch (err) {
    console.error("❌ Failed to analyze popular services:", err.message);
  }
}

analyzePopularServices();
