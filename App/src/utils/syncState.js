import AsyncStorage from '@react-native-async-storage/async-storage';

const KEYS = {
  lastCallSync: '@calltech/lastCallSyncTs',
  lastMessageSync: '@calltech/lastMessageSyncTs',
};

export async function getLastCallSyncTimestamp() {
  try {
    const value = await AsyncStorage.getItem(KEYS.lastCallSync);
    return value ? Number(value) : 0;
  } catch {
    return 0;
  }
}

export async function setLastCallSyncTimestamp(timestamp) {
  if (!timestamp || timestamp <= 0) {
    return;
  }

  try {
    await AsyncStorage.setItem(KEYS.lastCallSync, String(timestamp));
  } catch (error) {
    console.log('setLastCallSyncTimestamp error:', error);
  }
}

export async function getLastMessageSyncTimestamp() {
  try {
    const value = await AsyncStorage.getItem(KEYS.lastMessageSync);
    return value ? Number(value) : 0;
  } catch {
    return 0;
  }
}

export async function setLastMessageSyncTimestamp(timestamp) {
  if (!timestamp || timestamp <= 0) {
    return;
  }

  try {
    await AsyncStorage.setItem(KEYS.lastMessageSync, String(timestamp));
  } catch (error) {
    console.log('setLastMessageSyncTimestamp error:', error);
  }
}
