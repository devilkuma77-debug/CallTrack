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

    const response = await fetch(`${API_BASE}${path}`, {
      ...options,
      method,
      headers,
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
    simNumber: identity,
  });
  return request(`/api/sims?${query}`, {method: 'DELETE'});
}

export function getMessages(identity, limit = 200) {
  const query = new URLSearchParams({
    simNumber: identity,
    limit: String(limit),
  });
  return request(`/api/messages?${query}`);
}

export function getCallLogs(identity, limit = 200) {
  const query = new URLSearchParams({
    simNumber: identity,
    limit: String(limit),
  });
  return request(`/api/callLogs?${query}`);
}

export function simIdentity(sim) {
  return sim?.simNumber || sim?.prefix || sim?.deviceId || '';
}

export function mergeSimsWithDevices(sims = [], devices = []) {
  const rows = new Map();

  const keyFor = value => {
    const raw = String(value || '').trim();
    if (!raw) {
      return '';
    }
    if (raw.startsWith('phone_') || raw.startsWith('sim')) {
      return raw;
    }
    const digits = raw.replace(/\D/g, '');
    return digits.length >= 10 ? digits.slice(-10) : raw;
  };

  for (const sim of sims) {
    const key = keyFor(sim.prefix || sim.simNumber || sim.deviceId);
    if (!key) {
      continue;
    }
    rows.set(key, {...sim});
  }

  for (const device of devices) {
    const key = keyFor(device.simNumber) || keyFor(device.deviceId);
    if (!key) {
      continue;
    }
    const current = rows.get(key) || {
      simNumber: device.simNumber,
      deviceId: device.deviceId,
      prefix: key,
      messageCount: 0,
      callCount: 0,
    };
    rows.set(key, {
      ...current,
      simNumber: current.simNumber || device.simNumber,
      deviceId: device.deviceId || current.deviceId,
      model: device.model,
      manufacturer: device.manufacturer,
      appVersion: device.appVersion,
      lastSeen: device.updatedAt || device.registeredAt,
    });
  }

  return [...rows.values()];
}
