import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  StatusBar,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import CallLogs from 'react-native-call-log';

import {
  QuickActionCard,
  ScreenHero,
  StatusPill,
} from '../../components/ui/CommonUI';
import {CALL_LOG_LOAD_COUNT} from '../../utils/syncLimits';
import {theme} from '../../theme/theme';
import {
  isBackgroundCallSyncEnabled,
  fetchNativeAtlasStats,
  runAutoCloudSync,
} from '../../utils/backgroundSync';
import {useMessageRealtime} from '../../context/MessageRealtimeContext';

function HomeScreen({navigation}) {
  const [backgroundActive, setBackgroundActive] = useState(false);
  const {messageCount, mongoLive} = useMessageRealtime();
  const [callCount, setCallCount] = useState(0);
  const [cloudStats, setCloudStats] = useState({messageCount: 0, callCount: 0});
  const [serverOnline, setServerOnline] = useState(false);

  const checkServer = useCallback(async () => {
    const stats = await fetchNativeAtlasStats();
    if (stats.success) {
      setServerOnline(true);
      setCloudStats({
        messageCount: stats.messageCount || 0,
        callCount: stats.callCount || 0,
      });
      return true;
    }
    setServerOnline(false);
    return false;
  }, []);

  const loadStats = useCallback(async () => {
    try {
      const logs = await CallLogs.load(CALL_LOG_LOAD_COUNT).catch(() => []);
      setCallCount(Array.isArray(logs) ? logs.length : 0);
      await runAutoCloudSync().catch(() => {});
      await checkServer();
    } catch (error) {
      console.log('loadStats error:', error);
    }
  }, [checkServer]);

  useEffect(() => {
    isBackgroundCallSyncEnabled().then(setBackgroundActive);
    loadStats();
    checkServer();
    const serverPoll = setInterval(checkServer, 10000);
    return () => clearInterval(serverPoll);
  }, [loadStats, messageCount, checkServer]);

  const syncStatus = useMemo(() => {
    if (serverOnline && backgroundActive) {
      return {
        active: true,
        label: 'Sync ON',
        sublabel: `${cloudStats.messageCount} SMS · ${cloudStats.callCount} calls saved`,
      };
    }
    if (backgroundActive) {
      return {active: true, label: 'Sync ON', sublabel: ''};
    }
    return {active: false, label: 'Sync OFF', sublabel: 'App open karte hi start hoga'};
  }, [backgroundActive, serverOnline, cloudStats]);

  return (
    <SafeAreaView style={styles.safeArea} edges={['top']}>
      <StatusBar barStyle="dark-content" backgroundColor={theme.colors.white} />

      <ScreenHero
        title="CallTech"
        subtitle="Calls aur SMS ek jagah"
        stats={[
          {label: 'Calls', value: callCount},
          {label: 'SMS', value: messageCount},
          {label: 'Cloud', value: mongoLive || serverOnline ? 'ON' : 'OFF'},
        ]}
      />

      <ScrollView
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}>

        <StatusPill
          active={syncStatus.active}
          label={syncStatus.label}
          sublabel={syncStatus.sublabel}
        />

        <Text style={styles.sectionTitle}>Menu</Text>

        <QuickActionCard
          icon="call-outline"
          iconColor={theme.colors.textDark}
          title="Calls"
          subtitle="Incoming, outgoing, missed"
          hint={`${callCount} calls`}
          onPress={() => navigation.getParent()?.navigate('Chat')}
        />

        <QuickActionCard
          icon="chatbubbles-outline"
          iconColor={theme.colors.textDark}
          title="Messages"
          subtitle="Saare SMS contacts ke saath"
          hint={`${messageCount} messages`}
          onPress={() => navigation.getParent()?.navigate('Messages')}
        />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: theme.colors.white,
  },
  scrollContent: {
    paddingHorizontal: 16,
    paddingBottom: 32,
  },
  sectionTitle: {
    fontSize: 13,
    fontFamily: theme.fonts.medium,
    fontWeight: '600',
    color: theme.colors.textMuted,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginTop: 20,
    marginBottom: 8,
  },
});

export default HomeScreen;
