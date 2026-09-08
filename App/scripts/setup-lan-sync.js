/**
 * Same WiFi par phone sync — PC IP se SYNC_API_URL set karo.
 * Usage: npm run server:lan  (phir npm run android rebuild)
 */
const fs = require('fs');
const os = require('os');
const path = require('path');

const envPath = require('./project-paths').findServerEnv();

function getLanIp() {
  const nets = os.networkInterfaces();
  for (const name of Object.keys(nets)) {
    for (const net of nets[name] || []) {
      if (net.family === 'IPv4' && !net.internal) {
        return net.address;
      }
    }
  }
  return null;
}

const ip = getLanIp();
if (!ip) {
  console.error('LAN IP not found — WiFi connect karo');
  process.exit(1);
}

const syncUrl = `http://${ip}:3000/api`;

if (!fs.existsSync(envPath)) {
  console.error('Server/.env missing — pehle MONGODB_URI set karo');
  process.exit(1);
}

let env = fs.readFileSync(envPath, 'utf8');
if (/^SYNC_API_URL=/m.test(env)) {
  env = env.replace(/^SYNC_API_URL=.*$/m, `SYNC_API_URL=${syncUrl}`);
} else {
  env += `\nSYNC_API_URL=${syncUrl}\n`;
}

if (!/^MONGODB_DB_NAME=/m.test(env)) {
  env += 'MONGODB_DB_NAME=calltech\n';
}

fs.writeFileSync(envPath, env);
console.log(`[OK] SYNC_API_URL=${syncUrl}`);
console.log('Ab:');
console.log('  1) npm run server        (terminal 1)');
console.log('  2) npm run android       (rebuild + install)');
console.log('  3) npm run server:watch  (real-time data dekho)');
