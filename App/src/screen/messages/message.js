import React, {useCallback, useMemo, useState} from 'react';
import {
  FlatList,
  RefreshControl,
  StatusBar,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {useFocusEffect} from '@react-navigation/native';

import AppIcon from '../../components/AppIcon';
import {
  Avatar,
  EmptyState,
  FilterChips,
  InfoCard,
  ListDivider,
  ScreenHero,
} from '../../components/ui/CommonUI';
import {useMessageRealtime} from '../../context/MessageRealtimeContext';
import {theme} from '../../theme/theme';
import {sortByNewest} from '../../utils/syncMessages';
import {
  formatRelativeTime,
  getDisplayName,
  isSentMessage,
} from '../../utils/uiHelpers';

const FILTERS = [
  {id: 'ALL', label: 'All'},
  {id: 'INBOX', label: 'Received'},
  {id: 'SENT', label: 'Sent'},
];

function MessagesScreen() {
  const {messages, mongoLive, permissionGranted, refreshMessages} =
    useMessageRealtime();
  const [activeFilter, setActiveFilter] = useState('ALL');
  const [refreshing, setRefreshing] = useState(false);
  const [selectedContact, setSelectedContact] = useState(null);

  useFocusEffect(
    useCallback(() => {
      refreshMessages().catch(() => {});
    }, [refreshMessages]),
  );

  const onSwipeRefresh = useCallback(async () => {
    setRefreshing(true);
    try {
      await refreshMessages();
    } finally {
      setRefreshing(false);
    }
  }, [refreshMessages]);

  const stats = useMemo(() => {
    const inbox = messages.filter(item => !isSentMessage(item.type)).length;
    const sent = messages.filter(item => isSentMessage(item.type)).length;
    return {total: messages.length, inbox, sent};
  }, [messages]);

  const filteredMessages = useMemo(() => {
    let result = sortByNewest(messages);

    if (activeFilter === 'INBOX') {
      result = result.filter(item => !isSentMessage(item.type));
    } else if (activeFilter === 'SENT') {
      result = result.filter(item => isSentMessage(item.type));
    }

    return result;
  }, [messages, activeFilter]);

  const conversations = useMemo(() => {
    const map = new Map();

    filteredMessages.forEach(item => {
      const key = String(item.phoneNumber || 'Unknown');
      const existing = map.get(key);

      if (!existing) {
        map.set(key, {
          phoneNumber: key,
          name: getDisplayName(item),
          lastMessage: item.body || item.message || '',
          timestamp: item.timestamp,
          count: 1,
          lastType: item.type,
        });
        return;
      }

      existing.count += 1;
      if ((item.timestamp || 0) >= (existing.timestamp || 0)) {
        existing.lastMessage = item.body || item.message || '';
        existing.timestamp = item.timestamp;
        existing.lastType = item.type;
        existing.name = getDisplayName(item);
      }
    });

    return sortByNewest([...map.values()]);
  }, [filteredMessages]);

  const chatMessages = useMemo(() => {
    if (!selectedContact) {
      return [];
    }

    return sortByNewest(
      filteredMessages.filter(item => String(item.phoneNumber) === selectedContact),
    );
  }, [filteredMessages, selectedContact]);

  const selectedContactName = useMemo(() => {
    if (!selectedContact) {
      return '';
    }

    const match = conversations.find(item => item.phoneNumber === selectedContact);
    return match?.name || selectedContact;
  }, [conversations, selectedContact]);

  const getFilterCount = filterId => {
    if (filterId === 'INBOX') return stats.inbox;
    if (filterId === 'SENT') return stats.sent;
    return stats.total;
  };

  const renderConversation = ({item}) => {
    const sent = isSentMessage(item.lastType);
    const previewPrefix = sent ? 'You: ' : '';

    return (
      <TouchableOpacity
        style={styles.row}
        activeOpacity={0.7}
        onPress={() => setSelectedContact(item.phoneNumber)}>
        <Avatar name={item.name} phoneNumber={item.phoneNumber} size={44} />

        <View style={styles.rowBody}>
          <View style={styles.rowTop}>
            <Text style={styles.rowName} numberOfLines={1}>
              {item.name}
            </Text>
            <Text style={styles.rowTime}>
              {formatRelativeTime(item.timestamp)}
            </Text>
          </View>

          <Text style={styles.rowPreview} numberOfLines={1}>
            {previewPrefix}
            {item.lastMessage || 'No message'}
          </Text>
        </View>

        {item.count > 1 ? (
          <View style={styles.countBadge}>
            <Text style={styles.countBadgeText}>{item.count}</Text>
          </View>
        ) : (
          <AppIcon name="chevron-forward" size={16} color={theme.colors.textMuted} />
        )}
      </TouchableOpacity>
    );
  };

  const renderChatBubble = ({item}) => {
    const sent = isSentMessage(item.type);

    return (
      <View style={[styles.messageRow, sent && styles.messageRowSent]}>
        <View style={[styles.bubble, sent ? styles.bubbleSent : styles.bubbleInbox]}>
          <Text style={[styles.bubbleBody, sent && styles.bubbleBodySent]}>
            {item.body || item.message || 'No message'}
          </Text>
          <Text style={[styles.bubbleTime, sent && styles.bubbleTimeSent]}>
            {formatRelativeTime(item.timestamp)}
          </Text>
        </View>
      </View>
    );
  };

  return (
    <SafeAreaView style={styles.safeArea} edges={['top']}>
      <StatusBar barStyle="dark-content" backgroundColor={theme.colors.white} />

      <ScreenHero
        title={selectedContact ? selectedContactName : 'Messages'}
        subtitle={
          selectedContact
            ? selectedContact
            : `${stats.total} SMS${mongoLive ? ' · Sync ON' : ''}`
        }
        onBack={selectedContact ? () => setSelectedContact(null) : undefined}
        onRefresh={onSwipeRefresh}
      />

      {!selectedContact && (
        <>
          {!permissionGranted && (
            <InfoCard
              icon="shield-outline"
              title="SMS permission chahiye"
              text="Messages dikhane ke liye permission allow karein"
              tone="warning"
            />
          )}

          <FilterChips
            filters={FILTERS}
            activeId={activeFilter}
            onSelect={setActiveFilter}
            getCount={getFilterCount}
          />
        </>
      )}

      <View style={styles.content}>
        {selectedContact ? (
          chatMessages.length === 0 ? (
            <EmptyState
              icon="chatbubbles-outline"
              title="Koi message nahi"
              subtitle="Neeche kheench kar refresh karein"
            />
          ) : (
            <FlatList
              data={chatMessages}
              keyExtractor={item =>
                String(item.smsId || item.id || `${item.phoneNumber}_${item.timestamp}`)
              }
              renderItem={renderChatBubble}
              contentContainerStyle={styles.chatList}
              showsVerticalScrollIndicator={false}
              refreshControl={
                <RefreshControl
                  refreshing={refreshing}
                  onRefresh={onSwipeRefresh}
                  colors={[theme.colors.primary]}
                  tintColor={theme.colors.primary}
                />
              }
              ItemSeparatorComponent={() => <View style={styles.bubbleGap} />}
            />
          )
        ) : conversations.length === 0 ? (
          <EmptyState
            icon="chatbubbles-outline"
            title="Koi SMS nahi"
            subtitle="Phone ke SMS yahan dikhenge"
          />
        ) : (
          <FlatList
            data={conversations}
            keyExtractor={item => item.phoneNumber}
            renderItem={renderConversation}
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
            ItemSeparatorComponent={() => <ListDivider />}
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
  rowTime: {
    fontSize: 12,
    fontFamily: theme.fonts.regular,
    color: theme.colors.textMuted,
  },
  rowPreview: {
    marginTop: 4,
    fontSize: 14,
    fontFamily: theme.fonts.regular,
    color: theme.colors.textMuted,
  },
  countBadge: {
    minWidth: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: theme.colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 6,
    marginLeft: 8,
  },
  countBadgeText: {
    fontSize: 11,
    fontFamily: theme.fonts.medium,
    fontWeight: '600',
    color: theme.colors.textDark,
  },
  chatList: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    paddingBottom: 24,
  },
  messageRow: {
    alignItems: 'flex-start',
  },
  messageRowSent: {
    alignItems: 'flex-end',
  },
  bubble: {
    maxWidth: '80%',
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  bubbleInbox: {
    backgroundColor: theme.colors.bgSoft,
    borderTopLeftRadius: 4,
  },
  bubbleSent: {
    backgroundColor: theme.colors.primary,
    borderTopRightRadius: 4,
  },
  bubbleBody: {
    fontSize: 15,
    fontFamily: theme.fonts.regular,
    lineHeight: 21,
    color: theme.colors.textDark,
  },
  bubbleBodySent: {
    color: theme.colors.textDark,
  },
  bubbleTime: {
    fontSize: 11,
    fontFamily: theme.fonts.regular,
    color: theme.colors.textMuted,
    marginTop: 4,
    alignSelf: 'flex-end',
  },
  bubbleTimeSent: {
    color: 'rgba(26,26,26,0.6)',
  },
  bubbleGap: {
    height: 8,
  },
});

export default MessagesScreen;
