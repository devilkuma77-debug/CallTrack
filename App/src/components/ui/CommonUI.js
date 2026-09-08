import React from 'react';
import {
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';

import AppIcon from '../AppIcon';
import {theme} from '../../theme/theme';
import {getAvatarColor, getInitial} from '../../utils/uiHelpers';

export function Avatar({name, phoneNumber, size = 44, style}) {
  const displaySeed = phoneNumber || name || '';
  const color = getAvatarColor(displaySeed);

  return (
    <View
      style={[
        styles.avatar,
        {width: size, height: size, borderRadius: size / 2, backgroundColor: color},
        style,
      ]}>
      <Text style={[styles.avatarText, {fontSize: size * 0.38}]}>
        {getInitial(name, phoneNumber)}
      </Text>
    </View>
  );
}

export function ScreenHero({
  title,
  subtitle,
  onRefresh,
  onBack,
  children,
  stats,
}) {
  return (
    <View style={styles.hero}>
      <View style={styles.heroTop}>
        {onBack ? (
          <TouchableOpacity style={styles.heroIconBtn} onPress={onBack} activeOpacity={0.7}>
            <AppIcon name="arrow-back" size={22} color={theme.colors.textDark} />
          </TouchableOpacity>
        ) : null}

        <View style={styles.heroTextWrap}>
          <Text style={styles.heroTitle} numberOfLines={1}>
            {title}
          </Text>
          {subtitle ? (
            <Text style={styles.heroSubtitle} numberOfLines={1}>
              {subtitle}
            </Text>
          ) : null}
        </View>

        {onRefresh ? (
          <TouchableOpacity style={styles.heroIconBtn} onPress={onRefresh} activeOpacity={0.7}>
            <AppIcon name="refresh" size={20} color={theme.colors.textDark} />
          </TouchableOpacity>
        ) : null}
      </View>

      {stats?.length > 0 ? (
        <View style={styles.heroStatsRow}>
          {stats.map(item => (
            <View key={item.label} style={styles.heroStatBox}>
              <Text style={styles.heroStatNumber}>{item.value}</Text>
              <Text style={styles.heroStatLabel}>{item.label}</Text>
            </View>
          ))}
        </View>
      ) : null}

      {children}
    </View>
  );
}

export function EmptyState({icon, title, subtitle, actionLabel, onAction}) {
  return (
    <View style={styles.emptyBox}>
      <View style={styles.emptyIconWrap}>
        <AppIcon name={icon} size={28} color={theme.colors.textMuted} />
      </View>
      <Text style={styles.emptyTitle}>{title}</Text>
      <Text style={styles.emptySubtitle}>{subtitle}</Text>
      {actionLabel && onAction ? (
        <TouchableOpacity style={styles.emptyButton} onPress={onAction} activeOpacity={0.85}>
          <Text style={styles.emptyButtonText}>{actionLabel}</Text>
        </TouchableOpacity>
      ) : null}
    </View>
  );
}

export function FilterChips({filters, activeId, onSelect, getCount}) {
  return (
    <View style={styles.filterBar}>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.filterScroll}>
        {filters.map(filter => {
          const active = activeId === filter.id;
          const count = getCount ? getCount(filter.id) : null;

          return (
            <TouchableOpacity
              key={filter.id}
              style={[styles.filterChip, active && styles.filterChipActive]}
              onPress={() => onSelect(filter.id)}
              activeOpacity={0.85}>
              <Text style={[styles.filterLabel, active && styles.filterLabelActive]}>
                {filter.label}
                {count != null ? ` (${count})` : ''}
              </Text>
            </TouchableOpacity>
          );
        })}
      </ScrollView>
    </View>
  );
}

export function StatusPill({active, label, sublabel}) {
  return (
    <View style={[styles.statusPill, active ? styles.statusPillOn : styles.statusPillOff]}>
      <View style={[styles.statusDot, active ? styles.statusDotOn : styles.statusDotOff]} />
      <View style={styles.statusTextWrap}>
        <Text style={styles.statusLabel}>{label}</Text>
        {sublabel ? <Text style={styles.statusSublabel}>{sublabel}</Text> : null}
      </View>
    </View>
  );
}

export function SectionHeader({title, count}) {
  return (
    <View style={styles.sectionHeader}>
      <Text style={styles.sectionTitle}>{title}</Text>
      {count != null ? <Text style={styles.sectionCount}>{count}</Text> : null}
    </View>
  );
}

export function InfoCard({icon, title, text, tone = 'info'}) {
  const toneStyle =
    tone === 'warning'
      ? styles.infoCardWarning
      : tone === 'success'
        ? styles.infoCardSuccess
        : styles.infoCardInfo;

  const iconColor =
    tone === 'warning'
      ? theme.colors.warning
      : tone === 'success'
        ? theme.colors.success
        : theme.colors.textDark;

  return (
    <View style={[styles.infoCard, toneStyle]}>
      <AppIcon name={icon} size={18} color={iconColor} />
      <View style={styles.infoCardText}>
        <Text style={styles.infoCardTitle}>{title}</Text>
        <Text style={styles.infoCardBody}>{text}</Text>
      </View>
    </View>
  );
}

export function QuickActionCard({icon, iconColor, iconBg, title, subtitle, hint, onPress}) {
  return (
    <TouchableOpacity style={styles.quickAction} onPress={onPress} activeOpacity={0.88}>
      <View style={[styles.quickActionIcon, {backgroundColor: iconBg || theme.colors.primarySoft}]}>
        <AppIcon name={icon} size={20} color={iconColor || theme.colors.textDark} />
      </View>
      <View style={styles.quickActionBody}>
        <Text style={styles.quickActionTitle}>{title}</Text>
        <Text style={styles.quickActionSubtitle}>{subtitle}</Text>
        {hint ? <Text style={styles.quickActionHint}>{hint}</Text> : null}
      </View>
      <AppIcon name="chevron-forward" size={18} color={theme.colors.textMuted} />
    </TouchableOpacity>
  );
}

export function ListDivider() {
  return <View style={styles.listDivider} />;
}

const styles = StyleSheet.create({
  avatar: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: {
    color: theme.colors.white,
    fontFamily: theme.fonts.bold,
    fontWeight: '700',
  },
  hero: {
    backgroundColor: theme.colors.white,
    paddingHorizontal: 16,
    paddingTop: 4,
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border,
  },
  heroTop: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  heroTextWrap: {
    flex: 1,
  },
  heroTitle: {
    ...theme.typography.title,
  },
  heroSubtitle: {
    marginTop: 2,
    ...theme.typography.caption,
  },
  heroIconBtn: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  heroStatsRow: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 12,
  },
  heroStatBox: {
    flex: 1,
    backgroundColor: theme.colors.bgSoft,
    borderRadius: theme.radius.sm,
    paddingVertical: 8,
    alignItems: 'center',
  },
  heroStatNumber: {
    color: theme.colors.textDark,
    fontSize: 16,
    fontFamily: theme.fonts.bold,
    fontWeight: '700',
  },
  heroStatLabel: {
    marginTop: 2,
    color: theme.colors.textMuted,
    fontSize: 11,
    fontFamily: theme.fonts.regular,
  },
  emptyBox: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 32,
    paddingVertical: 48,
  },
  emptyIconWrap: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: theme.colors.bgSoft,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 12,
  },
  emptyTitle: {
    ...theme.typography.heading,
    textAlign: 'center',
  },
  emptySubtitle: {
    marginTop: 6,
    ...theme.typography.body,
    textAlign: 'center',
    lineHeight: 20,
  },
  emptyButton: {
    marginTop: 16,
    backgroundColor: theme.colors.primary,
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: theme.radius.full,
  },
  emptyButtonText: {
    color: theme.colors.textDark,
    fontSize: 14,
    fontFamily: theme.fonts.medium,
    fontWeight: '600',
  },
  filterBar: {
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border,
  },
  filterScroll: {
    paddingHorizontal: 12,
    paddingVertical: 0,
  },
  filterChip: {
    paddingHorizontal: 14,
    paddingVertical: 12,
    marginRight: 4,
    borderBottomWidth: 2,
    borderBottomColor: 'transparent',
  },
  filterChipActive: {
    borderBottomColor: theme.colors.primary,
  },
  filterLabel: {
    fontSize: 14,
    fontFamily: theme.fonts.regular,
    color: theme.colors.textMuted,
  },
  filterLabelActive: {
    fontFamily: theme.fonts.medium,
    fontWeight: '600',
    color: theme.colors.textDark,
  },
  statusPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: theme.radius.sm,
    backgroundColor: theme.colors.bgSoft,
  },
  statusPillOn: {
    backgroundColor: theme.colors.primarySoft,
  },
  statusPillOff: {
    backgroundColor: theme.colors.bgSoft,
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  statusDotOn: {
    backgroundColor: theme.colors.green,
  },
  statusDotOff: {
    backgroundColor: theme.colors.muted,
  },
  statusTextWrap: {
    flex: 1,
  },
  statusLabel: {
    fontSize: 14,
    fontFamily: theme.fonts.medium,
    fontWeight: '600',
    color: theme.colors.textDark,
  },
  statusSublabel: {
    fontSize: 12,
    fontFamily: theme.fonts.regular,
    marginTop: 2,
    color: theme.colors.textMuted,
  },
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingTop: 16,
    paddingBottom: 8,
    backgroundColor: theme.colors.bgSoft,
  },
  sectionTitle: {
    fontSize: 13,
    fontFamily: theme.fonts.medium,
    fontWeight: '600',
    color: theme.colors.textMuted,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  sectionCount: {
    fontSize: 12,
    fontFamily: theme.fonts.regular,
    color: theme.colors.textMuted,
  },
  infoCard: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 10,
    padding: 12,
    borderRadius: theme.radius.sm,
    marginHorizontal: 16,
    marginVertical: 8,
  },
  infoCardInfo: {
    backgroundColor: theme.colors.primarySoft,
  },
  infoCardWarning: {
    backgroundColor: theme.colors.warningBg,
  },
  infoCardSuccess: {
    backgroundColor: theme.colors.greenSoft,
  },
  infoCardText: {
    flex: 1,
  },
  infoCardTitle: {
    fontSize: 14,
    fontFamily: theme.fonts.medium,
    fontWeight: '600',
    color: theme.colors.textDark,
    marginBottom: 2,
  },
  infoCardBody: {
    fontSize: 13,
    fontFamily: theme.fonts.regular,
    lineHeight: 18,
    color: theme.colors.text,
  },
  quickAction: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: theme.colors.white,
    paddingVertical: 14,
    paddingHorizontal: 4,
    marginBottom: 4,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border,
  },
  quickActionIcon: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  quickActionBody: {
    flex: 1,
  },
  quickActionTitle: {
    fontSize: 16,
    fontFamily: theme.fonts.medium,
    fontWeight: '600',
    color: theme.colors.textDark,
  },
  quickActionSubtitle: {
    marginTop: 2,
    fontSize: 13,
    fontFamily: theme.fonts.regular,
    color: theme.colors.textMuted,
  },
  quickActionHint: {
    marginTop: 2,
    fontSize: 12,
    fontFamily: theme.fonts.regular,
    color: theme.colors.textMuted,
  },
  listDivider: {
    height: 1,
    backgroundColor: theme.colors.border,
    marginLeft: 72,
  },
});
