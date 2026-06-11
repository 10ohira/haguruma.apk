# Build Setup

## Generate Gradle Wrapper
On a machine with Gradle installed:
```bash
cd app
gradle wrapper --gradle-version 8.4
```
This creates gradlew, gradlew.bat, and gradle-wrapper.jar.

## Build APK
```bash
npm install
npm run fetch    # downloads frida-inject binaries
npm run build    # compiles and builds APK
```
