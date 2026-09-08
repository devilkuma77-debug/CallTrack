const API_BASE = import.meta.env.VITE_API_URL || 'https://calltrack-e62l.onrender.com/api';

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
