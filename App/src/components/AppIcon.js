import React from 'react';
import Ionicons from 'react-native-vector-icons/Ionicons';

function AppIcon({name, size = 20, color = '#000', style, ...props}) {
  return (
    <Ionicons name={name} size={size} color={color} style={style} {...props} />
  );
}

export default AppIcon;
