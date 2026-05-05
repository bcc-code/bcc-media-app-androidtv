#!/usr/bin/env bash
#
# Builds the signed release AAB on Semaphore CI.
#
# Expects the following to be available:
#   - Working directory: repo root
#   - $ANDROID_HOME    : path where the Android SDK is installed (or will be)
#   - Secrets exported as env vars:
#       $KEYSTORE_FILE, $KEYSTORE_PASSWORD, $KEY_ALIAS, $KEY_PASSWORD,
#       $NPAW_ACCOUNT_CODE,
#       $RUDDERSTACK_WRITE_KEY, $RUDDERSTACK_DATA_PLANE_URL,
#       $SENTRY_DSN
#   - fastlane/google-play-service-account.json already in place
#
# Cache restore/store and artifact upload are handled by the caller (YAML).

set -euo pipefail

# Compute the next Play Store version code.
bundle install
bundle exec fastlane next_version_code
BUILD_NUMBER="$(cat fastlane/.next_version_code)"
echo "Using versionCode=$BUILD_NUMBER"
test -n "$BUILD_NUMBER" && test "$BUILD_NUMBER" -gt 0
export BUILD_NUMBER

# Decode the release keystore from the base64-encoded secret.
keystore_path="$(pwd)/release.keystore"
echo "$KEYSTORE_FILE" | base64 -d > "$keystore_path"

# Append signing + analytics config consumed by the Gradle build.
cat >> local.properties << EOF
signing.storeFile=$keystore_path
signing.storePassword=$KEYSTORE_PASSWORD
signing.keyAlias=$KEY_ALIAS
signing.keyPassword=$KEY_PASSWORD
npaw.accountCode=$NPAW_ACCOUNT_CODE
rudderstack.writeKey=$RUDDERSTACK_WRITE_KEY
rudderstack.dataPlaneUrl=$RUDDERSTACK_DATA_PLANE_URL
sentry.dsn=$SENTRY_DSN
EOF

echo "NPAW_ACCOUNT_CODE is $([ -n "$NPAW_ACCOUNT_CODE" ] && echo 'set' || echo 'EMPTY')"
grep npaw local.properties | sed 's/=.*/=***/'

# Install Android SDK components if not already cached.
if [ ! -d "$ANDROID_HOME/platforms/android-36" ]; then
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  curl -sSL -o /tmp/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q /tmp/cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null
  "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-36" "build-tools;36.0.0"
fi

# Build the signed App Bundle.
./gradlew bundleRelease -PbuildNumber="$BUILD_NUMBER" --no-daemon --console=plain --warning-mode=summary
