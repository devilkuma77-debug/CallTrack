const {execSync} = require('child_process');
const fs = require('fs');
const path = require('path');

function findServerEnv() {
  const candidates = [
    path.join(__dirname, '..', '..', 'Server', '.env'),
    path.join(__dirname, '..', '..', 'server', '.env'),
    path.join(__dirname, '..', 'server', '.env'),
    path.join(__dirname, '..', 'Server', '.env'),
  ];
  return candidates.find(filePath => fs.existsSync(filePath)) || candidates[0];
}

function listAdbDevices() {
  const output = execSync('adb devices', {encoding: 'utf8'});
  const devices = output
    .split('\n')
    .slice(1)
    .map(line => line.trim())
    .filter(Boolean)
    .map(line => {
      const parts = line.split(/\s+/);
      return {id: parts[0], status: parts[1] || ''};
    })
    .filter(item => item.id && item.id !== 'List' && item.status === 'device');

  const physical = devices.filter(item => !item.id.startsWith('emulator-'));
  if (physical.length > 0) {
    return physical.map(item => item.id);
  }
  return devices.map(item => item.id);
}

function printNoDeviceHelp() {
  console.log(`
Phone ADB me nahi dikh raha (real device connect nahi).

Phone par:
  1. USB cable data wala lagao (charging-only nahi)
  2. Notification → USB → File transfer / MTP
  3. Settings → Developer options:
       • USB debugging ON
       • USB debugging (Security settings) ON   [Xiaomi/Redmi]
       • Install via USB ON                      [Xiaomi/Redmi]
  4. Popup aaye to "Allow USB debugging" → Always allow → OK

PC par:
  adb kill-server
  adb start-server
  adb devices

Jab list me "device" dikhe (unauthorized/offline nahi), tab:
  cd App
  npm run android
`);
}

module.exports = {
  findServerEnv,
  listAdbDevices,
  printNoDeviceHelp,
};
