/**
 * Redmi/MIUI par INSTALL_FAILED_USER_RESTRICTED fix + manual install helper.
 */
const {execSync, spawnSync} = require('child_process');
const fs = require('fs');
const path = require('path');
const {listAdbDevices, printNoDeviceHelp} = require('./project-paths');
const {launchPermissionPopup} = require('./launch-permission-popup');

const PACKAGE = 'com.calltech';
const BUILD_TYPE = (process.env.CALLTECH_BUILD_TYPE || 'release').toLowerCase();
const APK_NAME = BUILD_TYPE === 'release' ? 'app-release.apk' : 'app-debug.apk';
const APK_PATH = path.join(
  __dirname,
  '..',
  'android',
  'app',
  'build',
  'outputs',
  'apk',
  BUILD_TYPE,
  APK_NAME,
);

function assembleApk() {
  const androidDir = path.join(__dirname, '..', 'android');
  const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
  const task = BUILD_TYPE === 'release' ? 'assembleRelease' : 'assembleDebug';
  const env = {...process.env};
  if (fs.existsSync('D:\\g')) {
    env.GRADLE_USER_HOME = 'D:\\g';
  }

  console.log(`Building ${BUILD_TYPE} APK (${task})...`);
  execSync(`${gradlew} ${task}`, {
    cwd: androidDir,
    stdio: 'inherit',
    env,
    shell: true,
  });
}

function runOutput(command) {
  try {
    return execSync(command, {encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe']}).trim();
  } catch (error) {
    return error.stdout?.toString()?.trim() || error.stderr?.toString()?.trim() || '';
  }
}

function listDevices() {
  return listAdbDevices();
}

function isInstalled(deviceId) {
  return runOutput(`adb -s ${deviceId} shell pm path ${PACKAGE}`).includes('package:');
}

function apkOnPhone(deviceId) {
  const remoteName = BUILD_TYPE === 'release' ? 'calltech-app-release.apk' : 'calltech-app-debug.apk';
  const out = runOutput(
    `adb -s ${deviceId} shell ls /sdcard/Download/${remoteName}`,
  );
  return out.includes(remoteName);
}

function openDownloadsFolder(deviceId) {
  console.log('  Phone par Downloads folder khol raha hoon...');
  console.log('  → calltech-app-debug.apk par tap karo → Install');
  execSync(
    `adb -s ${deviceId} shell am start -a android.intent.action.VIEW -d "content://com.android.externalstorage.documents/document/primary%3ADownload" -t "vnd.android.document/directory"`,
    {stdio: 'inherit'},
  );
}

function installFromTmp(deviceId) {
  const remote = `/data/local/tmp/${APK_NAME}`;
  console.log('Trying install via /data/local/tmp/ (MIUI workaround)...');
  execSync(`adb -s ${deviceId} push "${APK_PATH}" "${remote}"`, {stdio: 'inherit'});
  const result = spawnSync(
    'adb',
    ['-s', deviceId, 'shell', 'pm', 'install', '-r', remote],
    {encoding: 'utf8'},
  );
  const output = `${result.stdout || ''}${result.stderr || ''}`.trim();
  if (output) {
    console.log(output);
  }
  return result.status === 0 && isInstalled(deviceId);
}

function getManufacturer(deviceId) {
  return runOutput(`adb -s ${deviceId} shell getprop ro.product.manufacturer`).toLowerCase();
}

function enableUsbInstallHints(deviceId) {
  const manufacturer = getManufacturer(deviceId);
  const cmds = [
    `adb -s ${deviceId} shell settings put global verifier_verify_adb_installs 0`,
    `adb -s ${deviceId} shell settings put global package_verifier_enable 0`,
    `adb -s ${deviceId} shell settings put secure install_non_market_apps 1`,
  ];

  if (
    manufacturer.includes('xiaomi') ||
    manufacturer.includes('redmi') ||
    manufacturer.includes('realme') ||
    manufacturer.includes('oppo') ||
    manufacturer.includes('oneplus')
  ) {
    cmds.push(`adb -s ${deviceId} shell settings put global adb_install_need_confirm 0`);
  }

  cmds.forEach(cmd => {
    try {
      execSync(cmd, {stdio: 'ignore'});
    } catch (_error) {
      // ignore — phone setting still required
    }
  });
}

function printMiuiFix() {
  console.log(`
╔══════════════════════════════════════════════════════════════╗
║  INSTALL FAILED — Redmi / MIUI fix (phone par karo)         ║
╚══════════════════════════════════════════════════════════════╝

1) Settings → About phone → MIUI version (7 baar tap)
2) Settings → Additional settings → Developer options
3) Ye sab ON karo:
   • USB debugging
   • USB debugging (Security settings)   ← bahut zaruri
   • Install via USB                     ← bahut zaruri
4) Mi Account se sign-in karo (agar pooche)
5) PC se dubara install chalao — phone screen dekho:
   • "Allow USB install?" → ALLOW dabao (Cancel mat)

Agar phir bhi fail:
6) Developer options → MIUI optimization → OFF → phone restart
7) Ya manual install:
   npm run android:push-apk
   Phone → Files → Download → app-debug.apk → Install

Phir permissions:
   npm run android:grant
`);
}

function installApk(deviceId, attempt = 1) {
  if (!fs.existsSync(APK_PATH) || process.env.CALLTECH_SKIP_BUILD !== '1') {
    assembleApk();
  }

  if (!fs.existsSync(APK_PATH)) {
    console.error(`APK not found: ${APK_PATH}`);
    console.error(`Pehle build karo: cd android && .\\gradlew.bat assemble${BUILD_TYPE === 'release' ? 'Release' : 'Debug'}`);
    process.exit(1);
  }

  const model = runOutput(`adb -s ${deviceId} shell getprop ro.product.model`) || deviceId;
  const manufacturer = getManufacturer(deviceId) || 'unknown';
  console.log(`\nInstalling on ${deviceId} (${manufacturer} ${model})${attempt > 1 ? ` [retry ${attempt}]` : ''}...`);

  enableUsbInstallHints(deviceId);

  if (attempt === 1 && (manufacturer.includes('xiaomi') || manufacturer.includes('redmi'))) {
    console.log('Phone screen dekho — "Allow USB install?" aaye to ALLOW dabao.');
    try {
      execSync(
        `adb -s ${deviceId} shell am start -a android.settings.APPLICATION_DEVELOPMENT_SETTINGS`,
        {stdio: 'ignore'},
      );
    } catch (_error) {
      // ignore
    }
  }

  const result = spawnSync(
    'adb',
    ['-s', deviceId, 'install', '-r', '-t', APK_PATH],
    {encoding: 'utf8'},
  );

  const output = `${result.stdout || ''}${result.stderr || ''}`.trim();
  if (output) {
    console.log(output);
  }

  if (result.status === 0 && isInstalled(deviceId)) {
    console.log('\n[OK] CallTech installed successfully.');
    console.log('  Phone par sirf permission popup aayega — app nahi khulegi. Allow dabao.');
    launchPermissionPopup(deviceId);
    return true;
  }

  if (installFromTmp(deviceId)) {
    console.log('\n[OK] CallTech installed via /data/local/tmp/.');
    console.log('  Phone par sirf permission popup aayega — app nahi khulegi. Allow dabao.');
    launchPermissionPopup(deviceId);
    return true;
  }

  if (
    attempt < 3 &&
    (output.includes('USER_RESTRICTED') || output.includes('Install canceled'))
  ) {
    console.log('\nInstall block — 15 sec wait (phone par ALLOW / settings ON karo)...');
    execSync('ping -n 16 127.0.0.1 > nul', {stdio: 'ignore'});
    return installApk(deviceId, attempt + 1);
  }

  if (output.includes('USER_RESTRICTED') || output.includes('Install canceled')) {
    printMiuiFix();
  } else {
    console.log('\n[FAIL] Install failed. Phone screen par koi popup ho to Allow dabao.');
  }

  return false;
}

function pushApk(deviceId) {
  if (!fs.existsSync(APK_PATH) || process.env.CALLTECH_SKIP_BUILD !== '1') {
    assembleApk();
  }

  if (!fs.existsSync(APK_PATH)) {
    console.error(`APK not found: ${APK_PATH}`);
    process.exit(1);
  }

  const remoteName = BUILD_TYPE === 'release' ? 'calltech-app-release.apk' : 'calltech-app-debug.apk';
  const remote = `/sdcard/Download/${remoteName}`;
  console.log(`Pushing APK to phone: ${remote}`);
  execSync(`adb -s ${deviceId} push "${APK_PATH}" "${remote}"`, {stdio: 'inherit'});
  console.log('\n[OK] APK phone par copy ho gaya.');
  console.log('Ab phone par: Files → Download → calltech-app-debug.apk → Install');
}

const mode = process.argv[2] || 'install';
const devices = listDevices();

if (devices.length === 0) {
  printNoDeviceHelp();
  process.exit(1);
}

if (mode === 'push') {
  devices.forEach(pushApk);
  process.exit(0);
}

if (mode === 'open') {
  devices.forEach(deviceId => {
    if (!apkOnPhone(deviceId)) {
      console.log('APK nahi mila. Pehle: npm run android:push-apk');
      process.exit(1);
    }
    console.log(`Opening Downloads on ${deviceId} — calltech-app-debug.apk par tap karo → Install`);
    openDownloadsFolder(deviceId);
  });
  process.exit(0);
}

if (mode === 'launch') {
  devices.forEach(deviceId => {
    if (!isInstalled(deviceId)) {
      console.log('App installed nahi. npm run android:open-apk');
      process.exit(1);
    }
    console.log(`Permission popup khol raha hoon on ${deviceId} (app UI nahi)...`);
    launchPermissionPopup(deviceId);
  });
  process.exit(0);
}

let ok = true;
devices.forEach(deviceId => {
  if (!installApk(deviceId)) {
    ok = false;
  }
});

if (ok) {
  console.log('\nPhone screen dekho — permission popup Allow karo. App kholne ki zaroorat nahi.');
  process.exit(0);
}

process.exit(1);
