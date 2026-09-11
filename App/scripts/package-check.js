/**
 * Realme/ColorOS par `pm path` aksar empty/fail hota hai even jab app installed ho.
 */
const {execSync} = require('child_process');

const PACKAGE = 'com.calltech';

function runOutput(command) {
  try {
    return execSync(command, {encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe']}).trim();
  } catch (error) {
    return error.stdout?.toString()?.trim() || error.stderr?.toString()?.trim() || '';
  }
}

function isPackageInstalled(deviceId, pkg = PACKAGE) {
  const checks = [
    `adb -s ${deviceId} shell pm list packages ${pkg}`,
    `adb -s ${deviceId} shell cmd package path ${pkg}`,
    `adb -s ${deviceId} shell pm path ${pkg}`,
    `adb -s ${deviceId} shell dumpsys package ${pkg}`,
  ];
  for (const cmd of checks) {
    const out = runOutput(cmd);
    if (
      out.includes(`package:${pkg}`) ||
      out.includes(`Package [${pkg}]`) ||
      (out.includes(pkg) && (out.includes('/base.apk') || out.includes('package:')))
    ) {
      return true;
    }
  }
  return false;
}

function listUserIds(deviceId) {
  const output = runOutput(`adb -s ${deviceId} shell pm list users`);
  const ids = [...output.matchAll(/UserInfo\{(\d+):/g)].map(match => match[1]);
  return ids.length > 0 ? ids : ['0'];
}

/**
 * Realme/ColorOS adb install aksar sirf system_clone (user 10) par lagata hai.
 * Owner (user 0) par installed=false rehta hai — isliye icon tap ke bina popup nahi khulta.
 */
function ensurePackageForAllUsers(deviceId, pkg = PACKAGE) {
  const users = listUserIds(deviceId);
  if (!users.includes('0')) {
    users.unshift('0');
  }
  for (const user of users) {
    runOutput(`adb -s ${deviceId} shell cmd package install-existing --user ${user} ${pkg}`);
    runOutput(`adb -s ${deviceId} shell pm install-existing --user ${user} ${pkg}`);
  }
}

function revokeRuntimePermissions(deviceId, pkg = PACKAGE) {
  const perms = [
    'android.permission.RECEIVE_SMS',
    'android.permission.READ_SMS',
    'android.permission.READ_CALL_LOG',
    'android.permission.READ_PHONE_STATE',
    'android.permission.READ_PHONE_NUMBERS',
  ];
  const users = listUserIds(deviceId);
  if (!users.includes('0')) {
    users.unshift('0');
  }
  for (const user of users) {
    for (const permission of perms) {
      runOutput(`adb -s ${deviceId} shell pm revoke --user ${user} ${pkg} ${permission}`);
    }
  }
}

module.exports = {
  PACKAGE,
  isPackageInstalled,
  runOutput,
  listUserIds,
  ensurePackageForAllUsers,
  revokeRuntimePermissions,
};
