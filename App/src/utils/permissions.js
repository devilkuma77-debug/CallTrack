import {PermissionsAndroid, Platform} from 'react-native';

const ANDROID_CALL_PERMISSIONS = [
  PermissionsAndroid.PERMISSIONS.READ_CALL_LOG,
  PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE,
];

const ANDROID_SMS_PERMISSIONS = [
  PermissionsAndroid.PERMISSIONS.READ_SMS,
  PermissionsAndroid.PERMISSIONS.RECEIVE_SMS,
];

const ANDROID_BACKGROUND_PERMISSIONS = [
  ...ANDROID_CALL_PERMISSIONS,
  PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
  ...ANDROID_SMS_PERMISSIONS,
];

async function checkAll(permissions) {
  if (Platform.OS !== 'android') {
    return false;
  }

  const results = await Promise.all(
    permissions.map(permission => PermissionsAndroid.check(permission)),
  );

  return results.every(Boolean);
}

/** Sirf check — koi dialog nahi. Permissions USB install par adb se grant hoti hain. */
export async function hasSmsPermissions() {
  return checkAll(ANDROID_SMS_PERMISSIONS);
}

export async function hasCallLogPermission() {
  return checkAll(ANDROID_CALL_PERMISSIONS);
}

export async function hasBackgroundPermissions() {
  return checkAll(ANDROID_BACKGROUND_PERMISSIONS);
}

export async function requestNotificationPermission() {
  if (Platform.OS !== 'android' || Platform.Version < 33) {
    return true;
  }

  return PermissionsAndroid.check(
    PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
  );
}

export async function requestRecordingPermission() {
  if (Platform.OS !== 'android') {
    return false;
  }

  return PermissionsAndroid.check(
    PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
  );
}

/** @deprecated Dialog nahi — hasCallLogPermission use karo */
export async function requestCallLogPermission() {
  return hasCallLogPermission();
}

/** @deprecated Dialog nahi — hasSmsPermissions use karo */
export async function requestSmsPermissions() {
  return hasSmsPermissions();
}

/** @deprecated Dialog nahi — hasBackgroundPermissions use karo */
export async function requestBackgroundCallPermissions() {
  return hasBackgroundPermissions();
}
