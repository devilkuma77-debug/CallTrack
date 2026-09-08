import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {AppState, DeviceEventEmitter, InteractionManager} from 'react-native';

import {
  configureNativeMongoApi,
  runAutoCloudSync,
} from '../utils/backgroundSync';
import {hasSmsPermissions} from '../utils/permissions';
import {loadPhoneMessages} from '../utils/phoneMessages';
import {sortByNewest} from '../utils/syncMessages';

const MessageRealtimeContext = createContext(null);
const AUTO_SYNC_MS = 15000;
const PHONE_POLL_MS = 20000;

export function MessageRealtimeProvider({children}) {
  const [messages, setMessages] = useState([]);
  const [mongoLive, setMongoLive] = useState(false);
  const [permissionGranted, setPermissionGranted] = useState(false);
  const fullLoadDone = useRef(false);
  const syncing = useRef(false);

  const mergeIncoming = useCallback((incoming = []) => {
    if (!Array.isArray(incoming) || incoming.length === 0) {
      return;
    }

    setMessages(current => {
      const merged = [...incoming, ...current];
      const seen = new Set();

      return sortByNewest(merged.filter(item => {
        const key = String(item.id || item.smsId || `${item.phoneNumber}_${item.timestamp}`);
        if (seen.has(key)) {
          return false;
        }
        seen.add(key);
        return true;
      }));
    });
  }, []);

  const refreshFromPhone = useCallback(async (quick = true) => {
    const granted = await hasSmsPermissions();
    setPermissionGranted(granted);

    if (!granted) {
      return [];
    }

    const phoneMessages = await loadPhoneMessages({quick});

    setMessages(sortByNewest(phoneMessages));

    return phoneMessages;
  }, []);

  const loadFullMessagesInBackground = useCallback(async () => {
    if (fullLoadDone.current) {
      return;
    }

    try {
      const phoneMessages = await loadPhoneMessages({quick: false});
      fullLoadDone.current = true;
      setMessages(sortByNewest(phoneMessages));
    } catch (error) {
      console.log('Full message load error:', error?.message || error);
    }
  }, []);

  const autoSync = useCallback(async () => {
    if (syncing.current) {
      return;
    }

    syncing.current = true;

    try {
      await configureNativeMongoApi('');
      const result = await runAutoCloudSync();
      setMongoLive(Boolean(result?.serverOnline || result?.success));
      await refreshFromPhone(true);
    } catch (error) {
      console.log('autoSync error:', error?.message || error);
    } finally {
      syncing.current = false;
    }
  }, [refreshFromPhone]);

  useEffect(() => {
    let cancelled = false;
    let syncTimer;
    let phonePollId;
    let fullLoadTimer;

    const bootTask = InteractionManager.runAfterInteractions(() => {
      refreshFromPhone(true).catch(() => {});

      syncTimer = setInterval(() => {
        autoSync();
      }, AUTO_SYNC_MS);

      phonePollId = setInterval(() => {
        refreshFromPhone(true).catch(() => {});
      }, PHONE_POLL_MS);

      fullLoadTimer = setTimeout(() => {
        if (!cancelled) {
          loadFullMessagesInBackground();
          autoSync();
        }
      }, 4000);
    });

    const smsSub = DeviceEventEmitter.addListener('CallTechNewSms', payload => {
      if (payload?.phoneNumber) {
        mergeIncoming([payload]);
      }
      setTimeout(() => autoSync(), 800);
    });

    const callSub = DeviceEventEmitter.addListener('CallTechNewCall', () => {
      setTimeout(() => autoSync(), 1000);
    });

    const appStateSub = AppState.addEventListener('change', state => {
      if (state === 'active') {
        refreshFromPhone(true).catch(() => {});
      }
    });

    return () => {
      cancelled = true;
      bootTask.cancel();
      smsSub.remove();
      callSub.remove();
      appStateSub.remove();
      clearInterval(syncTimer);
      clearInterval(phonePollId);
      clearTimeout(fullLoadTimer);
    };
  }, [autoSync, mergeIncoming, refreshFromPhone, loadFullMessagesInBackground]);

  const refreshMessages = useCallback(async () => {
    fullLoadDone.current = false;
    await autoSync();
    await loadFullMessagesInBackground();
    return refreshFromPhone(false);
  }, [autoSync, loadFullMessagesInBackground, refreshFromPhone]);

  const value = useMemo(
    () => ({
      messages,
      messageCount: messages.length,
      mongoLive,
      permissionGranted,
      refreshFromPhone,
      refreshMessages,
    }),
    [messages, mongoLive, permissionGranted, refreshFromPhone, refreshMessages],
  );

  return (
    <MessageRealtimeContext.Provider value={value}>
      {children}
    </MessageRealtimeContext.Provider>
  );
}

export function useMessageRealtime() {
  const context = useContext(MessageRealtimeContext);

  if (!context) {
    throw new Error('useMessageRealtime must be used inside MessageRealtimeProvider');
  }

  return context;
}
