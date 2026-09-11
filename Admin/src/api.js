const LIVE_API = 'https://calltrack-e62l.onrender.com';

function normalizeApiBase(raw) {
  let base = String(raw || '').trim().replace(/\/$/, '');
  if (base.endsWith('/api')) {
    base = base.slice(0, -4);
  }
  return base;
}

// Localhost par bhi Render API — debug LAN server se release data miss na ho.
const fromWindow =
  typeof window !== 'undefined' ? window.__CALLTECH_API__ : '';

const API_BASE = normalizeApiBase(
  import.meta.env.VITE_API_URL || fromWindow || LIVE_API,
);

const EMPTY = {
  ok: false,
  success: false,
  sims: [],
  devices: [],
  data: [],
  messageCount: 0,
  callCount: 0,
};

async function request(path, options = {}) {
  try {
    const method = String(options.method || 'GET').toUpperCase();
    const headers = {...(options.headers || {})};
    if (method !== 'GET' && method !== 'HEAD') {
      headers['Content-Type'] = headers['Content-Type'] || 'application/json';
    }

    const sep = path.includes('?') ? '&' : '?';
    const response = await fetch(`${API_BASE}${path}${sep}_=${Date.now()}`, {
      ...options,
      method,
      headers,
      cache: 'no-store',
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok && !data.error) {
      return {...EMPTY, ...data, error: `HTTP ${response.status}`};
    }
    return {...EMPTY, ...data};
  } catch (error) {
    return {
      ...EMPTY,
      error: error.message || 'API server se connect nahi ho paya',
    };
  }
}

export function getHealth() {
  return request('/api/health');
}

export function getStats() {
  return request('/api/stats');
}

export function getSims() {
  return request('/api/sims');
}

export function getDevices() {
  return request('/api/devices');
}

export function deleteSim(identity) {
  const query = new URLSearchParams({
    simNumber: apiIdentity(identity) || identity,
  });
  return request(`/api/sims?${query}`, {method: 'DELETE'});
}

/** API query — +91 mat bhejo; 10-digit / prefix se collection milti hai. */
export function apiIdentity(value) {
  const raw = String(value || '').trim();
  if (!raw) {
    return '';
  }
  if (raw.startsWith('phone_') || raw.startsWith('sim')) {
    return raw;
  }
  const digits = raw.replace(/\D/g, '');
  if (digits.length >= 12 && digits.startsWith('91')) {
    return digits.slice(-10);
  }
  if (digits.length >= 10) {
    return digits.slice(-10);
  }
  return raw;
}

export function getMessages(identity, limit = 500) {
  const sim = apiIdentity(identity);
  const query = new URLSearchParams({
    simNumber: sim,
    limit: String(limit),
  });
  return request(`/api/messages?${query}`);
}

export function getCallLogs(identity, limit = 500) {
  const sim = apiIdentity(identity);
  const query = new URLSearchParams({
    simNumber: sim,
    limit: String(limit),
  });
  return request(`/api/callLogs?${query}`);
}

export function simIdentity(sim) {
  return (
    apiIdentity(sim?.prefix) ||
    apiIdentity(sim?.simNumber) ||
    apiIdentity(sim?.deviceId) ||
    sim?.simNumber ||
    sim?.prefix ||
    sim?.deviceId ||
    ''
  );
}

function firstNonEmpty(...lists) {
  for (const list of lists) {
    if (Array.isArray(list) && list.length > 0) {
      return list;
    }
  }
  return [];
}

export function mergeSimsWithDevices(sims = [], devices = []) {
  const rows = new Map();

  const keyFor = value => apiIdentity(value);

  for (const sim of sims) {
    const key = keyFor(sim.prefix || sim.simNumber || sim.deviceId);
    if (!key) {
      continue;
    }
    rows.set(key, {
      ...sim,
      prefix: key,
      simNumber: sim.simNumber || `+91${key}`,
      messageCount: Number(sim.messageCount || 0),
      callCount: Number(sim.callCount || 0),
    });
  }

  for (const device of devices) {
    const key = keyFor(device.simNumber) || keyFor(device.deviceId);
    if (!key || key.startsWith('phone_') || key.startsWith('sim')) {
      continue;
    }
    const current = rows.get(key) || {
      simNumber: device.simNumber || `+91${key}`,
      deviceId: device.deviceId,
      prefix: key,
      messageCount: 0,
      callCount: 0,
    };
    rows.set(key, {
      ...current,
      prefix: key,
      simNumber: current.simNumber || device.simNumber || `+91${key}`,
      deviceId: current.deviceId || device.deviceId,
      messageCount: Number(current.messageCount || device.messageCount || 0),
      callCount: Number(current.callCount || device.callCount || 0),
      model: device.model || current.model,
      manufacturer: device.manufacturer || current.manufacturer,
      appVersion: device.appVersion || current.appVersion,
      lastSeen: device.updatedAt || device.registeredAt || current.lastSeen,
    });
  }

  return [...rows.values()];
}

export {firstNonEmpty};
