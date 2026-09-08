/**
 * MongoDB Atlas par real-time SMS + call data dekho (phone se aata hai).
 */
require('dotenv').config();

const {MongoClient, ServerApiVersion} = require('mongodb');
const {getSimCollections, APP_DB, listSimPrefixes} = require('./simCollections');

const uri = process.env.MONGODB_URI;
const pollMs = Number(process.env.WATCH_POLL_MS || 5000);

if (!uri) {
  console.error('[FAIL] MONGODB_URI missing in server/.env');
  process.exit(1);
}

const client = new MongoClient(uri, {
  serverApi: {version: ServerApiVersion.v1, strict: true, deprecationErrors: true},
  serverSelectionTimeoutMS: 30000,
});

const seen = new Set();

function formatDoc(type, doc) {
  const time = doc.dateTime || doc.timestamp || doc.syncedAt || '';
  if (type === 'sms') {
    const body = String(doc.body || doc.message || '').slice(0, 80);
    return `[SMS] ${time} | ${doc.phoneNumber || '?'} | ${body}`;
  }
  const dur = doc.durationFormatted || doc.durationSeconds || doc.duration || 0;
  return `[CALL] ${time} | ${doc.phoneNumber || '?'} | ${doc.type || '?'} | ${dur}`;
}

async function scanCollection(db, collectionName, type) {
  const col = db.collection(collectionName);
  const cursor = col.find({}).sort({timestamp: -1}).limit(25);
  const docs = await cursor.toArray();

  for (const doc of docs.reverse()) {
    const id = doc.id || String(doc._id);
    if (seen.has(id)) {
      continue;
    }
    seen.add(id);
    console.log(`  ${formatDoc(type, doc)}`);
  }
}

async function poll() {
  const db = client.db(APP_DB);
  const names = (await db.listCollections().toArray()).map(c => c.name);
  const prefixes = listSimPrefixes(names);

  if (prefixes.length === 0) {
    console.log(`[${new Date().toLocaleTimeString()}] Waiting for phone data in ${APP_DB}...`);
    console.log('  (Atlas: "devices" nahi — dekho {number}-massage / {number}-call)');
    return;
  }

  console.log(`\n--- ${new Date().toLocaleTimeString()} ---`);
  for (const prefix of prefixes) {
    const cols = getSimCollections(prefix);
    console.log(`${prefix}:`);
    if (names.includes(cols.messages)) {
      await scanCollection(db, cols.messages, 'sms');
    }
    if (names.includes(cols.callLogs)) {
      await scanCollection(db, cols.callLogs, 'call');
    }
  }
}

async function main() {
  await client.connect();
  console.log(`[OK] Watching ${APP_DB} every ${pollMs / 1000}s — phone par SMS/call karo\n`);

  await poll();
  setInterval(() => {
    poll().catch(err => console.error('[watch]', err.message));
  }, pollMs);
}

main().catch(err => {
  console.error('[FAIL]', err.message);
  process.exit(1);
});
