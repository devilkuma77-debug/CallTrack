require('dotenv').config();

const {MongoClient} = require('mongodb');
const {APP_DB, listSimPrefixes} = require('./simCollections');

async function dedupeCollection(collection) {
  const groups = await collection
    .aggregate([
      {$match: {id: {$type: 'string', $ne: ''}}},
      {$sort: {timestamp: -1, syncedAt: -1}},
      {
        $group: {
          _id: '$id',
          keep: {$first: '$_id'},
          extras: {$push: '$_id'},
          n: {$sum: 1},
        },
      },
      {$match: {n: {$gt: 1}}},
    ])
    .toArray();

  let removed = 0;
  for (const group of groups) {
    const extra = (group.extras || []).filter(item => String(item) !== String(group.keep));
    if (extra.length === 0) {
      continue;
    }
    const result = await collection.deleteMany({_id: {$in: extra}});
    removed += result.deletedCount;
    console.log(`  ${collection.collectionName} id=${group._id} removed ${result.deletedCount}`);
  }
  return removed;
}

async function replaceWithUniqueIdIndex(collection) {
  const indexes = await collection.indexes();
  const idIndex = indexes.find(item => item.name === 'id_1');
  if (idIndex && !idIndex.unique) {
    await collection.dropIndex('id_1');
  }
  await collection.createIndex({id: 1}, {unique: true});
}

(async () => {
  const uri = process.env.MONGODB_URI;
  if (!uri) {
    console.error('MONGODB_URI missing');
    process.exit(1);
  }

  const client = new MongoClient(uri);
  await client.connect();
  const db = client.db(APP_DB);
  const names = (await db.listCollections().toArray()).map(item => item.name);
  const prefixes = listSimPrefixes(names);

  let total = 0;
  for (const prefix of prefixes) {
    for (const suffix of ['massage', 'call']) {
      const name = `${prefix}-${suffix}`;
      if (!names.includes(name)) {
        continue;
      }
      const collection = db.collection(name);
      const removed = await dedupeCollection(collection);
      total += removed;
      try {
        await replaceWithUniqueIdIndex(collection);
        console.log(`[ok] unique id index: ${name}`);
      } catch (error) {
        console.warn(`[warn] ${name}: ${error.message}`);
      }
    }
  }

  console.log(`\nDone. Duplicate docs removed: ${total}`);
  await client.close();
})().catch(error => {
  console.error(error);
  process.exit(1);
});
