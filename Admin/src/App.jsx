import {useCallback, useEffect, useMemo, useState} from 'react';
import {
  deleteSim,
  getCallLogs,
  getHealth,
  getMessages,
  getSims,
  getStats,
  simIdentity,
} from './api';
import './App.css';

function formatTime(value) {
  const ts = Number(value);
  if (!Number.isFinite(ts) || ts <= 0) {
    return '—';
  }
  return new Date(ts).toLocaleString();
}

function formatDuration(seconds) {
  const total = Number(seconds) || 0;
  if (total <= 0) {
    return '—';
  }
  const mins = Math.floor(total / 60);
  const secs = total % 60;
  if (mins <= 0) {
    return `${secs}s`;
  }
  return `${mins}m ${secs}s`;
}

function badgeClass(type) {
  const value = String(type || '').toLowerCase();
  if (value.includes('inbox') || value.includes('incoming')) {
    return 'inbox';
  }
  if (value.includes('sent') || value.includes('outgoing')) {
    return 'outgoing';
  }
  if (value.includes('missed') || value.includes('reject') || value.includes('block')) {
    return 'missed';
  }
  return 'unknown';
}

function matchesQuery(item, query) {
  if (!query) {
    return true;
  }
  const haystack = [
    item.name,
    item.phoneNumber,
    item.body,
    item.message,
    item.type,
    item.callActionLabel,
  ]
    .join(' ')
    .toLowerCase();
  return haystack.includes(query);
}

export default function App() {
  const [healthOk, setHealthOk] = useState(false);
  const [stats, setStats] = useState({messageCount: 0, callCount: 0, sims: []});
  const [sims, setSims] = useState([]);
  const [selectedSim, setSelectedSim] = useState('');
  const [tab, setTab] = useState('messages');
  const [messages, setMessages] = useState([]);
  const [callLogs, setCallLogs] = useState([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [deletingSim, setDeletingSim] = useState('');
  const [error, setError] = useState('');

  const loadOverview = useCallback(async () => {
    const [health, statsRes, simsRes] = await Promise.all([
      getHealth().catch(() => ({ok: false})),
      getStats(),
      getSims(),
    ]);

    const simList = simsRes.sims || simsRes.devices || statsRes.sims || [];
    setHealthOk(Boolean(health.ok));
    setStats(statsRes);
    setSims(simList);
    setSelectedSim(prev => {
      const stillThere = simList.some(sim => simIdentity(sim) === prev);
      if (prev && stillThere) {
        return prev;
      }
      return simIdentity(simList[0]) || '';
    });
    setError(health.ok ? '' : health.error || statsRes.error || simsRes.error || '');
  }, []);

  const loadRecords = useCallback(async identity => {
    if (!identity) {
      setMessages([]);
      setCallLogs([]);
      return;
    }

    const [messageRes, callRes] = await Promise.all([
      getMessages(identity, 300),
      getCallLogs(identity, 300),
    ]);

    setMessages(Array.isArray(messageRes.data) ? messageRes.data : []);
    setCallLogs(Array.isArray(callRes.data) ? callRes.data : []);
  }, []);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      await loadOverview();
      if (selectedSim) {
        await loadRecords(selectedSim);
      }
    } catch (err) {
      setError(err.message || 'Data load nahi ho paya');
      setHealthOk(false);
    } finally {
      setLoading(false);
    }
  }, [loadOverview, loadRecords, selectedSim]);

  useEffect(() => {
    refresh();
    const timer = setInterval(() => {
      loadOverview().catch(() => setHealthOk(false));
      if (selectedSim) {
        loadRecords(selectedSim).catch(() => {});
      }
    }, 5000);
    return () => clearInterval(timer);
  }, [refresh, loadOverview, loadRecords, selectedSim]);

  useEffect(() => {
    if (!selectedSim) {
      return undefined;
    }

    let cancelled = false;
    setLoading(true);
    loadRecords(selectedSim)
      .catch(err => {
        if (!cancelled) {
          setError(err.message || 'Records load nahi ho paye');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [selectedSim, loadRecords]);

  const filteredMessages = useMemo(
    () => messages.filter(item => matchesQuery(item, query.trim().toLowerCase())),
    [messages, query],
  );

  const filteredCalls = useMemo(
    () => callLogs.filter(item => matchesQuery(item, query.trim().toLowerCase())),
    [callLogs, query],
  );

  const handleDeleteSim = useCallback(async identity => {
    if (!identity) {
      return;
    }

    const confirmed = window.confirm(
      `Delete SIM ${identity}?\nIske saare SMS aur call logs MongoDB se hat jayenge.`,
    );
    if (!confirmed) {
      return;
    }

    setDeletingSim(identity);
    setError('');
    try {
      const result = await deleteSim(identity);
      if (!result.success) {
        setError(result.error || 'SIM delete nahi ho payi');
        return;
      }

      await loadOverview();
    } catch (err) {
      setError(err.message || 'SIM delete nahi ho payi');
    } finally {
      setDeletingSim('');
    }
  }, [loadOverview]);

  const selectedStats = sims.find(sim => simIdentity(sim) === selectedSim);

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <div className="logo">C</div>
          <div>
            <h1>CallTech Admin</h1>
            <p>Phone se synced calls aur SMS</p>
          </div>
        </div>
        <div className="topbar-actions">
          <div className="health">
            <span className={`dot ${healthOk ? 'ok' : 'bad'}`} />
            {healthOk ? 'Server online' : 'Server offline'}
          </div>
          <button className="refresh-btn" onClick={refresh} type="button">
            Refresh
          </button>
        </div>
      </header>

      <div className="layout">
        <aside className="sidebar">
          <h2>SIMs / Devices</h2>
          <div className="sim-list">
            {sims.length === 0 && (
              <p className="empty">Koi SIM synced nahi hai</p>
            )}
            {sims.map(sim => {
              const identity = simIdentity(sim);
              return (
                <div
                  key={identity}
                  className={`sim-card ${identity === selectedSim ? 'active' : ''}`}>
                  <button
                    className="sim-card-main"
                    onClick={() => setSelectedSim(identity)}
                    type="button">
                    <span className="sim-number">{sim.simNumber || identity}</span>
                    <span className="sim-meta">
                      {sim.messageCount || 0} SMS · {sim.callCount || 0} calls
                    </span>
                  </button>
                  <button
                    className="sim-delete"
                    disabled={deletingSim === identity}
                    onClick={() => handleDeleteSim(identity)}
                    type="button">
                    {deletingSim === identity ? '...' : 'Delete'}
                  </button>
                </div>
              );
            })}
          </div>
        </aside>

        <main className="main">
          <section className="stats">
            <article className="stat-card">
              <span>Total SMS</span>
              <strong>{stats.messageCount || 0}</strong>
            </article>
            <article className="stat-card">
              <span>Total Calls</span>
              <strong>{stats.callCount || 0}</strong>
            </article>
            <article className="stat-card">
              <span>Selected SIM</span>
              <strong>
                {selectedStats
                  ? `${selectedStats.messageCount || 0} / ${selectedStats.callCount || 0}`
                  : '—'}
              </strong>
            </article>
          </section>

          <div className="toolbar">
            <div className="tabs">
              <button
                className={`tab ${tab === 'messages' ? 'active' : ''}`}
                onClick={() => setTab('messages')}
                type="button">
                Messages ({filteredMessages.length})
              </button>
              <button
                className={`tab ${tab === 'calls' ? 'active' : ''}`}
                onClick={() => setTab('calls')}
                type="button">
                Call logs ({filteredCalls.length})
              </button>
            </div>
            <input
              className="search"
              value={query}
              onChange={event => setQuery(event.target.value)}
              placeholder="Search name, number, message..."
            />
          </div>

          <section className="panel">
            {error && <div className="status error">{error}</div>}
            {!error && loading && <div className="status">Loading...</div>}
            {!error && !loading && tab === 'messages' && (
              <div className="table-wrap">
                {filteredMessages.length === 0 ? (
                  <div className="empty">Is SIM pe koi SMS nahi mila</div>
                ) : (
                  <table>
                    <thead>
                      <tr>
                        <th>From / To</th>
                        <th>Type</th>
                        <th>Message</th>
                        <th>Time</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredMessages.map(item => (
                        <tr key={item.id || `${item.phoneNumber}_${item.timestamp}`}>
                          <td>
                            <div className="name">{item.name || 'Unknown'}</div>
                            <div className="phone">{item.phoneNumber}</div>
                          </td>
                          <td>
                            <span className={`badge ${badgeClass(item.type)}`}>
                              {item.type || 'UNKNOWN'}
                            </span>
                          </td>
                          <td className="body-cell">{item.body || item.message || '—'}</td>
                          <td>{formatTime(item.timestamp)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            )}

            {!error && !loading && tab === 'calls' && (
              <div className="table-wrap">
                {filteredCalls.length === 0 ? (
                  <div className="empty">Is SIM pe koi call log nahi mila</div>
                ) : (
                  <table>
                    <thead>
                      <tr>
                        <th>Contact</th>
                        <th>Type</th>
                        <th>Duration</th>
                        <th>Time</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredCalls.map(item => (
                        <tr key={item.id || `${item.phoneNumber}_${item.timestamp}`}>
                          <td>
                            <div className="name">{item.name || 'Unknown'}</div>
                            <div className="phone">{item.phoneNumber}</div>
                          </td>
                          <td>
                            <span className={`badge ${badgeClass(item.type)}`}>
                              {item.callActionLabel || item.type || 'UNKNOWN'}
                            </span>
                          </td>
                          <td>
                            {item.durationFormatted || formatDuration(item.duration)}
                          </td>
                          <td>{formatTime(item.timestamp)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            )}
          </section>
        </main>
      </div>
    </div>
  );
}
