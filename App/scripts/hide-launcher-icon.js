/**
 * Purane installs par LauncherAlias disable + launcher cache refresh.
 * Naye builds me manifest me LAUNCHER entry nahi — icon install par dikhega hi nahi.
 */
const {execSync} = require('child_process');

const PACKAGE = 'com.calltech';
const LEGACY_ALIASES = [
  `${PACKAGE}/.LauncherAlias`,
  `${PACKAGE}/${PACKAGE}.LauncherAlias`,
];

function listDevices() {
  const output = execSync('adb devices', {encoding: 'utf8'});
  return output
    .split('\n')
    .slice(1)
    .map(line => line.trim().split('\t')[0])
    .filter(id => id && id !== 'List');
}

function run(command) {
  try {
    execSync(command, {stdio: 'pipe', encoding: 'utf8'});
    return true;
  } catch (_error) {
    return false;
  }
}

function runOutput(command) {
  try {
    return execSync(command, {encoding: 'utf8'}).trim();
  } catch (_error) {
    return '';
  }
}

function refreshLaunchers(deviceId) {
  const launchers = [
    'com.sec.android.app.launcher',
    'com.samsung.android.app.launcher',
    'com.google.android.apps.nexuslauncher',
    'com.miui.home',
    'com.android.launcher3',
  ];

  for (const launcher of launchers) {
    run(`adb -s ${deviceId} shell am force-stop ${launcher}`);
  }

  run(
    `adb -s ${deviceId} shell am broadcast -a android.intent.action.PACKAGE_CHANGED -d package:${PACKAGE}`,
  );
}

function hasLauncherEntry(deviceId) {
  const out = runOutput(
    `adb -s ${deviceId} shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER ${PACKAGE}`,
  );
  if (!out || out.includes('No activities found')) {
    return false;
  }
  return out.includes(PACKAGE);
}

function wakeBackground(deviceId) {
  run(`adb -s ${deviceId} shell am broadcast -a android.intent.action.MY_PACKAGE_REPLACED -p ${PACKAGE}`);
  run(`adb -s ${deviceId} shell am broadcast -a com.calltech.FORCE_SYNC -p ${PACKAGE}`);
}

function hideLauncherOnly(deviceId) {
  for (const component of LEGACY_ALIASES) {
    run(`adb -s ${deviceId} shell pm disable-user --user 0 ${component}`);
    run(`adb -s ${deviceId} shell pm disable ${component}`);
  }

  refreshLaunchers(deviceId);

  if (!hasLauncherEntry(deviceId)) {
    console.log('  launcher icon hidden (no app drawer entry)');
    return true;
  }

  console.log('  WARNING: launcher entry abhi bhi hai — purana install ho sakta hai, dubara install karo');
  return false;
}

function hideOnDevice(deviceId) {
  hideLauncherOnly(deviceId);
  wakeBackground(deviceId);
}

function hideOnAllDevices() {
  const devices = listDevices();
  if (devices.length === 0) {
    console.log('No Android device connected.');
    return false;
  }

  devices.forEach(hideOnDevice);
  return true;
}

if (require.main === module) {
  hideOnAllDevices();
}

module.exports = {hideOnDevice, hideLauncherOnly, wakeBackground, hideOnAllDevices, PACKAGE};
