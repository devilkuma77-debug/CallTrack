/**
 * RN CLI: trampoline start WITHOUT LAUNCHER category.
 * LAUNCHER start ColorOS drawer mein CallTech icon laga deta hai.
 */
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..', 'node_modules', '@react-native-community');

function patchGetMainActivity() {
  const targetFile = path.join(root, 'cli-config-android', 'build', 'config', 'getMainActivity.js');
  if (!fs.existsSync(targetFile)) {
    console.log('patch-rn-android-cli: getMainActivity.js not found, skip');
    return;
  }

  let content = fs.readFileSync(targetFile, 'utf8');
  if (content.includes("return '.PermissionTrampolineActivity'")) {
    return;
  }

  content = content.replace(
    "return '.MainActivity'; // CALLTECH_FALLBACK_MAIN",
    "return '.PermissionTrampolineActivity'; // CALLTECH_FALLBACK_MAIN",
  );
  content = content.replace(
    "return '.MainActivity';",
    "return '.PermissionTrampolineActivity';",
  );

  if (!content.includes("return '.PermissionTrampolineActivity'")) {
    const oldReturn = "      return mainActivity ? mainActivity['@_android:name'] : null;";
    if (content.includes(oldReturn)) {
      content = content.replace(
        oldReturn,
        "      return mainActivity ? mainActivity['@_android:name'] : '.PermissionTrampolineActivity';",
      );
    }
  }

  fs.writeFileSync(targetFile, content);
  console.log('patch-rn-android-cli: trampoline activity fallback');
}

function patchTryLaunch() {
  const targetFile = path.join(
    root,
    'cli-platform-android',
    'build',
    'commands',
    'runAndroid',
    'tryLaunchAppOnDevice.js',
  );
  if (!fs.existsSync(targetFile)) {
    console.log('patch-rn-android-cli: tryLaunchAppOnDevice.js not found, skip');
    return;
  }

  let content = fs.readFileSync(targetFile, 'utf8');
  if (content.includes('CALLTECH_PATCH_NO_LAUNCHER')) {
    return;
  }

  const oldArgs =
    "const adbArgs = ['shell', 'am', 'start', '-n', `${applicationIdWithSuffix}/${activityToLaunch}`, '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.LAUNCHER'];";
  const newArgs =
    "const adbArgs = ['shell', 'am', 'start', '-n', `${applicationIdWithSuffix}/${activityToLaunch}`, '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.DEFAULT']; // CALLTECH_PATCH_NO_LAUNCHER";

  if (!content.includes(oldArgs)) {
    content = content.replace(
      "'android.intent.category.LAUNCHER'",
      "'android.intent.category.DEFAULT' /* CALLTECH_PATCH_NO_LAUNCHER */",
    );
  } else {
    content = content.replace(oldArgs, newArgs);
  }

  if (!content.includes('CALLTECH_PATCH_NO_LAUNCHER')) {
    console.warn('patch-rn-android-cli: tryLaunchAppOnDevice format changed — patch skipped');
    return;
  }

  fs.writeFileSync(targetFile, content);
  console.log('patch-rn-android-cli: start uses DEFAULT, not LAUNCHER');
}

patchGetMainActivity();
patchTryLaunch();
