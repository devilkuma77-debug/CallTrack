const LIVE_API = 'https://calltrack-e62l.onrender.com';

function normalizeApiBase(raw) {
  let base = String(raw || '').trim().replace(/\/$/, '');
  if (base.endsWith('/api')) {
    base = base.slice(0, -4);
  }
  return base;
}

function defaultApiBase() {
  if (typeof window === 'undefined') {
    return LIVE_API;
  }

  const host = window.location.hostname;
  if (host === 'localhost' || host === '127.0.0.1') {
    return '';
  }

  return LIVE_API;
}

const API_BASE = normalizeApiBase(
  import.meta.env.VITE_API_URL || defaultApiBase(),
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

async function request(path) {
  try {
    const response = await fetch(`${API_BASE}${path}`);
    const data = await response.json().catch(() => ({}));
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
