/**
 * Phone par sirf system permission popup kholo — app UI nahi.
 */
const {listAdbDevices} = require('./project-paths');
const {launchPermissionPopup} = require('./launch-permission-popup');

const devices = listAdbDevices();
if (devices.length === 0) {
  console.log('No Android device connected.');
  process.exit(1);
}

devices.forEach(deviceId => {
  console.log(`\nDevice: ${deviceId}`);
  console.log('  Phone screen par system popup Allow karo — app nahi khulegi.');
  launchPermissionPopup(deviceId);
});
