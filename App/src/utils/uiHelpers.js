import {AVATAR_PALETTE} from '../theme/theme';

export function getAvatarColor(seed = '') {
  let hash = 0;

  for (let i = 0; i < seed.length; i++) {
    hash = seed.charCodeAt(i) + ((hash << 5) - hash);
  }

  return AVATAR_PALETTE[Math.abs(hash) % AVATAR_PALETTE.length];
}

export function getInitial(name, phoneNumber) {
  if (name && name !== 'Unknown' && String(name).trim().length > 0) {
    return String(name).trim().charAt(0).toUpperCase();
  }

  if (phoneNumber) {
    const number = String(phoneNumber);
    return number.charAt(number.length - 1);
  }

  return '#';
}

export function formatRelativeTime(value) {
  const timestamp = Number(value);

  if (!timestamp) {
    return String(value || '');
  }

  const date = new Date(timestamp);

  if (isNaN(date.getTime())) {
    return String(value || '');
  }

  const now = new Date();
  const isToday =
    date.getDate() === now.getDate() &&
    date.getMonth() === now.getMonth() &&
    date.getFullYear() === now.getFullYear();

  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  const isYesterday =
    date.getDate() === yesterday.getDate() &&
    date.getMonth() === yesterday.getMonth() &&
    date.getFullYear() === yesterday.getFullYear();

  const time = date.toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'});

  if (isToday) {
    return `Aaj ${time}`;
  }

  if (isYesterday) {
    return `Kal ${time}`;
  }

  return (
    date.toLocaleDateString([], {day: 'numeric', month: 'short', year: 'numeric'}) +
    ` · ${time}`
  );
}

export function getDateSectionLabel(timestamp) {
  const date = new Date(Number(timestamp));

  if (isNaN(date.getTime())) {
    return 'Purane';
  }

  const now = new Date();
  const isToday =
    date.getDate() === now.getDate() &&
    date.getMonth() === now.getMonth() &&
    date.getFullYear() === now.getFullYear();

  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  const isYesterday =
    date.getDate() === yesterday.getDate() &&
    date.getMonth() === yesterday.getMonth() &&
    date.getFullYear() === yesterday.getFullYear();

  if (isToday) {
    return 'Aaj';
  }

  if (isYesterday) {
    return 'Kal';
  }

  return date.toLocaleDateString([], {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  });
}

export function groupByDateSection(items, getTimestamp) {
  const sections = [];
  let currentLabel = null;
  let currentData = [];

  items.forEach(item => {
    const label = getDateSectionLabel(getTimestamp(item));

    if (label !== currentLabel) {
      if (currentData.length > 0) {
        sections.push({title: currentLabel, data: currentData});
      }

      currentLabel = label;
      currentData = [item];
      return;
    }

    currentData.push(item);
  });

  if (currentData.length > 0) {
    sections.push({title: currentLabel, data: currentData});
  }

  return sections;
}

export function isSentMessage(type) {
  const value = String(type || '').toUpperCase();
  return value === 'SENT' || value === 'OUTBOX';
}

export function getDisplayName(item) {
  const name = item?.name;
  const phone = item?.phoneNumber;

  if (name && name !== 'Unknown' && name !== phone) {
    return String(name);
  }

  return String(phone || 'Unknown');
}
