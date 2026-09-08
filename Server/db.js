const {MongoClient, ServerApiVersion} = require('mongodb');

const {
  getSimCollections,
  getDeviceCollections,
  APP_DB,
  listSimPrefixes,
} = require('./simCollections');

let client;
let connectingPromise;
const indexedCollections = new Set();

function createMongoClient(uri) {
  return new MongoClient(uri, {
    serverApi: {
      version: ServerApiVersion.v1,
      strict: true,
      deprecationErrors: true,
    },
    family: 4,
    serverSelectionTimeoutMS: 12000,
    connectTimeoutMS: 10000,
    socketTimeoutMS: 20000,
  });
}

function indexKey(databaseName, collectionName) {
  return `${databaseName}.${collectionName}`;
}

async function ensureSimIndexes(databaseName, collectionName) {
  if (!client) {
    return;
  }

  const key = indexKey(databaseName, collectionName);
  if (indexedCollections.has(key)) {
    return;
  }

  try {
    const collection = client.db(databaseName).collection(collectionName);
    await Promise.all([
      collection.createIndex({timestamp: -1}),
      collection.createIndex({deviceId: 1, timestamp: -1}),
      collection.createIndex({eventId: 1}),
    ]);

    await dedupeDuplicateIds(collection);

    try {
      await replaceWithUniqueIdIndex(collection);
    } catch (error) {
      console.warn(
        `[index] unique id failed on ${databaseName}.${collectionName}: ${error.message}`,
      );
    }
  } catch (error) {
    console.warn(`[index] skipped ${databaseName}.${collectionName}: ${error.message}`);
  }

  indexedCollections.add(key);
}

async function dedupeDuplicateIds(collection) {
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

  for (const group of groups) {
    const extra = (group.extras || []).filter(
      item => String(item) !== String(group.keep),
    );
    if (extra.length === 0) {
      continue;
    }

    const result = await collection.deleteMany({_id: {$in: extra}});
    console.log(
      `[dedupe] ${collection.collectionName} id=${group._id} removed ${result.deletedCount}`,
    );
  }
}

async function replaceWithUniqueIdIndex(collection) {
  const indexes = await collection.indexes();
  const idIndex = indexes.find(item => item.name === 'id_1');
  if (idIndex && !idIndex.unique) {
    await collection.dropIndex('id_1');
  }
  await collection.createIndex({id: 1}, {unique: true});
}

async function ensureDeviceRegistryIndexes() {
  if (!client) {
    return;
  }

  const key = indexKey(APP_DB, 'devices');
  if (indexedCollections.has(key)) {
    return;
  }

  const collection = client.db(APP_DB).collection('devices');
  await Promise.all([
    collection.createIndex({deviceId: 1}, {unique: true}),
    collection.createIndex({updatedAt: -1}),
  ]);

  indexedCollections.add(key);
}

async function connectMongo() {
  if (client) {
    return client;
  }

  if (connectingPromise) {
    return connectingPromise;
  }

  connectingPromise = (async () => {
    const uri = process.env.MONGODB_URI;

    if (!uri) {
      throw new Error('MONGODB_URI missing in server/.env');
    }

    const nextClient = createMongoClient(uri);
    await nextClient.connect();
    await nextClient.db('admin').command({ping: 1});
    client = nextClient;
    await ensureDeviceRegistryIndexes();
    console.log('MongoDB connected');

    return client;
  })();

  try {
    return await connectingPromise;
  } catch (error) {
    const failedClient = client;
    client = null;
    connectingPromise = null;
    if (failedClient) {
      failedClient.close().catch(() => {});
    }
    throw error;
  }
}

function getClient() {
  if (!client) {
    throw new Error('MongoDB not connected yet');
  }

  return client;
}

function isMongoConnected() {
  return Boolean(client);
}

function getSimDb(_simNumber) {
  return getClient().db(APP_DB);
}

async function getSimCollection(simNumber, type) {
  const collections = getSimCollections(simNumber);
  const collectionName = type === 'messages' ? collections.messages : collections.callLogs;
  await ensureSimIndexes(collections.database, collectionName);
  return getSimDb(simNumber).collection(collectionName);
}

async function getDeviceCollection(deviceId, type) {
  const collections = getDeviceCollections(deviceId);
  const collectionName = type === 'messages' ? collections.messages : collections.callLogs;
  await ensureSimIndexes(collections.database, collectionName);
  return getClient().db(collections.database).collection(collectionName);
}

async function listSimPrefixesInCalltech() {
  const names = (await getClient().db(APP_DB).listCollections().toArray()).map(item => item.name);
  return listSimPrefixes(names);
}

async function closeMongo() {
  if (client) {
    await client.close();
    client = null;
    connectingPromise = null;
    indexedCollections.clear();
  }
}

module.exports = {
  connectMongo,
  getClient,
  getSimDb,
  getSimCollection,
  getDeviceCollection,
  ensureSimIndexes,
  ensureDeviceRegistryIndexes,
  listSimPrefixesInCalltech,
  closeMongo,
  createMongoClient,
  isMongoConnected,
};
