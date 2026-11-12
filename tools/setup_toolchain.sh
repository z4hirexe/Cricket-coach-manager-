#!/usr/bin/env bash
# Installs JDK 17 + Android SDK 34 into ~/.cache/dl (used for local builds in this sandbox).
set -e
D=$HOME/.cache/dl; mkdir -p $D; cd $D
if [ ! -x jdk17/bin/java ]; then
  curl -sSL -o jdk17.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
  mkdir -p jdk17 && tar -xzf jdk17.tar.gz -C jdk17 --strip-components=1 && rm jdk17.tar.gz
fi
export JAVA_HOME=$D/jdk17
if [ ! -d sdk/platforms/android-34 ]; then
  curl -sSL -o cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  mkdir -p sdk/cmdline-tools && unzip -q -o cmdtools.zip -d sdk/cmdline-tools && rm cmdtools.zip
  rm -rf sdk/cmdline-tools/latest && mv sdk/cmdline-tools/cmdline-tools sdk/cmdline-tools/latest
  yes | sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=$D/sdk --licenses >/dev/null
  sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=$D/sdk "platforms;android-34" "build-tools;34.0.0" "platform-tools" >/dev/null
fi
echo TOOLCHAIN_OK
