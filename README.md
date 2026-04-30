# Scypheon Android

## Environment Setup for Agent

The setup script below installs Java 17, Android SDK, Android NDK, and CMake needed to build this Android project.

### Setup script
```bash
apt-get update && apt-get install -y openjdk-17-jdk wget unzip
mkdir -p /opt/android-sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11479570_latest.zip -O /tmp/cmdline-tools.zip
unzip -q /tmp/cmdline-tools.zip -d /opt/android-sdk/cmdline-tools
mv /opt/android-sdk/cmdline-tools/cmdline-tools /opt/android-sdk/cmdline-tools/latest
export ANDROID_HOME=/opt/android-sdk
yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses
/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;29.0.13113456" "cmake;3.22.1"
```

### Environment variables
```
ANDROID_HOME=/opt/android-sdk
```
