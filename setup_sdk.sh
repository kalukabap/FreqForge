#!/bin/bash
set -e
export ANDROID_HOME=/home/codespace/android-sdk
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

# Only install if not already there
if [ ! -f "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
    echo "Downloading cmdline-tools..."
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdline-tools.zip
    mkdir -p $ANDROID_HOME/cmdline-tools
    unzip -q -o /tmp/cmdline-tools.zip -d $ANDROID_HOME/cmdline-tools/
    mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest 2>/dev/null || true
    rm /tmp/cmdline-tools.zip
fi

# Accept licenses
mkdir -p $ANDROID_HOME/licenses
echo -e "\n24333f8a63b6825ea9c5514f83c2829b004d1fee" > $ANDROID_HOME/licenses/android-sdk-license
echo -e "\n84831b9409646a918e30573bab4c9c91346d8abd" >> $ANDROID_HOME/licenses/android-sdk-license

# Check what's installed
if [ ! -d "$ANDROID_HOME/platforms/android-36" ]; then
    echo "Installing SDK platforms..."
    yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-36" "build-tools;36.0.0" > /dev/null 2>&1
fi

echo "SDK setup complete"
echo "sdk.dir=$ANDROID_HOME" > /workspaces/FreqForge/local.properties
chmod +x /workspaces/FreqForge/gradlew
echo "local.properties created at /workspaces/FreqForge/local.properties"
cat /workspaces/FreqForge/local.properties
