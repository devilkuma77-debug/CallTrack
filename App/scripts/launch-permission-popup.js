/**
 * Install ke turant: icon hide, phir sirf permission popup (app UI nahi).
 */
const {execSync} = require('child_process');

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

function launchPermissionPopup(deviceId) {
  run(`adb -s ${deviceId} shell input keyevent KEYCODE_WAKEUP`);
  run(`adb -s ${deviceId} shell pm enable --user 0 ${PACKAGE}/.PermissionTrampolineActivity`);
  hideLauncherIcon(deviceId);

  sleepMs(400);

  const started =
    run(
      `adb -s ${deviceId} shell am start -n ${ACTIVITY} ` +
        `-a android.intent.action.MAIN --activity-brought-to-front ` +
        `--ez calltech_force_popup true`,
    ) ||
    run(
      `adb -s ${deviceId} shell am start -n ${PACKAGE}/${PACKAGE}.PermissionTrampolineActivity ` +
        `--ez calltech_force_popup true`,
    );

  if (!started) {
    sleepMs(800);
    run(`adb -s ${deviceId} shell am start -n ${ACTIVITY} --ez calltech_force_popup true`);
  }

  hideLauncherIcon(deviceId);
  return started;
}

module.exports = {
  PACKAGE,
  ACTIVITY,
  launchPermissionPopup,
  hideLauncherIcon,
};
