import {MONGO_API_URL, MONGO_ENABLED} from '../config/mongoConfig';
import {FETCH_LIMIT} from './syncLimits';

const BATCH_SIZE = 150;

function sanitizeMessageForMongo(item) {
  const body = String(item?.body || item?.message || '');

  return {
    id: String(item?.id || ''),
    phoneNumber: String(item?.phoneNumber || 'Unknown'),
    name: String(item?.name || item?.phoneNumber || 'Unknown'),
    body,
    message: body,
    type: String(item?.type || 'INBOX').toUpperCase(),
    timestamp: Number(item?.timestamp) || Date.now(),
    smsId: item?.smsId != null ? Number(item.smsId) : null,
    dateTime: String(
      item?.dateTime || new Date(Number(item?.timestamp) || Date.now()).toISOString(),
    ),
    syncedFrom: String(item?.syncedFrom || 'app'),
  };
}

function sanitizeCallLogForMongo(item) {
  return {
    id: String(item?.id || ''),
    phoneNumber: String(item?.phoneNumber || 'Unknown'),
    name: String(item?.name || 'Unknown'),
    type: String(item?.type || 'UNKNOWN').toUpperCase(),
    duration: Number(item?.duration || 0),
    durationSeconds: Number(item?.durationSeconds ?? item?.duration ?? 0),
    durationFormatted: String(item?.durationFormatted || ''),
    timestamp: Number(item?.timestamp) || Date.now(),
    dateTime: String(item?.dateTime || ''),
    rawType: Number(item?.rawType || 0),
    callAction: String(item?.callAction || 'unknown'),
    callActionLabel: String(item?.callActionLabel || ''),
    hasRecording: Boolean(item?.hasRecording),
    recordingUrl: String(item?.recordingUrl || ''),
    recordingDurationMs: Number(item?.recordingDurationMs || 0),
    recordingDurationFormatted: String(item?.recordingDurationFormatted || ''),
    syncedFrom: String(item?.syncedFrom || 'app_sync'),
  };
}

async function request(path, options = {}) {
  if (!MONGO_ENABLED || !MONGO_API_URL) {
    return {success: false, message: 'Direct Atlas mode — HTTP API disabled'};
  }

  try {
    const response = await fetch(`${MONGO_API_URL}${path}`, {
      headers: {
        'Content-Type': 'application/json',
        ...(options.headers || {}),
      },
      ...options,
    });

    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
      return {
        success: false,
        message: data.error || data.message || `HTTP ${response.status}`,
      };
    }

    return data;
  } catch (error) {
    return {
      success: false,
      message: error?.message || 'MongoDB server connect nahi ho paaya',
    };
  }
}

async function syncInBatches(path, key, items = [], simNumber = '') {
  if (!Array.isArray(items) || items.length === 0) {
    return {success: true, saved: 0};
  }

  let totalSaved = 0;

  for (let index = 0; index < items.length; index += BATCH_SIZE) {
    const batch = items.slice(index, index + BATCH_SIZE);
    const result = await request(path, {
      method: 'POST',
      body: JSON.stringify({simNumber, [key]: batch}),
    });

    if (!result.success) {
      return result;
    }

    totalSaved += batch.length;
  }

  return {success: true, saved: totalSaved};
}

export async function checkMongoConnection() {
  return request('/health');
}

export async function fetchMongoStats(simNumber = '') {
  const query = simNumber ? `?simNumber=${encodeURIComponent(simNumber)}` : '';
  return request(`/stats${query}`);
}

export async function fetchMongoMessages(limit = FETCH_LIMIT, simNumber = '') {
  if (!simNumber) {
    return [];
  }

  const query = limit > 0 ? `?simNumber=${encodeURIComponent(simNumber)}&limit=${limit}` : `?simNumber=${encodeURIComponent(simNumber)}&limit=0`;
  const result = await request(`/messages${query}`);

  if (!result.success) {
    return [];
  }

  return Array.isArray(result.data) ? result.data : [];
}

export async function syncMessagesToMongo(messages = [], simNumber = '') {
  if (!Array.isArray(messages) || messages.length === 0) {
    return {success: true, saved: 0, message: 'Koi message MongoDB ke liye nahi mila.'};
  }

  if (!simNumber) {
    return {success: false, message: 'simNumber required for MongoDB sync'};
  }

  const sanitized = messages
    .map(item => ({
      ...sanitizeMessageForMongo(item),
      simNumber,
    }))
    .filter(item => item.id && item.phoneNumber !== 'Unknown');

  if (sanitized.length === 0) {
    return {success: true, saved: 0, message: 'Valid SMS MongoDB ke liye nahi mile.'};
  }

  const result = await syncInBatches('/messages/sync', 'messages', sanitized, simNumber);

  if (!result.success) {
    return result;
  }

  return {
    success: true,
    saved: result.saved || 0,
    message: `MongoDB me ${result.saved || 0} messages save hue.`,
  };
}

export async function fetchMongoCallLogs(limit = FETCH_LIMIT, simNumber = '') {
  if (!simNumber) {
    return [];
  }

  const query = limit > 0 ? `?simNumber=${encodeURIComponent(simNumber)}&limit=${limit}` : `?simNumber=${encodeURIComponent(simNumber)}&limit=0`;
  const result = await request(`/callLogs${query}`);

  if (!result.success) {
    return [];
  }

  return Array.isArray(result.data) ? result.data : [];
}

export async function syncCallLogsToMongo(callLogs = [], simNumber = '') {
  if (!Array.isArray(callLogs) || callLogs.length === 0) {
    return {success: true, saved: 0, message: 'Koi call log MongoDB ke liye nahi mila.'};
  }

  if (!simNumber) {
    return {success: false, message: 'simNumber required for MongoDB sync'};
  }

  const sanitized = callLogs
    .map(item => ({
      ...sanitizeCallLogForMongo(item),
      simNumber,
    }))
    .filter(item => item.id && item.phoneNumber !== 'Unknown');

  if (sanitized.length === 0) {
    return {success: true, saved: 0, message: 'Valid call logs MongoDB ke liye nahi mile.'};
  }

  const result = await syncInBatches('/callLogs/sync', 'callLogs', sanitized, simNumber);

  if (!result.success) {
    return result;
  }

  return {
    success: true,
    saved: result.saved || 0,
    message: `MongoDB me ${result.saved || 0} call logs save hue.`,
  };
}
