import React from 'react';
import {Platform, StyleSheet, Text, View} from 'react-native';
import {createBottomTabNavigator} from '@react-navigation/bottom-tabs';
import {useSafeAreaInsets} from 'react-native-safe-area-context';

import AppIcon from '../components/AppIcon';
import {theme} from '../theme/theme';

import HomeStackNavigator from './HomeStackNavigator';
import CallsScreen from '../screen/Call/call';
import MessagesScreen from '../screen/messages/message';

const Tab = createBottomTabNavigator();

const TABS = [
  {name: 'Home', component: HomeStackNavigator, label: 'Home', icon: 'home-outline', activeIcon: 'home'},
  {name: 'Messages', component: MessagesScreen, label: 'SMS', icon: 'chatbubbles-outline', activeIcon: 'chatbubbles'},
  {name: 'Chat', component: CallsScreen, label: 'Calls', icon: 'call-outline', activeIcon: 'call'},
];

function TabIcon({focused, color, tab}) {
  return (
    <View style={styles.tabIconWrap}>
      <AppIcon name={focused ? tab.activeIcon : tab.icon} size={22} color={color} />
    </View>
  );
}

function AppNavigator() {
  const insets = useSafeAreaInsets();
  const bottomPad = Platform.OS === 'android' ? Math.max(insets.bottom, 10) : insets.bottom;

  return (
    <Tab.Navigator
      initialRouteName="Home"
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: theme.colors.textDark,
        tabBarInactiveTintColor: theme.colors.textMuted,
        sceneContainerStyle: styles.scene,
        tabBarStyle: {
          height: 56 + bottomPad,
          paddingBottom: bottomPad,
          paddingTop: 4,
          backgroundColor: theme.colors.white,
          borderTopColor: theme.colors.border,
          borderTopWidth: 1,
          elevation: 0,
        },
        tabBarLabelStyle: {
          fontSize: 12,
          fontFamily: theme.fonts.medium,
          fontWeight: '500',
          marginTop: 2,
        },
      }}>
      {TABS.map(tab => (
        <Tab.Screen
          key={tab.name}
          name={tab.name}
          component={tab.component}
          options={{
            tabBarLabel: tab.label,
            tabBarIcon: ({focused, color}) => (
              <TabIcon focused={focused} color={color} tab={tab} />
            ),
          }}
        />
      ))}
    </Tab.Navigator>
  );
}

const styles = StyleSheet.create({
  scene: {
    flex: 1,
    backgroundColor: theme.colors.white,
  },
  tabIconWrap: {
    alignItems: 'center',
    justifyContent: 'center',
    height: 28,
  },
});

export default AppNavigator;
