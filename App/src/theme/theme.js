import {Platform} from 'react-native';

/** CallTech — Rapido-style clean white UI */
export const colors = {
  white: '#FFFFFF',
  bg: '#FFFFFF',
  bgSoft: '#F7F7F7',
  card: '#FFFFFF',
  ink: '#1A1A1A',
  inkSoft: '#424242',
  muted: '#9E9E9E',
  border: '#EEEEEE',
  borderLight: '#F5F5F5',
  primary: '#FFCA08',
  primaryDark: '#E6B600',
  primarySoft: '#FFF8E1',
  accent: '#FFCA08',
  accentSoft: '#FFF8E1',
  green: '#2E7D32',
  greenSoft: '#E8F5E9',
  red: '#D32F2F',
  redSoft: '#FFEBEE',
  orange: '#F57C00',
  orangeSoft: '#FFF3E0',
};

const fontRegular = Platform.select({
  android: 'Roboto',
  ios: 'System',
  default: 'System',
});

export const fonts = {
  regular: fontRegular,
  medium: fontRegular,
  bold: fontRegular,
  display: fontRegular,
  body: fontRegular,
};

export const theme = {
  colors: {
    bg: colors.bg,
    card: colors.card,

    primary: colors.primary,
    primaryDark: colors.primaryDark,
    primarySoft: colors.primarySoft,
    primaryGradient: [colors.primary, colors.primaryDark],
    accent: colors.accent,
    accentSoft: colors.accentSoft,

    textDark: colors.ink,
    text: colors.inkSoft,
    textMuted: colors.muted,

    border: colors.border,

    incoming: colors.green,
    incomingBg: colors.greenSoft,

    outgoing: '#1565C0',
    outgoingBg: '#E3F2FD',

    missed: colors.red,
    missedBg: colors.redSoft,

    spam: colors.orange,
    spamBg: colors.orangeSoft,

    unknown: colors.muted,
    unknownBg: colors.bgSoft,

    white: colors.white,
    success: colors.green,
    warning: colors.orange,
    warningBg: colors.orangeSoft,

    tabActive: colors.ink,
    tabInactive: colors.muted,
    splashBg: colors.white,
  },

  fonts,

  spacing: {
    xs: 4,
    sm: 8,
    md: 12,
    lg: 16,
    xl: 20,
    xxl: 28,
  },

  radius: {
    sm: 8,
    md: 12,
    lg: 16,
    xl: 20,
    full: 999,
  },

  shadow: {
    card: {
      elevation: 0,
      shadowColor: 'transparent',
      shadowOffset: {width: 0, height: 0},
      shadowOpacity: 0,
      shadowRadius: 0,
    },
    soft: {
      elevation: 1,
      shadowColor: '#000',
      shadowOffset: {width: 0, height: 1},
      shadowOpacity: 0.04,
      shadowRadius: 3,
    },
  },

  typography: {
    title: {
      fontSize: 20,
      fontWeight: '700',
      fontFamily: fonts.bold,
      color: colors.ink,
    },
    heading: {
      fontSize: 16,
      fontWeight: '600',
      fontFamily: fonts.medium,
      color: colors.ink,
    },
    body: {
      fontSize: 14,
      fontWeight: '400',
      fontFamily: fonts.regular,
      color: colors.inkSoft,
    },
    caption: {
      fontSize: 12,
      fontWeight: '400',
      fontFamily: fonts.regular,
      color: colors.muted,
    },
    label: {
      fontSize: 13,
      fontWeight: '600',
      fontFamily: fonts.medium,
      color: colors.inkSoft,
    },
  },
};

export const COLORS = theme.colors;

export const AVATAR_PALETTE = [
  '#FFCA08',
  '#1565C0',
  '#2E7D32',
  '#6A1B9A',
  '#F57C00',
  '#00838F',
];
