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
  const obj = { id: doc.name.split('/').pop(), createTime: doc.createTime, updateTime: doc.updateTime };
  for (const [k, v] of Object.entries(fields)) {
    obj[k] = parseValue(val);
  }
  return obj;
}

function parseFirestoreDoc(doc) {
  const fields = doc.fields || {};
  const obj = { id: doc.name.split('/').pop(), createTime: doc.createTime, updateTime: doc.updateTime };
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

async function findNewest() {
  try {
    let accessToken = getAccessToken();

    const userUrl = `https://firestore.googleapis.com/v1/projects/checkot-14700/databases/(default)/documents/users?pageSize=300`;
    let uRes = await fetch(userUrl, { headers: { 'Authorization': `Bearer ${accessToken}` } });

    if (uRes.status === 401) {
      console.log("🔄 Token expired. Force refreshing authentication token...");
      execSync('npx firebase-tools projects:list', { stdio: 'ignore' });
      accessToken = getAccessToken();
      uRes = await fetch(userUrl, { headers: { 'Authorization': `Bearer ${accessToken}` } });
    }

    const uData = await uRes.json();
    const users = (uData.documents || []).map(parseFirestoreDoc);
    users.sort((a, b) => new Date(b.createTime || 0) - new Date(a.createTime || 0));
    const newestUser = users[0];

    const bookingUrl = `https://firestore.googleapis.com/v1/projects/checkot-14700/databases/(default)/documents/bookings?pageSize=300`;
    let bRes = await fetch(bookingUrl, { headers: { 'Authorization': `Bearer ${accessToken}` } });
    const bData = await bRes.json();
    const bookings = (bData.documents || []).map(parseFirestoreDoc);
    bookings.sort((a, b) => new Date(b.createTime || 0) - new Date(a.createTime || 0));
    const newestBooking = bookings[0];

    console.log("==================================================");
    console.log("🆕 NEWEST REGISTERED USER IN DATABASE:");
    console.log("==================================================");
    if (newestUser) {
      console.log(`  Name: ${newestUser.name || newestUser.fullName || 'N/A'}`);
      console.log(`  Gmail: ${newestUser.email || 'No email'}`);
      console.log(`  Role: ${newestUser.role || 'customer'}`);
      console.log(`  User ID: ${newestUser.id}`);
      console.log(`  Registered At: ${new Date(newestUser.createTime).toLocaleString()}`);
    } else {
      console.log("  No user records found.");
    }
    console.log("==================================================\n");

    console.log("==================================================");
    console.log("🆕 NEWEST BOOKING PLACED IN DATABASE:");
    console.log("==================================================");
    if (newestBooking) {
      console.log(`  Doc ID: ${newestBooking.id}`);
      console.log(`  Customer Name: ${newestBooking.userName || newestBooking.customerName || newestBooking.userId}`);
      console.log(`  Status: ${newestBooking.status}`);
      console.log(`  Price: ₱${newestBooking.price || newestBooking.totalPrice || 'N/A'}`);
      console.log(`  Scheduled: ${newestBooking.date || newestBooking.bookingDate} at ${newestBooking.timeSlot || newestBooking.time}`);
      console.log(`  Booked At: ${new Date(newestBooking.createTime).toLocaleString()}`);
    } else {
      console.log("  No booking records found.");
    }
    console.log("==================================================\n");

  } catch (err) {
    console.error("❌ Failed to query newest user:", err.message);
  }
}

findNewest();
