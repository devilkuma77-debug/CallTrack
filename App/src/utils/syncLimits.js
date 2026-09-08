/** UI ke liye pehli load — fast open (blank screen avoid) */
export const UI_MESSAGE_PAGE = 200;

/** 0 = app me saare dikhao — koi limit nahi */
export const DISPLAY_LIMIT = 0;

/** MongoDB se fetch — 0 = saare */
export const FETCH_LIMIT = 0;

/** react-native-call-log me saare calls load karne ke liye */
export const CALL_LOG_LOAD_COUNT = 99999;

/** 0 = phone se saare SMS/calls sync karo */
export const MONGO_SYNC_ALL = 0;

/** @deprecated */
export const MONGO_MAX = MONGO_SYNC_ALL;
export const SYNC_LIMIT = DISPLAY_LIMIT;
export const REALTIME_LIMIT = DISPLAY_LIMIT;

export function applyListLimit(items = [], max = DISPLAY_LIMIT) {
  if (!max || max <= 0) {
    return items;
  }

  return items.slice(0, max);
}
