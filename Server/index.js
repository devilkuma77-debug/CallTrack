require('dotenv').config();

const cors = require('cors');
const express = require('express');

const {
  connectMongo,
  getClient,
  getSimCollection,
  getDeviceCollection,
  listSimPrefixesInCalltech,
  isMongoConnected,
} = require('./db');
const {
  getSimCollections,
  getDeviceCollections,
  normalizeSimNumber,
  normalizeDeviceId,
  sanitizeSimNumber,
  APP_DB,
} = require('./simCollections');

const app = express();
const PORT = Number(process.env.PORT || 3000);

app.use(cors());
app.use(express.json({limit: '50mb'}));

function resolveIdentity(source) {
  const simNumber = normalizeSimNumber(source?.simNumber || source?.query?.simNumber);
  if (simNumber && !simNumber.startsWith('phone_') && !simNumber.startsWith('sim')) {
    return simNumber;
  }

  const deviceId = normalizeDeviceId(source?.deviceId || source?.query?.deviceId);
  return simNumber || deviceId || null;
}

function validateSyncItem(item, type) {
  const errors = [];
  const id = String(item?.id || item?.eventId || '').trim();
  const timestamp = Number(item?.timestamp);

  if (!id) {
    errors.push('id/eventId required');
  }

  if (!Number.isFinite(timestamp) || timestamp <= 0) {
    errors.push('valid timestamp required');
  }

  if (type === 'messages') {
    const body = String(item?.body || item?.message || '').trim();
    if (!body) {
      errors.push('message body required');
    }
  }

  if (type === 'callLogs') {
    const phoneNumber = String(item?.phoneNumber || '').trim();
    if (!phoneNumber) {
      errors.push('phoneNumber required');
    }
  }

  return errors;
}

function normalizeSyncItem(item, identity, type) {
  const collections = getSimCollections(item.simNumber || identity);
  const eventId = String(item.eventId || item.id || '').trim();
  const deviceId = String(item.deviceId || collections.prefix).trim();

  return {
    ...item,
    id: String(item.id || `${collections.prefix}_${eventId}`),
    eventId,
    deviceId,
    timestamp: Number(item.timestamp),
    syncStatus: 'synced',
    simNumber: item.simNumber || collections.simNumber || identity,
    type: item.type || (type === 'messages' ? 'INBOX' : 'UNKNOWN'),
  };
}

async function countDeviceCollections(identity) {
  const collections = getSimCollections(identity);
  const db = getClient().db(collections.database);
  const [messageCount, callCount] = await Promise.all([
    db.collection(collections.messages).countDocuments(),
    db.collection(collections.callLogs).countDocuments(),
  ]);

  return {
    deviceId: collections.prefix,
    simNumber: collections.simNumber || normalizeSimNumber(identity),
    prefix: collections.prefix,
    database: collections.database,
    messageCount,
    callCount,
  };
}

const MONGO_OFFLINE_ERROR =
  'MongoDB connected nahi hai. Is WiFi pe port 27017 block hai — phone hotspot/VPN on karo, phir Server restart karo.';

function mongoOfflinePayload() {
  return {
    ok: false,
    success: false,
    mongo: false,
    error: MONGO_OFFLINE_ERROR,
    sims: [],
    devices: [],
    data: [],
    messageCount: 0,
    callCount: 0,
  };
}

function requireMongo(_req, res, next) {
  if (!isMongoConnected()) {
    return res.json(mongoOfflinePayload());
  }
  next();
}

app.get('/api/stats', requireMongo, async (req, res) => {
  try {
    const identity = resolveIdentity({query: req.query});

    if (identity) {
      const stats = await countDeviceCollections(identity);
      return res.json({
        success: true,
        database: stats.database,
        deviceId: stats.deviceId,
        simNumber: stats.simNumber,
        messageCount: stats.messageCount,
        callCount: stats.callCount,
      });
    }

    const prefixes = await listAllSimPrefixes();
    const deviceStats = await Promise.all(
      prefixes.map(prefix => countDeviceCollections(prefix)),
    );

    res.json({
      success: true,
      devices: deviceStats,
      sims: deviceStats,
      messageCount: deviceStats.reduce((total, item) => total + item.messageCount, 0),
      callCount: deviceStats.reduce((total, item) => total + item.callCount, 0),
    });
  } catch (error) {
    res.status(500).json({success: false, error: error.message});
  }
});

async function listAllSimPrefixes() {
  const prefixes = new Set(await listSimPrefixesInCalltech());

  try {
    const devices = await getClient().db(APP_DB).collection('devices').find({}).toArray();
    for (const device of devices) {
      const prefix = sanitizeSimNumber(device.simNumber || device.deviceId || '');
      if (prefix && prefix !== 'unknown') {
        prefixes.add(prefix);
      }
    }
  } catch (_error) {
    // devices collection optional
  }

  return [...prefixes].sort();
}

async function ensureSimListed(identity) {
  const collections = getSimCollections(identity);
  const now = Date.now();
  const messageCol = await getDeviceCollection(identity, 'messages');
  const callCol = await getDeviceCollection(identity, 'callLogs');

  await messageCol.updateOne(
    {id: `sim_registration_${collections.prefix}_message`},
    {
      $set: {
        id: `sim_registration_${collections.prefix}_message`,
        simNumber: collections.simNumber,
        phoneNumber: collections.simNumber || collections.prefix,
        name: 'CallTech SIM',
        body: 'SIM registered in MongoDB',
        message: 'SIM registered in MongoDB',
        type: 'REGISTRATION',
        timestamp: now,
        syncedFrom: 'device_register',
      },
    },
    {upsert: true},
  );

  await callCol.updateOne(
    {id: `sim_registration_${collections.prefix}_call`},
    {
      $set: {
        id: `sim_registration_${collections.prefix}_call`,
        simNumber: collections.simNumber,
        phoneNumber: collections.simNumber || collections.prefix,
        name: 'CallTech SIM',
        type: 'REGISTRATION',
        duration: 0,
        durationSeconds: 0,
        durationFormatted: '0s',
        timestamp: now,
        rawType: 0,
        callAction: 'sim_registered',
        callActionLabel: 'SIM registered',
        hasRecording: false,
        recordingUrl: '',
        syncedFrom: 'device_register',
      },
    },
    {upsert: true},
  );

  return collections;
}

async function deleteSimData(identity) {
  const collections = getSimCollections(identity);
  const db = getClient().db(collections.database);
  const names = await db.listCollections().toArray();
  const existing = new Set(names.map(item => item.name));
  const dropped = [];

  for (const name of [collections.messages, collections.callLogs]) {
    if (existing.has(name)) {
      await db.collection(name).drop();
      dropped.push(name);
    }
  }

  const deviceResult = await db.collection('devices').deleteMany({
    $or: [
      {deviceId: collections.prefix},
      {deviceId: identity},
      {simNumber: identity},
      {simNumber: collections.simNumber},
      {simNumber: collections.prefix},
    ],
  });

  return {
    prefix: collections.prefix,
    simNumber: collections.simNumber,
    dropped,
    devicesRemoved: deviceResult.deletedCount || 0,
  };
}

app.get('/api/sims', requireMongo, async (_req, res) => {
  try {
    const prefixes = await listAllSimPrefixes();
    const sims = await Promise.all(
      prefixes.map(prefix => countDeviceCollections(prefix)),
    );

    res.json({
      success: true,
      database: APP_DB,
      sims,
      devices: sims,
    });
  } catch (error) {
    res.status(500).json({success: false, error: error.message});
  }
});

app.delete('/api/sims', requireMongo, async (req, res) => {
  try {
    const identity = resolveIdentity({
      query: req.query,
      simNumber: req.body?.simNumber,
      deviceId: req.body?.deviceId,
    });

    if (!identity) {
      return res.status(400).json({success: false, error: 'simNumber required'});
    }

    const result = await deleteSimData(identity);
    res.json({
      success: true,
      ...result,
    });
  } catch (error) {
    res.status(500).json({success: false, error: error.message});
  }
});

app.get('/api/devices', requireMongo, async (_req, res) => {
  try {
    const devices = await getClient()
      .db(APP_DB)
      .collection('devices')
      .find({})
      .sort({updatedAt: -1})
      .limit(100)
      .toArray();

    res.json({success: true, database: APP_DB, devices});
  } catch (error) {
    res.status(500).json({success: false, error: error.message});
  }
});

app.post('/api/devices/register', async (req, res) => {
  try {
    if (!isMongoConnected()) {
      return res.status(503).json({success: false, error: MONGO_OFFLINE_ERROR});
    }

    const deviceId = normalizeDeviceId(req.body?.deviceId);
    if (!deviceId) {
      return res.status(400).json({success: false, error: 'deviceId required'});
    }

    const now = new Date();
    const simNumber = normalizeSimNumber(req.body?.simNumber || deviceId);
    const doc = {
      deviceId,
      simNumber,
      model: String(req.body?.model || 'unknown'),
      manufacturer: String(req.body?.manufacturer || 'unknown'),
      appVersion: String(req.body?.appVersion || ''),
      updatedAt: now,
    };

    const collection = getClient().db(APP_DB).collection('devices');
    await collection.updateOne(
      {deviceId},
      {$set: doc, $setOnInsert: {registeredAt: now}},
      {upsert: true},
    );

    const listed = await ensureSimListed(simNumber || deviceId);

    res.json({
      success: true,
      deviceId,
      simNumber: listed.simNumber,
      prefix: listed.prefix,
      database: APP_DB,
    });
  } catch (error) {
    res.status(500).json({success: false, error: error.message});
  }
});

app.get('/api/health', async (_req, res) => {
  if (!isMongoConnected()) {
    return res.json({ok: false, mongo: false, error: MONGO_OFFLINE_ERROR});
  }

  try {
    await getClient().db('admin').command({ping: 1});
    res.json({ok: true, mongo: true});
  } catch (error) {
    res.json({ok: false, mongo: false, error: error.message});
  }
});

app.get('/api/messages', requireMongo, async (req, res) => {
  try {
    const identity = resolveIdentity({query: req.query});
    if (!identity) {
      return res.status(400).json({success: false, error: 'deviceId or simNumber query param required'});
    }

    const requested = Number(req.query.limit || 0);
    let cursor = (await getDeviceCollection(identity, 'messages')).find({}).sort({timestamp: -1});

    if (requested > 0) {
      cursor = cursor.limit(requested);
    }

    const data = await cursor.toArray();
    const {database, deviceId} = getDeviceCollections(identity);
    res.json({success: true, deviceId, simNumber: identity, database, data});
  } catch (error) {
    res.status(500).json({success: false, error: error.message});
  }
});

app.post('/api/messages/sync', async (req, res) => {
  try {
    const identity = resolveIdentity({
      deviceId: req.body?.deviceId,
      simNumber: req.body?.simNumber,
    });
    const messages = Array.isArray(req.body?.messages) ? req.body.messages : [];

    if (!identity) {
      return res.status(400).json({success: false, error: 'deviceId or simNumber required in request body'});
    }

    if (messages.length === 0) {
      return res.json({success: true, saved: 0, message: 'No messages to sync'});
    }

    const validationErrors = [];
    messages.forEach((item, index) => {
      const itemErrors = validateSyncItem(item, 'messages');
      if (itemErrors.length > 0) {
        validationErrors.push({index, errors: itemErrors});
      }
    });

    if (validationErrors.length > 0) {
      return res.status(400).json({
        success: false,
        error: 'Validation failed',
        details: validationErrors.slice(0, 20),
      });
    }

    const {database, deviceId} = getDeviceCollections(identity);
    console.log(`[sync] messages: ${messages.length} db=${database} device=${deviceId} from ${req.ip || 'unknown'}`);

    const collection = await getDeviceCollection(identity, 'messages');
    const now = new Date();

    const ops = messages.map(item => {
      const normalized = normalizeSyncItem(item, identity, 'messages');
      return {
        updateOne: {
          filter: {id: normalized.id},
          update: {
            $set: {
              ...normalized,
              syncedAt: now,
            },
          },
          upsert: true,
        },
      };
    });

    const result = await collection.bulkWrite(ops, {ordered: false});

    res.json({
      success: true,
      deviceId,
      simNumber: normalizeSimNumber(identity),
      database,
      saved: ops.length,
      upserted: result.upsertedCount,
      modified: result.modifiedCount,
    });
  } catch (error) {
    res.status(500).json({success: false, error: error.message});
  }
});

app.get('/api/callLogs', requireMongo, async (req, res) => {
  try {
    const identity = resolveIdentity({query: req.query});
    if (!identity) {
      return res.status(400).json({success: false, error: 'deviceId or simNumber query param required'});
    }

    const requested = Number(req.query.limit || 0);
    let cursor = (await getDeviceCollection(identity, 'callLogs')).find({}).sort({timestamp: -1});

    if (requested > 0) {
      cursor = cursor.limit(requested);
    }

    const data = await cursor.toArray();
    const {database, deviceId} = getDeviceCollections(identity);
    res.json({success: true, deviceId, simNumber: identity, database, data});
  } catch (error) {
    res.status(500).json({success: false, error: error.message});
  }
});

app.post('/api/callLogs/sync', async (req, res) => {
  try {
    const identity = resolveIdentity({
      deviceId: req.body?.deviceId,
      simNumber: req.body?.simNumber,
    });
    const callLogs = Array.isArray(req.body?.callLogs) ? req.body.callLogs : [];

    if (!identity) {
      return res.status(400).json({success: false, error: 'deviceId or simNumber required in request body'});
    }

    if (callLogs.length === 0) {
      return res.json({success: true, saved: 0, message: 'No call logs to sync'});
    }

    const validationErrors = [];
    callLogs.forEach((item, index) => {
      const itemErrors = validateSyncItem(item, 'callLogs');
      if (itemErrors.length > 0) {
        validationErrors.push({index, errors: itemErrors});
      }
    });

    if (validationErrors.length > 0) {
      return res.status(400).json({
        success: false,
        error: 'Validation failed',
        details: validationErrors.slice(0, 20),
      });
    }

    const {database, deviceId} = getDeviceCollections(identity);
    console.log(`[sync] callLogs: ${callLogs.length} db=${database} device=${deviceId} from ${req.ip || 'unknown'}`);

    const collection = await getDeviceCollection(identity, 'callLogs');
    const now = new Date();

    const ops = callLogs.map(item => {
      const normalized = normalizeSyncItem(item, identity, 'callLogs');
      return {
        updateOne: {
          filter: {id: normalized.id},
          update: {
            $set: {
              ...normalized,
              syncedAt: now,
            },
          },
          upsert: true,
        },
      };
    });

    const result = await collection.bulkWrite(ops, {ordered: false});

    res.json({
      success: true,
      deviceId,
      simNumber: normalizeSimNumber(identity),
      database,
      saved: ops.length,
      upserted: result.upsertedCount,
      modified: result.modifiedCount,
    });
  } catch (error) {
    res.status(500).json({success: false, error: error.message});
  }
});

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function connectMongoWithRetry() {
  let attempt = 1;

  while (true) {
    try {
      await connectMongo();
      return;
    } catch (error) {
      if (attempt <= 2) {
        console.error(`[FAIL] MongoDB: ${error.message}`);
      }
      if (attempt === 2) {
        console.error(`
Is WiFi pe MongoDB port 27017 BLOCK hai. Code se ye fix nahi hota.
Atlas HTTPS (443) chal raha hai, database port nahi.

Data dekhne ke liye:
  1. Phone hotspot ON karo (ya VPN)
  2. Is terminal me Ctrl+C, phir: node .\\index.js
  3. "MongoDB connected" aaye to Admin refresh karo
`);
      }
      attempt += 1;
      await delay(attempt <= 3 ? 3000 : 60000);
    }
  }
}

app.listen(PORT, '0.0.0.0', () => {
  console.log(`CallTech Mongo API: http://0.0.0.0:${PORT}/api/health`);
  console.log('Structure: calltech -> {phoneNumber}-massage / {phoneNumber}-call');
  connectMongoWithRetry();
});
