import React from 'react';
import {ScrollView, StyleSheet, Text, TouchableOpacity, View} from 'react-native';

import {theme} from '../theme/theme';

class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = {error: null};
  }

  static getDerivedStateFromError(error) {
    return {error};
  }

  componentDidCatch(error, info) {
    console.log('App crash:', error?.message, info?.componentStack);
  }

  handleRetry = () => {
    this.setState({error: null});
  };

  render() {
    if (this.state.error) {
      return (
        <View style={styles.wrap}>
          <Text style={styles.title}>App load nahi ho payi</Text>
          <ScrollView style={styles.scroll} contentContainerStyle={styles.scrollContent}>
            <Text style={styles.message}>
              {String(this.state.error?.message || this.state.error)}
            </Text>
          </ScrollView>
          <TouchableOpacity style={styles.button} onPress={this.handleRetry} activeOpacity={0.85}>
            <Text style={styles.buttonText}>Dubara try karein</Text>
          </TouchableOpacity>
        </View>
      );
    }

    return this.props.children;
  }
}

const styles = StyleSheet.create({
  wrap: {
    flex: 1,
    backgroundColor: theme.colors.bg,
    padding: 24,
    justifyContent: 'center',
  },
  title: {
    fontSize: 20,
    fontWeight: '800',
    color: theme.colors.textDark,
    marginBottom: 12,
    textAlign: 'center',
  },
  scroll: {
    maxHeight: 220,
    marginBottom: 20,
  },
  scrollContent: {
    padding: 12,
    backgroundColor: theme.colors.card,
    borderRadius: theme.radius.lg,
    borderWidth: 1,
    borderColor: theme.colors.border,
  },
  message: {
    fontSize: 13,
    lineHeight: 20,
    color: theme.colors.text,
  },
  button: {
    alignSelf: 'center',
    backgroundColor: theme.colors.accent,
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: theme.radius.full,
  },
  buttonText: {
    color: theme.colors.white,
    fontSize: 14,
    fontWeight: '700',
  },
});

export default ErrorBoundary;
