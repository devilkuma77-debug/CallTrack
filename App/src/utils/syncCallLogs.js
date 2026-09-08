import CallLogs from 'react-native-call-log';

import {
  buildCallPayload,
  formatDurationSeconds,
  getCallAction,
  getCallActionLabel,
} from './callMetadata';
import {syncCallLogsToMongo as pushCallLogsToMongo} from './mongoApi';
import {CALL_LOG_LOAD_COUNT} from './syncLimits';

function normalizeTimestamp(value) {
  if (!value) {
    return Date.now();
  }

  const numberValue = Number(value);

  if (!isNaN(numberValue)) {
    if (numberValue > 100000000000) {
      return numberValue;
    }

    if (numberValue > 1000000000) {
      return numberValue * 1000;
    }
  }

  const parsed = new Date(value).getTime();

  if (!isNaN(parsed)) {
    return parsed;
  }

  return Date.now();
}

function normalizeType(type) {
  const value = String(type || '').trim().toUpperCase();

  if (value === 'INCOMING' || value === 'OUTGOING' || value === 'MISSED') {
    return value;
  }

  if (value === '1') return 'INCOMING';
  if (value === '2') return 'OUTGOING';
  if (value === '3') return 'MISSED';

  return 'UNKNOWN';
}

function buildCallDocument(log) {
  const phoneNumber = String(log?.phoneNumber || 'Unknown');
  const name =
    log?.name && String(log.name).trim().length > 0
      ? String(log.name).trim()
      : 'Unknown';
  const timestamp = normalizeTimestamp(log?.timestamp || log?.dateTime);
  const type = normalizeType(log?.type || log?.rawType);
  const duration = Number(log?.duration || 0);
  const callAction = getCallAction(type, duration);
  const dateTime = log?.dateTime
    ? String(log.dateTime)
    : new Date(timestamp).toISOString();
  const safePhone = phoneNumber.replace(/[\/.#$[\]+]/g, '_');
  const documentId = `${safePhone}_${timestamp}`;

  return {
    documentId,
    timestamp,
    data: {
      phoneNumber,
      name,
      type,
      duration,
      durationSeconds: duration,
      durationFormatted: formatDurationSeconds(duration),
      timestamp,
      dateTime,
      rawType: Number(log?.rawType || 0),
      callAction,
      callActionLabel: getCallActionLabel(callAction),
      hasRecording: Boolean(log?.hasRecording),
      recordingUrl: log?.recordingUrl ? String(log.recordingUrl) : '',
      recordingDurationMs: Number(log?.recordingDurationMs || 0),
      recordingDurationFormatted: log?.recordingDurationFormatted || '',
      syncedFrom: log?.syncedFrom || 'app_sync',
      syncedAt: new Date().toISOString(),
    },
  };
}

async function buildCallLogPayloads(existingLogs = null) {
  let logs = existingLogs;

  if (!logs) {
    logs = await CallLogs.load(CALL_LOG_LOAD_COUNT);
  }

  if (!Array.isArray(logs) || logs.length === 0) {
    return [];
  }

  return [...logs]
    .map(log => buildCallDocument(log))
    .sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0))
    .map(({documentId, data}) => ({
      id: documentId,
      ...data,
      syncedAt: new Date().toISOString(),
    }));
}

export async function syncCallLogsToMongo(existingLogs = null) {
  try {
    const payloads = await buildCallLogPayloads(existingLogs);

    if (payloads.length === 0) {
      return {
        success: true,
        message: 'Phone me koi call log nahi mila.',
        saved: 0,
      };
    }

    return pushCallLogsToMongo(payloads);
  } catch (error) {
    return {
      success: false,
      message: error?.message || 'MongoDB call sync fail ho gaya.',
      saved: 0,
    };
  }
}

export function mergeCallLists(...lists) {
  const map = new Map();

  lists.flat().forEach((item, index) => {
    const payload = buildCallPayload(item, index);
    const key = payload.id || `${payload.phoneNumber}_${payload.timestamp}`;

    if (!payload.phoneNumber || payload.phoneNumber === 'Unknown') {
      return;
    }

    map.set(key, payload);
  });

  return [...map.values()].sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0));
}
