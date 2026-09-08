/**
 * RN CLI ko activity-alias se MainActivity detect karne ke liye patch.
 * Bina iske `npx react-native run-android` com.calltech/com.calltech launch karta hai.
 */
const fs = require('fs');
const path = require('path');

const MARKER = 'CALLTECH_PATCH_ACTIVITY_ALIAS';
const FALLBACK_MARKER = 'CALLTECH_FALLBACK_MAIN';
const targetFile = path.join(
  __dirname,
  '..',
  'node_modules',
  '@react-native-community',
  'cli-config-android',
  'build',
  'config',
  'getMainActivity.js',
);

if (!fs.existsSync(targetFile)) {
  console.log('patch-rn-android-cli: cli-config-android not found, skip');
  process.exit(0);
}

let content = fs.readFileSync(targetFile, 'utf8');
if (content.includes(MARKER) && content.includes(FALLBACK_MARKER)) {
  process.exit(0);
}

if (content.includes(MARKER) && content.includes("return null;\n    } else")) {
  content = content.replace(
    "      return null;\n    } else {",
    "      return '.MainActivity';\n    } else {",
  );
  fs.writeFileSync(targetFile, content);
  console.log('patch-rn-android-cli: MainActivity fallback added');
  process.exit(0);
}

if (content.includes(MARKER)) {
  process.exit(0);
}

const oldReturn = "      return mainActivity ? mainActivity['@_android:name'] : null;";
const newReturn = `      if (mainActivity) {
        return mainActivity['@_android:name'];
      }
      // ${MARKER}
      const alias = application['activity-alias'] || {};
      let aliases = [];
      if (!Array.isArray(alias)) {
        aliases = alias && Object.keys(alias).length ? [alias] : [];
      } else {
        aliases = alias;
      }
      const launcherAlias = aliases.find(act => {
        let intentFilters = act['intent-filter'];
        if (!intentFilters) {
          return false;
        }
        if (!Array.isArray(intentFilters)) {
          intentFilters = [intentFilters];
        }
        return intentFilters.find(intentFilter => {
          const {action, category} = intentFilter;
          if (!action || !category) {
            return false;
          }
          let actions;
          let categories;
          if (!Array.isArray(action)) {
            actions = [action];
          } else {
            actions = action;
          }
          if (!Array.isArray(category)) {
            categories = [category];
          } else {
            categories = category;
          }
          if (actions && categories) {
            const parsedActions = actions.map(({'@_android:name': name}) => name);
            const parsedCategories = categories.map(({'@_android:name': name}) => name);
            return parsedActions.includes(MAIN_ACTION) && parsedCategories.includes(LAUNCHER);
          }
          return false;
        });
      });
      if (launcherAlias) {
        return launcherAlias['@_android:targetActivity'] || launcherAlias['@_android:name'];
      }
      return '.MainActivity'; // ${FALLBACK_MARKER}`;

if (!content.includes(oldReturn)) {
  console.warn('patch-rn-android-cli: getMainActivity.js format changed — patch skipped');
  process.exit(0);
}

content = content.replace(oldReturn, newReturn);
fs.writeFileSync(targetFile, content);
console.log('patch-rn-android-cli: getMainActivity patched');
