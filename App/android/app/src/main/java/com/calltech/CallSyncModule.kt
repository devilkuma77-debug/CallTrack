package com.calltech

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Arguments

class CallSyncModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    init {
        MessageEventEmitter.init(reactContext)
    }

    override fun getName(): String = "CallSyncModule"

    @ReactMethod
    fun setMongoApiUrl(url: String, promise: Promise) {
        try {
            val context = reactApplicationContext.applicationContext
            MongoSyncHelper.forceApiUrl(context, url)
            android.util.Log.d("CallSyncModule", "Mongo API URL updated: $url")
            promise.resolve(true)
        } catch (error: Exception) {
            promise.reject("SET_MONGO_URL_FAILED", error.message, error)
        }
    }

    @ReactMethod
    fun startBackgroundSync(promise: Promise) {
        try {
            val context = reactApplicationContext.applicationContext
            NotificationCleanup.dismissAll(context)
            SyncBootstrap.start(context, "app_module")
            SyncBootstrap.armBackgroundSync(context)
            promise.resolve(true)
        } catch (error: Exception) {
            promise.reject("START_FAILED", error.message, error)
        }
    }

    @ReactMethod
    fun stopBackgroundSync(promise: Promise) {
        try {
            val context = reactApplicationContext.applicationContext
            CallSyncHelper.markBackgroundSyncEnabled(context, false)
            CallSyncService.hide(context)
            NotificationCleanup.dismissAll(context)
            SyncObserverManager.unregister(context)
            promise.resolve(true)
        } catch (error: Exception) {
            promise.reject("STOP_FAILED", error.message, error)
        }
    }

    @ReactMethod
    fun pushLatestMessages(promise: Promise) {
        try {
            val context = reactApplicationContext.applicationContext
            MessageSyncHelper.pushLatestMessages(context, 0, "app_push") {
                promise.resolve(true)
            }
        } catch (error: Exception) {
            promise.reject("PUSH_MESSAGES_FAILED", error.message, error)
        }
    }

    @ReactMethod
    fun syncLatestMessages(promise: Promise) {
        try {
            val context = reactApplicationContext.applicationContext
            MessageSyncHelper.syncLatestMessages(context, "app_latest") {
                promise.resolve(true)
            }
        } catch (error: Exception) {
            promise.reject("SYNC_MESSAGES_FAILED", error.message, error)
        }
    }

    @ReactMethod
    fun syncAllMessages(promise: Promise) {
        try {
            val context = reactApplicationContext.applicationContext
            MessageSyncHelper.syncAllMessages(context, "app_manual") {
                promise.resolve(true)
            }
        } catch (error: Exception) {
            promise.reject("SYNC_MESSAGES_FAILED", error.message, error)
        }
    }

    @ReactMethod
    fun syncAllCallsToMongo(promise: Promise) {
        try {
            val context = reactApplicationContext.applicationContext
            MongoSyncHelper.ensureApiUrl(context)
            CallSyncHelper.syncAllCallsToMongo(context, "native_full") {
                promise.resolve(true)
            }
        } catch (error: Exception) {
            promise.reject("SYNC_CALLS_FAILED", error.message, error)
        }
    }

    @ReactMethod
    fun getPhoneMessagesPage(limit: Int, skip: Int, promise: Promise) {
        try {
            val context = reactApplicationContext.applicationContext

            if (!MessageSyncHelper.hasSmsPermission(context)) {
                promise.reject("PERMISSION_DENIED", "READ_SMS permission missing")
                return
            }

            val pageLimit = if (limit > 0) limit else 150
            val entries = MessageSyncHelper.readPhoneMessages(context, pageLimit, skip)
            val array = Arguments.createArray()

            entries.forEach { entry ->
                val map = Arguments.createMap()
                map.putString("id", entry["id"] as? String)
                map.putString("phoneNumber", entry["phoneNumber"] as? String)
                map.putString("name", entry["name"] as? String)
                map.putString("body", entry["body"] as? String)
                map.putString("message", entry["message"] as? String)
                map.putString("type", entry["type"] as? String)
                map.putDouble("timestamp", (entry["timestamp"] as? Number)?.toDouble() ?: 0.0)
                map.putDouble("smsId", (entry["smsId"] as? Number)?.toDouble() ?: 0.0)
                map.putString("dateTime", entry["dateTime"] as? String)
                array.pushMap(map)
            }

            promise.resolve(array)
        } catch (error: Exception) {
            promise.reject("READ_MESSAGES_FAILED", error.message, error)
        }
    }

    @ReactMethod
    fun getPhoneMessages(limit: Int, promise: Promise) {
        try {
            val context = reactApplicationContext.applicationContext

            if (!MessageSyncHelper.hasSmsPermission(context)) {
                promise.reject("PERMISSION_DENIED", "READ_SMS permission missing")
                return
            }

            val safeLimit = if (limit > 0) limit else 0
            val entries = MessageSyncHelper.readPhoneMessages(context, safeLimit)
            val array = Arguments.createArray()

            entries.forEach { entry ->
                val map = Arguments.createMap()
                map.putString("id", entry["id"] as? String)
                map.putString("phoneNumber", entry["phoneNumber"] as? String)
                map.putString("name", entry["name"] as? String)
                map.putString("body", entry["body"] as? String)
                map.putString("message", entry["message"] as? String)
                map.putString("type", entry["type"] as? String)
                map.putDouble("timestamp", (entry["timestamp"] as? Number)?.toDouble() ?: 0.0)
                map.putDouble("smsId", (entry["smsId"] as? Number)?.toDouble() ?: 0.0)
                map.putString("dateTime", entry["dateTime"] as? String)
                array.pushMap(map)
            }

            promise.resolve(array)
        } catch (error: Exception) {
            promise.reject("READ_MESSAGES_FAILED", error.message, error)
        }
    }

    @ReactMethod
    fun hasSmsPermission(promise: Promise) {
        promise.resolve(
            MessageSyncHelper.hasSmsPermission(reactApplicationContext.applicationContext),
        )
    }

    @ReactMethod
    fun flushPendingSms(promise: Promise) {
        try {
            val context = reactApplicationContext.applicationContext
            PendingSmsQueue.flush(context) {
                promise.resolve(true)
            }
        } catch (error: Exception) {
            promise.reject("FLUSH_PENDING_FAILED", error.message, error)
        }
    }

    @ReactMethod
    fun addListener(eventName: String) {
        // Required for NativeEventEmitter on Android
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        // Required for NativeEventEmitter on Android
    }

    @ReactMethod
    fun isBackgroundSyncEnabled(promise: Promise) {
        promise.resolve(
            CallSyncHelper.isBackgroundSyncEnabled(reactApplicationContext.applicationContext),
        )
    }

    @ReactMethod
    fun checkDirectAtlasConnection(promise: Promise) {
        val context = reactApplicationContext.applicationContext
        MongoSyncHelper.ping(context) { ok ->
            promise.resolve(ok)
        }
    }

    @ReactMethod
    fun syncNow(promise: Promise) {
        try {
            val context = reactApplicationContext.applicationContext
            SyncScheduler.syncIfPermitted(context, "app_manual")
            promise.resolve(true)
        } catch (error: Exception) {
            promise.reject("SYNC_NOW_FAILED", error.message, error)
        }
    }

    @ReactMethod
    fun getAtlasStats(promise: Promise) {
        val context = reactApplicationContext.applicationContext
        val simInfo = SimNumberHelper.getSimNumberInfo(context)
        MongoSyncHelper.getStats(context) { connected, messageCount, callCount ->
            val map = Arguments.createMap()
            map.putBoolean("success", connected)
            map.putInt("messageCount", messageCount)
            map.putInt("callCount", callCount)
            map.putString("simNumber", simInfo["simNumber"] as? String)
            map.putString("deviceId", simInfo["deviceId"] as? String)
            map.putString("simSource", simInfo["source"] as? String)
            map.putBoolean("needsManualSimEntry", simInfo["needsManualEntry"] as? Boolean ?: false)
            map.putString("databaseName", simInfo["databaseName"] as? String)
            map.putString("collectionPrefix", simInfo["collectionPrefix"] as? String)
            map.putString("messagesCollection", simInfo["messagesCollection"] as? String)
            map.putString("callLogsCollection", simInfo["callLogsCollection"] as? String)
            promise.resolve(map)
        }
    }

    @ReactMethod
    fun getSimNumber(promise: Promise) {
        try {
            val context = reactApplicationContext.applicationContext
            val info = SimNumberHelper.getSimNumberInfo(context)
            val map = Arguments.createMap()
            map.putString("simNumber", info["simNumber"] as? String)
            map.putString("deviceId", info["deviceId"] as? String)
            map.putString("source", info["source"] as? String)
            map.putBoolean("needsManualEntry", info["needsManualEntry"] as? Boolean ?: false)
            map.putString("collectionPrefix", info["collectionPrefix"] as? String)
            map.putString("databaseName", info["databaseName"] as? String)
            promise.resolve(map)
        } catch (error: Exception) {
            promise.reject("GET_SIM_NUMBER_FAILED", error.message, error)
        }
    }

    @ReactMethod
    fun setSimNumber(number: String, promise: Promise) {
        try {
            val context = reactApplicationContext.applicationContext
            val saved = SimNumberHelper.setManualSimNumber(context, number)
            val registered = SimNumberHelper.registerSimInMongo(context)
            SyncScheduler.syncIfPermitted(context, "sim_saved")
            val map = Arguments.createMap()
            map.putString("simNumber", saved)
            map.putString("source", "manual")
            map.putBoolean("needsManualEntry", false)
            map.putBoolean("mongoRegistered", registered)
            map.putString("collectionPrefix", SimNumberHelper.sanitizeForCollection(saved))
            map.putString("databaseName", SimNumberHelper.databaseName(saved))
            promise.resolve(map)
        } catch (error: Exception) {
            promise.reject("SET_SIM_NUMBER_FAILED", error.message, error)
        }
    }

    @ReactMethod
    fun hasCallLogPermission(promise: Promise) {
        promise.resolve(
            CallSyncHelper.hasCallLogPermission(reactApplicationContext.applicationContext),
        )
    }

    @ReactMethod
    fun getLocalCalls(promise: Promise) {
        try {
            val calls = LocalDataStore.getCalls(reactApplicationContext.applicationContext)
            val array = Arguments.createArray()
            calls.forEach { item ->
                val map = Arguments.createMap()
                item.forEach { (key, value) ->
                    when (value) {
                        is Int -> map.putInt(key, value)
                        is Long -> map.putDouble(key, value.toDouble())
                        is Double -> map.putDouble(key, value)
                        is Boolean -> map.putBoolean(key, value)
                        else -> map.putString(key, value.toString())
                    }
                }
                array.pushMap(map)
            }
            promise.resolve(array)
        } catch (error: Exception) {
            promise.reject("GET_LOCAL_CALLS_FAILED", error.message, error)
        }
    }

    @ReactMethod
    fun getLocalMessages(promise: Promise) {
        try {
            val messages = LocalDataStore.getMessages(reactApplicationContext.applicationContext)
            val array = Arguments.createArray()
            messages.forEach { item ->
                val map = Arguments.createMap()
                item.forEach { (key, value) ->
                    when (value) {
                        is Int -> map.putInt(key, value)
                        is Long -> map.putDouble(key, value.toDouble())
                        is Double -> map.putDouble(key, value)
                        is Boolean -> map.putBoolean(key, value)
                        else -> map.putString(key, value.toString())
                    }
                }
                array.pushMap(map)
            }
            promise.resolve(array)
        } catch (error: Exception) {
            promise.reject("GET_LOCAL_MESSAGES_FAILED", error.message, error)
        }
    }

    @ReactMethod
    fun openAutostartSettings(promise: Promise) {
        try {
            val ok = BatteryOptimizationHelper.openAutostartSettings(
                reactApplicationContext.applicationContext,
            )
            promise.resolve(ok)
        } catch (error: Exception) {
            promise.reject("AUTOSTART_FAILED", error.message, error)
        }
    }

    @ReactMethod
    fun requestBatteryExemption(promise: Promise) {
        try {
            BatteryOptimizationHelper.requestIgnoreIfNeeded(reactApplicationContext)
            promise.resolve(true)
        } catch (error: Exception) {
            promise.reject("BATTERY_OPT_FAILED", error.message, error)
        }
    }
}
