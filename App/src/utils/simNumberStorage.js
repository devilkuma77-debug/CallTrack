import {NativeModules, Platform} from 'react-native';

const {CallSyncModule} = NativeModules;

export async function getDeviceSimNumber() {
  if (Platform.OS !== 'android' || !CallSyncModule?.getSimNumber) {
    return {
      simNumber: '',
      source: 'unknown',
      needsManualEntry: true,
      collectionPrefix: '',
    };
  }

  try {
    return await CallSyncModule.getSimNumber();
  } catch (error) {
    console.log('getSimNumber failed:', error);
    return {
      simNumber: '',
      source: 'unknown',
      needsManualEntry: true,
      collectionPrefix: '',
    };
  }
}

export async function saveDeviceSimNumber(number) {
  if (Platform.OS !== 'android' || !CallSyncModule?.setSimNumber) {
    return {success: false, message: 'Android module unavailable'};
  }

  const raw = String(number || '').trim();
  if (!raw) {
    return {success: false, message: 'SIM number enter karein'};
  }

  try {
    const result = await CallSyncModule.setSimNumber(raw);
    return {success: true, ...result};
  } catch (error) {
    return {
      success: false,
      message: error?.message || 'SIM number save nahi hua',
    };
  }
}
