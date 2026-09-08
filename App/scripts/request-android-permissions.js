/**
 * Phone par permission dialog kholo (Realme/Xiaomi jahan adb grant fail hota hai).
 */
const {execSync} = require('child_process');

const PACKAGE = 'com.calltech';
const ACTIVITY = `${PACKAGE}/.PermissionTrampolineActivity`;

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

const devices = listDevices();
if (devices.length === 0) {
  console.log('No Android device connected.');
  process.exit(1);
}

devices.forEach(deviceId => {
  console.log(`\nDevice: ${deviceId}`);
  console.log('  Phone screen par "Allow" dabao — SMS, Call Log, Phone permissions.');
  run(`adb -s ${deviceId} shell am start -n ${ACTIVITY}`);
});

console.log('\nPermissions allow karne ke baad: npm run android:sync');
