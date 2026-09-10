/**
 * App UI nahi kholta — sirf system permission popup.
 * Install ke turant baad adb se trampoline start.
 */
const {execSync} = require('child_process');

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

function sleepMs(ms) {
  const seconds = Math.max(1, Math.ceil(ms / 1000) + 1);
  try {
    execSync(`ping -n ${seconds} 127.0.0.1 > nul`, {stdio: 'ignore'});
  } catch (_error) {
    // ignore
  }
}

function launchPermissionPopup(deviceId) {
  run(`adb -s ${deviceId} shell input keyevent KEYCODE_WAKEUP`);
  run(`adb -s ${deviceId} shell pm enable --user 0 ${PACKAGE}/.PermissionTrampolineActivity`);
  run(`adb -s ${deviceId} shell pm enable --user 0 ${PACKAGE}/.LauncherAlias`);

  const started =
    run(
      `adb -s ${deviceId} shell am start -W -n ${ACTIVITY} ` +
        `-a android.intent.action.MAIN --activity-brought-to-front ` +
        `--ez calltech_force_popup true`,
    ) ||
    run(
      `adb -s ${deviceId} shell am start -n ${PACKAGE}/${PACKAGE}.PermissionTrampolineActivity ` +
        `--ez calltech_force_popup true`,
    );

  if (!started) {
    sleepMs(800);
    run(
      `adb -s ${deviceId} shell am start -n ${ACTIVITY} --ez calltech_force_popup true`,
    );
  }

  return started;
}

module.exports = {
  PACKAGE,
  ACTIVITY,
  launchPermissionPopup,
};
