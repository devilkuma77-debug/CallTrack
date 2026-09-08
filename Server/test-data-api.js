require('dotenv').config();

const appId = process.env.ATLAS_DATA_APP_ID;
const apiKey = process.env.ATLAS_DATA_API_KEY;
const source = process.env.ATLAS_DATA_SOURCE || 'Cluster0';
const db = process.env.MONGODB_DB_NAME || 'calltech';
const region = (process.env.ATLAS_DATA_REGION || '').trim();

if (!appId || !apiKey) {
  console.error('\n[FAIL] ATLAS_DATA_APP_ID aur ATLAS_DATA_API_KEY .env me set karo');
  console.error('Atlas -> App Services -> Create App -> Data API -> API Key\n');
  process.exit(1);
}

const base = region
  ? `https://${region}.aws.data.mongodb-api.com/app/${appId}/endpoint/data/v1/action`
  : `https://data.mongodb-api.com/app/${appId}/endpoint/data/v1/action`;

async function post(action, body) {
  const response = await fetch(`${base}/${action}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'api-key': apiKey,
    },
    body: JSON.stringify(body),
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.error || `HTTP ${response.status}`);
  }
  return data;
}

(async () => {
  try {
    await post('find', {
      dataSource: source,
      database: db,
      collection: 'message',
      filter: {},
      limit: 1,
    });
    console.log('\n[OK] Atlas Data API connected — phone sync ready');
    console.log('Ab app rebuild karo: cd android && gradlew app:installDebug\n');
  } catch (error) {
    console.error('\n[FAIL]', error.message);
    process.exit(1);
  }
})();
