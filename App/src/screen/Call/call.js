import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  Alert,
  AppState,
  DeviceEventEmitter,
  FlatList,
  Linking,
  NativeModules,
  Platform,
  RefreshControl,
  StatusBar,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {useFocusEffect} from '@react-navigation/native';
import CallLogs from 'react-native-call-log';

import AppIcon from '../../components/AppIcon';
import {
  Avatar,
  EmptyState,
  FilterChips,
  InfoCard,
  ListDivider,
  ScreenHero,
  SectionHeader,
} from '../../components/ui/CommonUI';
import {mergeCallLists} from '../../utils/syncCallLogs';
import {fetchMongoCallLogs} from '../../utils/mongoApi';
import {runAutoCloudSync} from '../../utils/backgroundSync';
import {buildCallPayload} from '../../utils/callMetadata';
import {sortByNewest} from '../../utils/syncMessages';
import {
  formatRelativeTime,
  getDisplayName,
  groupByDateSection,
} from '../../utils/uiHelpers';
import {CALL_LOG_LOAD_COUNT, FETCH_LIMIT} from '../../utils/syncLimits';
import {theme} from '../../theme/theme';

const {CallSyncModule} = NativeModules;

const FILTERS = [
  {id: 'ALL', label: 'All'},
  {id: 'INCOMING', label: 'Incoming'},
  {id: 'OUTGOING', label: 'Outgoing'},
  {id: 'MISSED', label: 'Missed'},
];

function getTypeConfig(type) {
  switch (type) {
    case 'INCOMING':
      return {icon: 'arrow-down', color: theme.colors.incoming, label: 'Incoming'};
    case 'OUTGOING':
      return {icon: 'arrow-up', color: theme.colors.outgoing, label: 'Outgoing'};
    case 'MISSED':
      return {icon: 'close', color: theme.colors.missed, label: 'Missed'};
    default:
      return {icon: 'help', color: theme.colors.muted, label: 'Unknown'};
  }
}

function formatDuration(seconds) {
  const totalSeconds = parseInt(seconds, 10) || 0;
  const minutes = Math.floor(totalSeconds / 60);
  const remainingSeconds = totalSeconds % 60;

  if (minutes > 0) {
    return `${minutes}m ${remainingSeconds}s`;
  }

  return `${remainingSeconds}s`;
}

function CallsScreen() {
  const [calls, setCalls] = useState([]);
  const [permissionGranted, setPermissionGranted] = useState(true);
  const [mongoConnected, setMongoConnected] = useState(false);
  const [activeFilter, setActiveFilter] = useState('ALL');
  const [refreshing, setRefreshing] = useState(false);

  const appState = useRef(AppState.currentState);

  const loadPhoneCalls = useCallback(async () => {
    try {
      let merged = [];

      if (Platform.OS === 'android' && CallSyncModule?.getLocalCalls) {
        const localCalls = await CallSyncModule.getLocalCalls();
        if (Array.isArray(localCalls) && localCalls.length > 0) {
          merged = sortByNewest(
            localCalls.map((log, index) => buildCallPayload(log, index)),
          );
        }
      }

      const hasCallLog =
        Platform.OS === 'android' && CallSyncModule?.hasCallLogPermission
          ? await CallSyncModule.hasCallLogPermission()
          : false;

      setPermissionGranted(hasCallLog || merged.length > 0);

      if (hasCallLog) {
        const logs = await CallLogs.load(CALL_LOG_LOAD_COUNT);
        if (logs && logs.length > 0) {
          const phoneCalls = sortByNewest(
            logs.map((log, index) => buildCallPayload(log, index)),
          );
          merged = sortByNewest(mergeCallLists(merged, phoneCalls));
        }
      }

      setCalls(merged);
    } catch (error) {
      console.log('PHONE CALL LOG ERROR:', error);
    }
  }, []);

  const refreshFromMongo = useCallback(async () => {
    const mongoCalls = await fetchMongoCallLogs(FETCH_LIMIT);

    if (!Array.isArray(mongoCalls) || mongoCalls.length === 0) {
      return;
    }

    setMongoConnected(true);

    const formattedCalls = mongoCalls.map((log, index) => buildCallPayload(log, index));
    setCalls(current => sortByNewest(mergeCallLists(current, formattedCalls)));
  }, []);

  const onSwipeRefresh = useCallback(async () => {
    setRefreshing(true);
    try {
      await loadPhoneCalls();
      const result = await runAutoCloudSync();
      setMongoConnected(Boolean(result?.success || result?.serverOnline));
      await refreshFromMongo();
    } finally {
      setRefreshing(false);
    }
  }, [loadPhoneCalls, refreshFromMongo]);

  useFocusEffect(
    useCallback(() => {
      loadPhoneCalls();
      runAutoCloudSync().catch(() => {});
      refreshFromMongo();
    }, [loadPhoneCalls, refreshFromMongo]),
  );

  useEffect(() => {
    const callSub = DeviceEventEmitter.addListener('CallTechNewCall', () => {
      loadPhoneCalls();
      runAutoCloudSync().catch(() => {});
      refreshFromMongo();
    });

    const subscription = AppState.addEventListener('change', nextAppState => {
      if (appState.current.match(/inactive|background/) && nextAppState === 'active') {
        loadPhoneCalls();
        refreshFromMongo();
      }
      appState.current = nextAppState;
    });

    return () => {
      callSub.remove();
      subscription.remove();
    };
  }, [loadPhoneCalls, refreshFromMongo]);

  const filteredCalls = useMemo(() => {
    let result = [...calls];

    if (activeFilter === 'INCOMING') {
      result = result.filter(item => item.type === 'INCOMING');
    } else if (activeFilter === 'OUTGOING') {
      result = result.filter(item => item.type === 'OUTGOING');
    } else if (activeFilter === 'MISSED') {
      result = result.filter(item => item.type === 'MISSED');
    }

    return sortByNewest(result);
  }, [calls, activeFilter]);

  const sections = useMemo(
    () => groupByDateSection(filteredCalls, item => item.timestamp),
    [filteredCalls],
  );

  const flatData = useMemo(
    () =>
      sections.flatMap(section => [
        {type: 'header', id: `header_${section.title}`, title: section.title, count: section.data.length},
        ...section.data.map(item => ({type: 'call', ...item})),
      ]),
    [sections],
  );

  const getFilterCount = filterId => {
    if (filterId === 'ALL') return calls.length;
    if (filterId === 'INCOMING') return calls.filter(item => item.type === 'INCOMING').length;
    if (filterId === 'OUTGOING') return calls.filter(item => item.type === 'OUTGOING').length;
    if (filterId === 'MISSED') return calls.filter(item => item.type === 'MISSED').length;
    return 0;
  };

  const callNumber = number => {
    if (!number || number === 'Unknown') {
      return;
    }

    Linking.openURL(`tel:${number}`).catch(() => {
      Alert.alert('Error', 'Dialer open nahi ho paya');
    });
  };

  const renderCallItem = item => {
    const config = getTypeConfig(item.type);
    const displayName = getDisplayName(item);
    const durationText =
      item.duration > 0
        ? formatDuration(item.duration)
        : config.label;

    return (
      <TouchableOpacity
        style={styles.row}
        activeOpacity={0.7}
        onPress={() => callNumber(item.phoneNumber)}>
        <Avatar name={item.name} phoneNumber={item.phoneNumber} size={44} />

        <View style={styles.rowBody}>
          <View style={styles.rowTop}>
            <Text
              style={[styles.rowName, item.type === 'MISSED' && styles.rowNameMissed]}
              numberOfLines={1}>
              {displayName}
            </Text>
            <Text style={styles.rowTime}>
              {formatRelativeTime(item.timestamp) || item.dateTime}
            </Text>
          </View>

          <View style={styles.rowBottom}>
            <AppIcon name={config.icon} size={14} color={config.color} />
            <Text style={styles.rowMeta} numberOfLines={1}>
              {durationText} · {item.phoneNumber}
            </Text>
          </View>
        </View>

        <TouchableOpacity
          style={styles.callBtn}
          onPress={() => callNumber(item.phoneNumber)}
          activeOpacity={0.8}>
          <AppIcon name="call" size={18} color={theme.colors.textDark} />
        </TouchableOpacity>
      </TouchableOpacity>
    );
  };

  const renderItem = ({item}) => {
    if (item.type === 'header') {
      return <SectionHeader title={item.title} count={item.count} />;
    }

    return renderCallItem(item);
  };

  return (
    <SafeAreaView style={styles.safeArea} edges={['top']}>
      <StatusBar barStyle="dark-content" backgroundColor={theme.colors.white} />

      <ScreenHero
        title="Calls"
        subtitle={`${calls.length} calls${mongoConnected ? ' · Sync ON' : ''}`}
        onRefresh={onSwipeRefresh}
      />

      <FilterChips
        filters={FILTERS}
        activeId={activeFilter}
        onSelect={setActiveFilter}
        getCount={getFilterCount}
      />

      <View style={styles.content}>
        {!permissionGranted && Platform.OS === 'android' && (
          <InfoCard
            icon="alert-circle-outline"
            title="Permission chahiye"
            text="Call log permission allow karein"
            tone="warning"
          />
        )}

        {filteredCalls.length === 0 ? (
          <EmptyState
            icon="call-outline"
            title="Koi call nahi"
            subtitle="Neeche kheench kar refresh karein"
          />
        ) : (
          <FlatList
            data={flatData}
            keyExtractor={item =>
              item.type === 'header' ? item.id : String(item.id || `${item.phoneNumber}_${item.timestamp}`)
            }
            renderItem={renderItem}
            contentContainerStyle={styles.listContent}
            showsVerticalScrollIndicator={false}
            refreshControl={
              <RefreshControl
                refreshing={refreshing}
                onRefresh={onSwipeRefresh}
                colors={[theme.colors.primary]}
                tintColor={theme.colors.primary}
              />
            }
            ItemSeparatorComponent={({leadingItem}) =>
              leadingItem?.type === 'header' ? null : <ListDivider />
            }
          />
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: theme.colors.white,
  },
  content: {
    flex: 1,
  },
  listContent: {
    paddingBottom: 16,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    backgroundColor: theme.colors.white,
  },
  rowBody: {
    flex: 1,
    marginLeft: 12,
    minWidth: 0,
  },
  rowTop: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
  },
  rowName: {
    flex: 1,
    fontSize: 16,
    fontFamily: theme.fonts.medium,
    fontWeight: '600',
    color: theme.colors.textDark,
  },
  rowNameMissed: {
    color: theme.colors.missed,
  },
  rowTime: {
    fontSize: 12,
    fontFamily: theme.fonts.regular,
    color: theme.colors.textMuted,
  },
  rowBottom: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    marginTop: 4,
  },
  rowMeta: {
    flex: 1,
    fontSize: 13,
    fontFamily: theme.fonts.regular,
    color: theme.colors.textMuted,
  },
  callBtn: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: theme.colors.primarySoft,
    alignItems: 'center',
    justifyContent: 'center',
    marginLeft: 8,
  },
});

export default CallsScreen;
