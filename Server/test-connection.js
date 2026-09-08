require('dotenv').config();

const {MongoClient, ServerApiVersion} = require('mongodb');
const {getSimCollections, APP_DB, listSimPrefixes} = require('./simCollections');

const uri = process.env.MONGODB_URI;

if (!uri) {
  console.error('[FAIL] MONGODB_URI missing in server/.env');
  process.exit(1);
}

const client = new MongoClient(uri, {
  serverApi: {
    version: ServerApiVersion.v1,
    strict: true,
    deprecationErrors: true,
  },
  serverSelectionTimeoutMS: 30000,
  connectTimeoutMS: 30000,
});

async function run() {
  try {
    console.log('Connecting to MongoDB Atlas...');
    await client.connect();
    await client.db('admin').command({ping: 1});
    console.log('[OK] MongoDB connected');

    const db = client.db(APP_DB);
    const collectionNames = (await db.listCollections().toArray()).map(item => item.name);
    const prefixes = listSimPrefixes(collectionNames);

    console.log(`\nStructure: ${APP_DB} (single database)\n`);

    if (prefixes.length === 0) {
      console.log(`No SMS/call collections yet in ${APP_DB}.`);
      console.log('');
      console.log('Atlas me "devices" collection mat dekho — wahan sirf registration hota hai.');
      console.log('Data yahan aata hai:');
      console.log('  calltech / 9982669294-massage  (SMS)');
      console.log('  calltech / 9982669294-call      (calls)');
      console.log('');
      console.log('Phone se sync ke liye: npm run server:lan → npm run server → npm run android');
    } else {
      for (const prefix of prefixes) {
        const cols = getSimCollections(prefix);
        const [messageCount, callCount] = await Promise.all([
          db.collection(cols.messages).countDocuments(),
          db.collection(cols.callLogs).countDocuments(),
        ]);
        console.log(`${APP_DB}/`);
        console.log(`  ${prefix}/`);
        console.log(`    ${cols.messages} (${messageCount})`);
        console.log(`    ${cols.callLogs} (${callCount})`);
      }
    }

    const deviceCount = await db.collection('devices').countDocuments().catch(() => 0);
    if (deviceCount > 0) {
      console.log(`\nRegistered devices: ${deviceCount}`);
    }

    const {databases} = await client.db('admin').admin().listDatabases();
    const extraDbs = databases.map(item => item.name).filter(name => name.startsWith('calltech_'));
    if (extraDbs.length > 0) {
      console.log('\nExtra databases (delete after migration):');
      extraDbs.forEach(name => console.log(`  - ${name}`));
    }

    console.log('\n[OK] Ready.');
  } catch (error) {
    console.error('[FAIL]', error.message);
    if (/ETIMEDOUT|ECONNREFUSED|27017/.test(String(error.message))) {
      console.error(`
Likely cause: your WiFi/network blocks outbound MongoDB port 27017.
Atlas IP Access List is OK (0.0.0.0/0) — this is NOT an IP whitelist issue.

Quick fixes:
  1) Mobile hotspot try karo, phir: npm run server:test
  2) VPN on karo
  3) Render par server deploy karo (SYNC_API_URL) — phone HTTPS se sync karega
  4) Atlas Data API use karo (port 443): npm run server:test:data-api

Check port block:
  Test-NetConnection 159.41.192.59 -Port 27017
`);
    }
    process.exitCode = 1;
  } finally {
    await client.close();
  }
}

run();
