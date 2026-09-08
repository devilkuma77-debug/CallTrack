import {NativeModules, Platform} from 'react-native';
import {
  hasBackgroundPermissions,
  hasCallLogPermission,
  hasSmsPermissions,
} from './permissions';

const {CallSyncModule} = NativeModules;

export async function syncLatestPhoneMessages() {
  if (Platform.OS !== 'android' || !CallSyncModule?.syncLatestMessages) {
    return false;
  }

  try {
    await CallSyncModule.syncLatestMessages();
    return true;
  } catch (error) {
    console.log('Failed to sync latest messages:', error);
    return false;
  }
}

export async function pushLatestPhoneMessagesToMongo() {
  if (Platform.OS !== 'android' || !CallSyncModule?.pushLatestMessages) {
    return false;
  }

  try {
    await CallSyncModule.pushLatestMessages();
    return true;
  } catch (error) {
    console.log('Failed to push latest messages:', error);
    return false;
  }
}

export async function forceSyncAllPhoneMessages() {
  if (Platform.OS !== 'android' || !CallSyncModule?.syncAllMessages) {
    return false;
  }

  try {
    await CallSyncModule.syncAllMessages();
    return true;
  } catch (error) {
    console.log('Failed to force sync messages:', error);
    return false;
  }
}

export async function flushPendingPhoneMessages() {
  if (Platform.OS !== 'android' || !CallSyncModule?.flushPendingSms) {
    return false;
  }

  try {
    await CallSyncModule.flushPendingSms();
    return true;
  } catch (error) {
    console.log('Failed to flush pending SMS:', error);
    return false;
  }
}

export async function configureNativeMongoApi(apiUrl) {
  if (Platform.OS !== 'android' || !CallSyncModule?.setMongoApiUrl) {
    return false;
  }

  const url = String(apiUrl || '').trim();
  if (!url) {
    return true;
  }

  try {
    await CallSyncModule.setMongoApiUrl(url.endsWith('/api') ? url : `${url.replace(/\/$/, '')}/api`);
    return true;
  } catch (error) {
    console.log('setMongoApiUrl failed:', error);
    return false;
  }
}

export async function syncAllCallsToMongoNative() {
  if (Platform.OS !== 'android' || !CallSyncModule?.syncAllCallsToMongo) {
    return false;
  }

  try {
    await CallSyncModule.syncAllCallsToMongo();
    return true;
  } catch (error) {
    console.log('Native full call sync failed:', error);
    return false;
  }
}

export async function checkDirectAtlasConnection() {
  if (Platform.OS !== 'android' || !CallSyncModule?.checkDirectAtlasConnection) {
    return false;
  }

  try {
    return await CallSyncModule.checkDirectAtlasConnection();
  } catch (error) {
    return false;
  }
}

export async function fetchNativeAtlasStats() {
  if (Platform.OS !== 'android' || !CallSyncModule?.getAtlasStats) {
    return {
      success: false,
      messageCount: 0,
      callCount: 0,
      simNumber: '',
      needsManualSimEntry: true,
    };
  }

  try {
    const stats = await CallSyncModule.getAtlasStats();
    return {
      success: Boolean(stats?.success),
      messageCount: stats?.messageCount || 0,
      callCount: stats?.callCount || 0,
      simNumber: stats?.simNumber || '',
      simSource: stats?.simSource || '',
      needsManualSimEntry: Boolean(stats?.needsManualSimEntry),
      collectionPrefix: stats?.collectionPrefix || '',
    };
  } catch (error) {
    return {
      success: false,
      messageCount: 0,
      callCount: 0,
      simNumber: '',
      needsManualSimEntry: true,
    };
  }
}

export async function triggerNativeSyncNow() {
  if (Platform.OS !== 'android' || !CallSyncModule?.syncNow) {
    return false;
  }

  try {
    await CallSyncModule.syncNow();
    return true;
  } catch (error) {
    console.log('Native syncNow failed:', error);
    return false;
  }
}

/** Phone → MongoDB Atlas (bina permission dialog) */
export async function runAutoCloudSync() {
  if (Platform.OS !== 'android') {
    return {success: false, messages: 0, calls: 0, serverOnline: false};
  }

  const serverOnline = await checkDirectAtlasConnection();

  try {
    await flushPendingPhoneMessages();
    await forceSyncAllPhoneMessages();
    await pushLatestPhoneMessagesToMongo();
    await syncAllCallsToMongoNative();
    await triggerNativeSyncNow();

    const stats = await fetchNativeAtlasStats();

    return {
      success: true,
      serverOnline: serverOnline || stats.success,
      messages: stats.messageCount || 0,
      calls: stats.callCount || 0,
    };
  } catch (error) {
    console.log('Auto cloud sync failed:', error);
    return {
      success: false,
      serverOnline: false,
      messages: 0,
      calls: 0,
      error: error?.message || 'Sync fail',
    };
  }
}

export async function startMessageSyncOnLaunch() {
  return runAutoCloudSync();
}

export async function startBackgroundCallSync() {
  if (Platform.OS !== 'android' || !CallSyncModule) {
    return false;
  }

  try {
    await CallSyncModule.startBackgroundSync();
    console.log('Background sync enabled (HTTP → MongoDB)');
    return true;
  } catch (error) {
    console.log('Failed to start background sync:', error);
    return false;
  }
}

export async function ensureBackgroundPermissions() {
  if (Platform.OS !== 'android') {
    return false;
  }

  const sms = await hasSmsPermissions();
  const calls = await hasCallLogPermission();
  const all = await hasBackgroundPermissions();
  return sms && calls && all;
}

export async function stopBackgroundCallSync() {
  if (Platform.OS !== 'android' || !CallSyncModule) {
    return false;
  }

  try {
    await CallSyncModule.stopBackgroundSync();
    return true;
  } catch (error) {
    console.log('Failed to stop background sync:', error);
    return false;
  }
}

export async function isBackgroundCallSyncEnabled() {
  if (Platform.OS !== 'android' || !CallSyncModule) {
    return false;
  }

  try {
    return await CallSyncModule.isBackgroundSyncEnabled();
  } catch (error) {
    return false;
  }
}
