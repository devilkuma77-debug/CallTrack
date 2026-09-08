function sanitizePhone(phone) {
  return String(phone || 'Unknown').replace(/[\/.#$[\]+]/g, '_');
}

function normalizeTimestamp(value) {
  const numberValue = Number(value);

  if (!isNaN(numberValue)) {
    if (numberValue > 100000000000) {
      return numberValue;
    }

    if (numberValue > 1000000000) {
      return numberValue * 1000;
    }
  }

  return Date.now();
}

export function buildMessagePayload(item, index = 0) {
  const phoneNumber = String(item?.phoneNumber || item?.address || 'Unknown');
  const timestamp = normalizeTimestamp(item?.timestamp || item?.date);
  const body = String(item?.body || item?.message || '');
  const smsId = item?.smsId ? String(item.smsId) : '';

  return {
    id:
      item?.id ||
      `${sanitizePhone(phoneNumber)}_${timestamp}_${smsId || index}`,
    phoneNumber,
    name: item?.name && String(item.name).trim().length > 0 ? String(item.name) : phoneNumber,
    body,
    message: body,
    type: String(item?.type || 'INBOX').toUpperCase(),
    timestamp,
    smsId: item?.smsId || null,
    dateTime: item?.dateTime ? String(item.dateTime) : new Date(timestamp).toLocaleString(),
    syncedFrom: item?.syncedFrom || 'app',
  };
}

export function sortByNewest(items = []) {
  return [...items].sort((a, b) => {
    const timeDiff = (b.timestamp || 0) - (a.timestamp || 0);
    if (timeDiff !== 0) {
      return timeDiff;
    }

    return String(b.id || '').localeCompare(String(a.id || ''));
  });
}
