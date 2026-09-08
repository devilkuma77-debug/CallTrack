const APP_PREFIX = 'calltech';
const APP_DB = process.env.MONGODB_DB_NAME || APP_PREFIX;

function sanitizeSimNumber(simNumber) {
  const raw = String(simNumber || '').trim();
  if (raw.startsWith('phone_') || raw.startsWith('sim')) {
    return raw.replace(/[^a-zA-Z0-9_]/g, '');
  }

  const digits = raw.replace(/\D/g, '');
  if (!digits) {
    return 'unknown';
  }

  if (digits.length >= 12 && digits.startsWith('91')) {
    return digits.slice(-10);
  }
  if (digits.length === 11 && digits.startsWith('0')) {
    return digits.slice(-10);
  }

  return digits;
}

function normalizeSimNumber(simNumber) {
  const raw = String(simNumber || '').trim();
  if (!raw) {
    return '';
  }

  if (raw.startsWith('phone_') || raw.startsWith('sim')) {
    return raw;
  }

  const digits = raw.replace(/\D/g, '');
  if (!digits) {
    return raw;
  }

  if (digits.length === 10) {
    return `+91${digits}`;
  }
  if (digits.length === 12 && digits.startsWith('91')) {
    return `+${digits}`;
  }
  if (raw.startsWith('+')) {
    return `+${digits}`;
  }

  return `+${digits}`;
}

function normalizeDeviceId(deviceId) {
  const raw = String(deviceId || '').trim();
  if (!raw) {
    return '';
  }

  if (raw.startsWith('phone_')) {
    return raw.replace(/[^a-zA-Z0-9_]/g, '');
  }

  return sanitizeSimNumber(raw);
}

/** Sab devices ek hi database me — calltech */
function getSimDatabaseName(_simNumber) {
  return APP_DB;
}

function buildCollectionNames(prefix) {
  return {
    messages: `${prefix}-massage`,
    callLogs: `${prefix}-call`,
  };
}

function getSimCollections(simNumber) {
  const prefix = sanitizeSimNumber(simNumber);
  const names = buildCollectionNames(prefix);
  return {
    simNumber: normalizeSimNumber(simNumber),
    prefix,
    database: APP_DB,
    messages: names.messages,
    callLogs: names.callLogs,
  };
}

function getDeviceCollections(deviceId) {
  return getSimCollections(deviceId);
}

function extractSimPrefixFromCollection(name) {
  const match = String(name || '').match(/^(.+?)-(?:massage|call|messages|callLogs)$/);
  return match ? match[1] : null;
}

function listSimPrefixes(collectionNames) {
  const prefixes = new Set();

  for (const name of collectionNames) {
    const prefix = extractSimPrefixFromCollection(name);
    if (prefix) {
      prefixes.add(prefix);
    }
  }

  return [...prefixes].sort();
}

module.exports = {
  APP_PREFIX,
  APP_DB,
  sanitizeSimNumber,
  normalizeSimNumber,
  normalizeDeviceId,
  getSimDatabaseName,
  getSimCollections,
  getDeviceCollections,
  buildCollectionNames,
  extractSimPrefixFromCollection,
  listSimPrefixes,
};
