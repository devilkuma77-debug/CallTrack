/**
 * Phone par sync trigger — app open kiye bina (USB debugging).
 */
const {execSync} = require('child_process');
const {listAdbDevices, findServerEnv} = require('./project-paths');

const PACKAGE = 'com.calltech';

function run(command) {
  try {
    execSync(command, {stdio: 'pipe', encoding: 'utf8'});
    return true;
  } catch (_error) {
    return false;
  }
}

function listDevices() {
  return listAdbDevices();
}

function readSyncApiUrl() {
  try {
    const fs = require('fs');
    const envPath = findServerEnv();
    if (!fs.existsSync(envPath)) {
      return '';
    }
    const env = fs.readFileSync(envPath, 'utf8');
    const match = env.match(/^SYNC_API_URL=\s*(\S+)/m);
    return match ? match[1].trim() : '';
  } catch (_error) {
    return '';
  }
}

function triggerOnDevice(deviceId) {
  console.log(`\nDevice: ${deviceId}`);
  const syncUrl = readSyncApiUrl();
  if (syncUrl) {
    const escaped = syncUrl.replace(/"/g, '\\"');
    run(
      `adb -s ${deviceId} shell am broadcast -a com.calltech.FORCE_SYNC -p ${PACKAGE} --es sync_url "${escaped}"`,
    );
    console.log(`  sync URL: ${syncUrl}`);
  } else {
    console.log('  WARNING: SYNC_API_URL missing — pehle npm run server:lan');
  }
  console.log('  Starting silent background sync...');
  run(`adb -s ${deviceId} shell am broadcast -a android.intent.action.MY_PACKAGE_REPLACED -p ${PACKAGE}`);
  run(`adb -s ${deviceId} shell am broadcast -a com.calltech.FORCE_SYNC -p ${PACKAGE}`);
  console.log('  Sync triggered — 30 sec wait, phir npm run server:watch dekho');
}

const devices = listDevices();
if (devices.length === 0) {
  console.log('No Android device connected.');
  process.exit(1);
}

devices.forEach(triggerOnDevice);
