export function formatDurationSeconds(seconds) {
  const total = Number(seconds) || 0;
  const minutes = Math.floor(total / 60);
  const remaining = total % 60;

  if (minutes > 0) {
    return `${minutes}m ${remaining}s`;
  }

  return `${remaining}s`;
}

export function getCallAction(type, duration) {
  const normalized = String(type || '').toUpperCase();
  const dur = Number(duration) || 0;

  if (normalized === 'MISSED') {
    return 'missed_call';
  }

  if (normalized === 'INCOMING') {
    return dur > 0 ? 'incoming_answered' : 'incoming_not_answered';
  }

  if (normalized === 'OUTGOING') {
    return dur > 0 ? 'outgoing_connected' : 'outgoing_no_answer';
  }

  return 'unknown';
}

export function getCallActionLabel(action) {
  const labels = {
    missed_call: 'Missed call',
    incoming_answered: 'Incoming · Answered',
    incoming_not_answered: 'Incoming · Not answered',
    outgoing_connected: 'Outgoing · Connected',
    outgoing_no_answer: 'Outgoing · No answer',
    unknown: 'Unknown activity',
  };

  return labels[action] || labels.unknown;
}

export function buildCallPayload(log, index = 0) {
  const phoneNumber = String(log?.phoneNumber || 'Unknown');
  const name =
    log?.name && String(log.name).trim().length > 0
      ? String(log.name).trim()
      : 'Unknown';
  const type = String(log?.type || 'UNKNOWN').toUpperCase();
  const duration = Number(log?.duration || log?.durationSeconds || 0);
  const timestamp = Number(log?.timestamp || Date.now());
  const callAction = log?.callAction || getCallAction(type, duration);

  return {
    phoneNumber,
    name,
    type,
    duration,
    durationSeconds: duration,
    durationFormatted:
      log?.durationFormatted || formatDurationSeconds(duration),
    timestamp,
    dateTime: log?.dateTime
      ? String(log.dateTime)
      : new Date(timestamp).toISOString(),
    rawType: Number(log?.rawType || 0),
    callAction,
    callActionLabel:
      log?.callActionLabel || getCallActionLabel(callAction),
    hasRecording: Boolean(log?.hasRecording),
    recordingUrl: log?.recordingUrl ? String(log.recordingUrl) : '',
    recordingDurationMs: Number(log?.recordingDurationMs || 0),
    recordingDurationFormatted: log?.recordingDurationFormatted || '',
    syncedFrom: log?.syncedFrom || 'app',
    id: log?.id || `${phoneNumber}_${timestamp}_${index}`,
  };
}
