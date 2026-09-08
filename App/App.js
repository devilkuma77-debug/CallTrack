import React, {useEffect, useState} from 'react';
import {ActivityIndicator, InteractionManager, StyleSheet, Text, View} from 'react-native';
import {NavigationContainer, DefaultTheme} from '@react-navigation/native';
import {SafeAreaProvider} from 'react-native-safe-area-context';

import AppNavigator from './src/navigation/AppNavigator';
import ErrorBoundary from './src/components/ErrorBoundary';
import {MessageRealtimeProvider} from './src/context/MessageRealtimeContext';
import {theme} from './src/theme/theme';
import {MONGO_API_URL} from './src/config/mongoConfig';
import {
  configureNativeMongoApi,
  fetchNativeAtlasStats,
  runAutoCloudSync,
  startBackgroundCallSync,
} from './src/utils/backgroundSync';

const navigationTheme = {
  ...DefaultTheme,
  colors: {
    ...DefaultTheme.colors,
    primary: theme.colors.primary,
    background: theme.colors.bg,
    card: theme.colors.card,
    text: theme.colors.textDark,
    border: theme.colors.border,
    notification: theme.colors.primary,
  },
};

function BootScreen() {
  return (
    <View style={styles.boot}>
      <Text style={styles.bootTitle}>CallTech</Text>
      <ActivityIndicator size="large" color={theme.colors.primary} style={styles.bootSpinner} />
      <Text style={styles.bootSub}>Loading...</Text>
    </View>
  );
}

function runStartupSync() {
  (async () => {
    try {
      await configureNativeMongoApi(MONGO_API_URL);
      await startBackgroundCallSync();
      runAutoCloudSync().catch(() => {});

      fetchNativeAtlasStats().then(stats => {
        if (stats.success) {
          console.log(
            'MongoDB sync OK — messages:',
            stats.messageCount,
            'calls:',
            stats.callCount,
          );
        }
      });
    } catch (error) {
      console.log('Startup sync error:', error?.message || error);
    }
  })();
}

function App() {
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const showTimer = setTimeout(() => setReady(true), 250);

    const syncTask = InteractionManager.runAfterInteractions(() => {
      setTimeout(runStartupSync, 600);
    });

    return () => {
      clearTimeout(showTimer);
      syncTask.cancel();
    };
  }, []);

  if (!ready) {
    return (
      <SafeAreaProvider>
        <BootScreen />
      </SafeAreaProvider>
    );
  }

  return (
    <SafeAreaProvider>
      <ErrorBoundary>
        <MessageRealtimeProvider>
          <NavigationContainer theme={navigationTheme}>
            <AppNavigator />
          </NavigationContainer>
        </MessageRealtimeProvider>
      </ErrorBoundary>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  boot: {
    flex: 1,
    backgroundColor: theme.colors.white,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  bootTitle: {
    fontSize: 28,
    fontWeight: '700',
    color: theme.colors.textDark,
  },
  bootSpinner: {
    marginTop: 24,
  },
  bootSub: {
    marginTop: 12,
    fontSize: 14,
    fontFamily: theme.fonts.regular,
    color: theme.colors.textMuted,
  },
});

export default App;
