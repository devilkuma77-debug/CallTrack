require('dotenv').config();

const {MongoClient, ServerApiVersion} = require('mongodb');
const {getSimCollections, normalizeSimNumber, listSimPrefixes} = require('./simCollections');

const uri = process.env.MONGODB_URI;
const dbName = process.env.MONGODB_DB_NAME || 'calltech';
const simNumber = normalizeSimNumber(process.env.SIM_NUMBER || process.argv.find(arg => arg.startsWith('--sim='))?.slice(5));
const apply = process.argv.includes('--apply');
const cleanup = process.argv.includes('--cleanup');

if (!uri) {
  console.error('[FAIL] MONGODB_URI missing in server/.env');
  process.exit(1);
}

if (!simNumber) {
  console.error('[FAIL] SIM_NUMBER required');
  console.error('Usage: SIM_NUMBER=919876543210 node migrate-to-sim-collections.js [--apply] [--cleanup]');
  process.exit(1);
}

const client = new MongoClient(uri, {
  serverApi: {
    version: ServerApiVersion.v1,
    strict: true,
    deprecationErrors: true,
  },
  serverSelectionTimeoutMS: 30000,
});

async function migrateCollection(db, sourceName, targetName) {
  const source = db.collection(sourceName);
  const target = db.collection(targetName);
  const total = await source.countDocuments();

  if (total === 0) {
    console.log(`[skip] ${sourceName} is empty`);
    return 0;
  }

  console.log(`${apply ? '[apply]' : '[dry-run]'} ${sourceName} -> ${targetName} (${total} docs)`);

  if (!apply) {
    return total;
  }

  const cursor = source.find({});
  let moved = 0;

  while (await cursor.hasNext()) {
    const doc = await cursor.next();
    const payload = {
      ...doc,
      simNumber,
      syncedAt: doc.syncedAt || new Date(),
      updatedAt: new Date(),
    };
    delete payload._id;

    await target.updateOne({id: String(doc.id)}, {$set: payload}, {upsert: true});
    moved += 1;
  }

  return moved;
}

async function run() {
  await client.connect();
  const db = client.db(dbName);
  const collections = getSimCollections(simNumber);

  const messageMoved = await migrateCollection(db, 'message', collections.messages);
  const callMoved = await migrateCollection(db, 'callLogs', collections.callLogs);

  if (apply && cleanup) {
    if (messageMoved > 0) {
      await db.collection('message').drop().catch(() => {});
      console.log('[cleanup] dropped message');
    }
    if (callMoved > 0) {
      await db.collection('callLogs').drop().catch(() => {});
      console.log('[cleanup] dropped callLogs');
    }
  }

  const names = await db.listCollections().toArray();
  const sims = listSimPrefixes(names.map(item => item.name));

  console.log('');
  console.log(`Database: ${dbName}`);
  console.log(`Target SIM: ${collections.simNumber}`);
  console.log(`Messages moved: ${messageMoved}`);
  console.log(`Call logs moved: ${callMoved}`);
  console.log(`SIM collections found: ${sims.join(', ') || 'none'}`);

  if (!apply) {
    console.log('');
    console.log('Dry run only. Re-run with --apply to migrate.');
  }
}

run()
  .catch(error => {
    console.error('[FAIL]', error.message);
    process.exitCode = 1;
  })
  .finally(async () => {
    await client.close();
  });
