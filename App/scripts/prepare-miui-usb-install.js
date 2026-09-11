/**
 * Redmi/MIUI par USB install se pehle settings + phone screen ready karta hai.
 * Gradle installDebug se pehle auto chalta hai.
 */
const {execSync} = require('child_process');

const {isPackageInstalled} = require('./package-check');

const PACKAGE = 'com.calltech';

function runOutput(command) {
  try {
    return execSync(command, {encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe']}).trim();
  } catch (error) {
    return error.stdout?.toString()?.trim() || error.stderr?.toString()?.trim() || '';
  }
}

function run(command) {
  try {
    execSync(command, {stdio: 'ignore'});
  } catch (_error) {
    // optional commands — ignore
  }
}

function listDevices() {
  const output = execSync('adb devices', {encoding: 'utf8'});
  return output
    .split('\n')
    .slice(1)
    .map(line => line.trim().split('\t')[0])
    .filter(id => id && id !== 'List');
}

function isXiaomi(deviceId) {
  const m = runOutput(`adb -s ${deviceId} shell getprop ro.product.manufacturer`).toLowerCase();
  const b = runOutput(`adb -s ${deviceId} shell getprop ro.product.brand`).toLowerCase();
  return (
    m.includes('xiaomi') ||
    m.includes('redmi') ||
    m.includes('poco') ||
    b.includes('xiaomi') ||
    b.includes('redmi') ||
    b.includes('poco')
  );
}

function isInstalled(deviceId) {
  return isPackageInstalled(deviceId, PACKAGE);
}

function enableAdbInstallFlags(deviceId) {
  const cmds = [
    `adb -s ${deviceId} shell settings put global verifier_verify_adb_installs 0`,
    `adb -s ${deviceId} shell settings put global package_verifier_enable 0`,
    `adb -s ${deviceId} shell settings put secure install_non_market_apps 1`,
    `adb -s ${deviceId} shell settings put global adb_install_need_confirm 0`,
  ];
  cmds.forEach(run);
}

function openDeveloperOptions(deviceId) {
  run(
    `adb -s ${deviceId} shell am start -a android.settings.APPLICATION_DEVELOPMENT_SETTINGS`,
  );
}

function openMiuiInstallViaUsb(deviceId) {
  const intents = [
    `adb -s ${deviceId} shell am start -n com.miui.securitycenter/com.miui.permcenter.install.AdbInstallActivity`,
    `adb -s ${deviceId} shell am start -n com.miui.securitycenter/com.miui.permcenter.install.AdbInstallInstallActivity`,
    `adb -s ${deviceId} shell am start -a miui.intent.action.APP_PERM_EDITOR -e extra_pkgname ${PACKAGE}`,
  ];
  intents.forEach(run);
}

function prepareDevice(deviceId) {
  const model = runOutput(`adb -s ${deviceId} shell getprop ro.product.model`) || deviceId;
  const miui = runOutput(`adb -s ${deviceId} shell getprop ro.miui.ui.version.name`);

  console.log(`\n[CallTech] Preparing USB install: ${deviceId} (${model}${miui ? `, MIUI ${miui}` : ''})`);

  if (isInstalled(deviceId)) {
    console.log('  App already installed — skip MIUI prep.');
    return;
  }

  enableAdbInstallFlags(deviceId);

  if (!isXiaomi(deviceId)) {
    console.log('  Non-Xiaomi device — adb flags set.');
    return;
  }

  console.log(`
  ┌─ Redmi/MIUI — phone screen par ye karo (ek baar) ─────────────┐
  │ 1) Developer options khul raha hai — ye 3 ON karo:             │
  │    • USB debugging                                               │
  │    • USB debugging (Security settings)  ← 3 warning Accept karo │
  │    • Install via USB                    ← SIM + Mi Account zaruri│
  │                                                                  │
  │ 2) "Install via USB" ON nahi ho raha?                           │
  │    → Wi-Fi OFF, Mobile data ON, phir toggle karo               │
  │                                                                  │
  │ 3) Install chalte waqt phone par popup aaye to ALLOW dabao       │
  │    (Cancel / Remember+Deny mat karo)                            │
  └──────────────────────────────────────────────────────────────────┘
`);

  openDeveloperOptions(deviceId);
  openMiuiInstallViaUsb(deviceId);

  console.log('  Phone screen dekho — settings ON karo, phir install continue hoga.');
  console.log('  (Agar pehle "Don\'t allow" dabaya tha: Security → Install via USB → CallTech unblock)\n');
}

const devices = listDevices();
if (devices.length === 0) {
  console.log('[CallTech] No device — connect phone with USB debugging ON.');
  process.exit(0);
}

devices.forEach(prepareDevice);
