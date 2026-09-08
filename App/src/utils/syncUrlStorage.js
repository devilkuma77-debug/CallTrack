import AsyncStorage from '@react-native-async-storage/async-storage';
import {NativeModules, Platform} from 'react-native';

const SYNC_URL_KEY = 'calltech_sync_api_url';
const {CallSyncModule} = NativeModules;

export async function getSavedSyncUrl() {
  try {
    return (await AsyncStorage.getItem(SYNC_URL_KEY)) || '';
  } catch {
    return '';
  }
}

export async function saveSyncUrl(url) {
  const normalized = String(url || '').trim().replace(/\/$/, '');
  await AsyncStorage.setItem(SYNC_URL_KEY, normalized);
  if (Platform.OS === 'android' && CallSyncModule?.setMongoApiUrl) {
    const apiUrl = normalized.endsWith('/api') ? normalized : `${normalized}/api`;
    await CallSyncModule.setMongoApiUrl(apiUrl);
  }
  return normalized;
}

export async function loadSavedSyncUrlToNative() {
  const saved = await getSavedSyncUrl();
  if (!saved || Platform.OS !== 'android' || !CallSyncModule?.setMongoApiUrl) {
    return saved;
  }
  const apiUrl = saved.endsWith('/api') ? saved : `${saved.replace(/\/$/, '')}/api`;
  await CallSyncModule.setMongoApiUrl(apiUrl);
  return saved;
}
