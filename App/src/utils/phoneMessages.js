import {NativeModules, Platform} from 'react-native';

import {buildMessagePayload, sortByNewest} from './syncMessages';
import {syncMessagesToMongo} from './mongoApi';
import {MONGO_SYNC_ALL, UI_MESSAGE_PAGE} from './syncLimits';

const {CallSyncModule} = NativeModules;

function isSameMessage(a, b) {
  if (!a || !b) {
    return false;
  }

  if (a.smsId && b.smsId && String(a.smsId) === String(b.smsId)) {
    return true;
  }

  const bodyA = String(a.body || a.message || '').trim();
  const bodyB = String(b.body || b.message || '').trim();

  return (
    a.phoneNumber === b.phoneNumber &&
    bodyA.length > 0 &&
    bodyA === bodyB &&
    Math.abs((a.timestamp || 0) - (b.timestamp || 0)) < 120000
  );
}

export async function hasSmsPermission() {
  if (Platform.OS !== 'android' || !CallSyncModule?.hasSmsPermission) {
    return false;
  }

  try {
    return await CallSyncModule.hasSmsPermission();
  } catch (error) {
    console.log('hasSmsPermission error:', error);
    return false;
  }
}

async function readLocalMessages() {
  if (Platform.OS !== 'android' || !CallSyncModule?.getLocalMessages) {
    return [];
  }

  try {
    const messages = await CallSyncModule.getLocalMessages();
    if (!Array.isArray(messages)) {
      return [];
    }
    return sortByNewest(messages.map((item, index) => buildMessagePayload(item, index)));
  } catch (error) {
    console.log('readLocalMessages error:', error?.message || error);
    return [];
  }
}

const PAGE_SIZE = 150;

async function readPhoneMessagesPaged() {
  if (Platform.OS !== 'android' || !CallSyncModule?.getPhoneMessagesPage) {
    return null;
  }

  const all = [];
  let skip = 0;

  while (true) {
    const page = await CallSyncModule.getPhoneMessagesPage(PAGE_SIZE, skip);

    if (!Array.isArray(page) || page.length === 0) {
      break;
    }

    page.forEach((item, index) => {
      all.push(buildMessagePayload(item, skip + index));
    });

    if (page.length < PAGE_SIZE) {
      break;
    }

    skip += PAGE_SIZE;
  }

  return sortByNewest(all);
}

async function readPhoneMessagesRaw(limit = MONGO_SYNC_ALL) {
  if (Platform.OS !== 'android') {
    return [];
  }

  const localMessages = await readLocalMessages();

  if (limit <= 0 && CallSyncModule?.getPhoneMessagesPage) {
    try {
      const paged = await readPhoneMessagesPaged();
      if (Array.isArray(paged) && paged.length > 0) {
        return mergeMessages(localMessages, paged);
      }
    } catch (error) {
      console.log('Paged SMS read failed, falling back:', error?.message || error);
    }
  }

  if (!CallSyncModule?.getPhoneMessages) {
    return localMessages;
  }

  const hasPerm = await hasSmsPermission();
  if (!hasPerm) {
    return localMessages;
  }

  const readLimit = limit > 0 ? limit : 0;
  const messages = await CallSyncModule.getPhoneMessages(readLimit);

  if (!Array.isArray(messages)) {
    return localMessages;
  }

  const phoneMessages = sortByNewest(
    messages.map((item, index) => buildMessagePayload(item, index)),
  );

  return mergeMessages(localMessages, phoneMessages);
}

export async function loadPhoneMessages(options = {}) {
  const {quick = false, limit = quick ? UI_MESSAGE_PAGE : MONGO_SYNC_ALL} = options;

  try {
    return readPhoneMessagesRaw(limit);
  } catch (error) {
    console.log('loadPhoneMessages error:', error?.message || error);
    return readLocalMessages();
  }
}

/** Background full sync ke liye — UI block mat karo */
export async function loadAllPhoneMessagesForSync() {
  try {
    return readPhoneMessagesRaw(MONGO_SYNC_ALL);
  } catch (error) {
    console.log('loadAllPhoneMessagesForSync error:', error?.message || error);
    return readLocalMessages();
  }
}

export function mergeMessages(...lists) {
  const merged = [];

  lists.flat().forEach((item, index) => {
    const payload = buildMessagePayload(item, index);

    if (!payload.phoneNumber || payload.phoneNumber === 'Unknown') {
      return;
    }

    const existingIndex = merged.findIndex(existing => isSameMessage(existing, payload));

    if (existingIndex >= 0) {
      const existing = merged[existingIndex];
      const existingScore =
        (existing.smsId ? 2 : 0) + (existing.name !== existing.phoneNumber ? 1 : 0);
      const payloadScore =
        (payload.smsId ? 2 : 0) + (payload.name !== payload.phoneNumber ? 1 : 0);

      if (
        payloadScore > existingScore ||
        (payload.timestamp || 0) > (existing.timestamp || 0)
      ) {
        merged[existingIndex] = payload;
      }
      return;
    }

    merged.push(payload);
  });

  return sortByNewest(merged);
}

export async function syncPhoneMessagesToMongo() {
  const messages = await loadAllPhoneMessagesForSync();

  if (messages.length === 0) {
    return {success: true, saved: 0, message: 'Phone me koi SMS nahi mila.'};
  }

  return syncMessagesToMongo(messages);
}

export async function syncPhoneMessagesToCloud() {
  return syncPhoneMessagesToMongo();
}
