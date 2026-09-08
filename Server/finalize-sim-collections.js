require('dotenv').config();

const {MongoClient, ServerApiVersion} = require('mongodb');
const {
  getSimCollections,
  APP_DB,
  normalizeSimNumber,
  listSimPrefixes,
} = require('./simCollections');

const uri = process.env.MONGODB_URI;
const simNumber = normalizeSimNumber(
  process.env.SIM_NUMBER || process.argv.find(arg => arg.startsWith('--sim='))?.slice(5),
);
const apply = process.argv.includes('--apply');

const LEGACY_COLLECTIONS = ['message', 'callLogs'];

if (!uri) {
  console.error('[FAIL] MONGODB_URI missing');
  process.exit(1);
}

const client = new MongoClient(uri, {
  serverApi: {version: ServerApiVersion.v1, strict: true, deprecationErrors: true},
  serverSelectionTimeoutMS: 30000,
});

async function copyDocs(sourceCollection, targetCollection, sim) {
  let moved = 0;
  const cursor = sourceCollection.find({});
  while (await cursor.hasNext()) {
    const doc = await cursor.next();
    const payload = {...doc, simNumber: sim, syncedAt: doc.syncedAt || new Date()};
    delete payload._id;
    await targetCollection.updateOne({id: String(doc.id)}, {$set: payload}, {upsert: true});
    moved += 1;
  }
  return moved;
}

async function migrateLegacy(db, sim) {
  const target = getSimCollections(sim);
  let moved = 0;

  for (const legacyName of LEGACY_COLLECTIONS) {
    const source = db.collection(legacyName);
    const total = await source.countDocuments();
    if (total === 0) {
      continue;
    }

    const targetName = legacyName === 'message' ? target.messages : target.callLogs;
    console.log(
      `${apply ? '[apply]' : '[dry-run]'} ${APP_DB}.${legacyName} -> ${APP_DB}.${targetName} (${total})`,
    );

    if (!apply) {
      moved += total;
      continue;
    }

    moved += await copyDocs(source, db.collection(targetName), target.simNumber);
  }

  return moved;
}

async function migrateSeparateSimDatabases(sim) {
  const target = getSimCollections(sim);
  const calltechDb = client.db(APP_DB);
  let moved = 0;

  const {databases} = await client.db('admin').admin().listDatabases();
  const extraDbs = databases.map(item => item.name).filter(name => name.startsWith('calltech_'));

  for (const dbName of extraDbs) {
    const sourceDb = client.db(dbName);
    for (const type of ['messages', 'callLogs']) {
      const source = sourceDb.collection(type);
      const total = await source.countDocuments();
      if (total === 0) {
        continue;
      }

      const targetName = type === 'messages' ? target.messages : target.callLogs;
      console.log(
        `${apply ? '[apply]' : '[dry-run]'} ${dbName}.${type} -> ${APP_DB}.${targetName} (${total})`,
      );

      if (!apply) {
        moved += total;
        continue;
      }

      moved += await copyDocs(source, calltechDb.collection(targetName), target.simNumber);
    }

    if (apply) {
      await client.db(dbName).dropDatabase();
      console.log(`[cleanup] dropped database ${dbName}`);
    } else {
      console.log(`[dry-run] drop database ${dbName}`);
    }
  }

  return moved;
}

async function dropOldCollections(db) {
  const names = (await db.listCollections().toArray()).map(item => item.name);

  for (const name of names) {
    const shouldDrop =
      LEGACY_COLLECTIONS.includes(name) ||
      name.startsWith('device_') ||
      name === 'messages' ||
      name === 'callLogs';

    if (!shouldDrop) {
      continue;
    }

    if (apply) {
      await db.collection(name).drop();
      console.log(`[cleanup] dropped ${APP_DB}.${name}`);
    } else {
      console.log(`[dry-run] drop ${APP_DB}.${name}`);
    }
  }
}

async function run() {
  await client.connect();
  const calltechDb = client.db(APP_DB);

  if (!simNumber) {
    console.error('SIM_NUMBER required');
    console.error('Example: SIM_NUMBER=919024373168 node finalize-sim-collections.js --apply');
    process.exitCode = 1;
    return;
  }

  const target = getSimCollections(simNumber);
  let moved = 0;
  moved += await migrateLegacy(calltechDb, simNumber);
  moved += await migrateSeparateSimDatabases(simNumber);
  await dropOldCollections(calltechDb);

  const prefixes = listSimPrefixes(
    (await calltechDb.listCollections().toArray()).map(item => item.name),
  );

  console.log('');
  console.log(`Database: ${APP_DB}`);
  console.log(`Target collections: ${target.messages}, ${target.callLogs}`);
  console.log(`Docs migrated: ${moved}`);
  console.log('SIM collections:');
  for (const prefix of prefixes) {
    const cols = getSimCollections(prefix);
    const [messageCount, callCount] = await Promise.all([
      calltechDb.collection(cols.messages).countDocuments(),
      calltechDb.collection(cols.callLogs).countDocuments(),
    ]);
    console.log(`  ${prefix}/`);
    console.log(`    ${cols.messages} (${messageCount})`);
    console.log(`    ${cols.callLogs} (${callCount})`);
  }

  if (!apply) {
    console.log('\nDry run — re-run with --apply');
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
