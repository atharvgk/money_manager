# Same as env.ps1 for Git Bash. Usage:  source tools/env.sh
JDK_DIR=$(ls -d "$USERPROFILE"/.jdks/jdk-17* 2>/dev/null | head -1)
[ -n "$JDK_DIR" ] && export JAVA_HOME="$JDK_DIR" || echo "JDK 17 not found in ~/.jdks"
export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"
export PATH="$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH"
echo "JAVA_HOME=$JAVA_HOME"; echo "ANDROID_HOME=$ANDROID_HOME"; command -v adb
