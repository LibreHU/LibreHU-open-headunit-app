#!/bin/sh
# LibreHU fork: runs RegexCheck (dex $1) on the running emulator through app_process, as root when the image allows it
# (Android 4.x's Dalvik writes its dex cache), and fails when a pattern of today's sources does not pass.
adb root >/dev/null 2>&1
sleep 3
adb wait-for-device
adb push "$1" /data/local/tmp/regexcheck.dex >/dev/null
adb shell "CLASSPATH=/data/local/tmp/regexcheck.dex app_process /system/bin RegexCheck" | tr -d '\r' | tee result.txt
if [ -n "$GITHUB_STEP_SUMMARY" ]; then
    { echo "### Android API $API"; echo '```'; grep -E "^(sdk|FAIL|RESULT)" result.txt; echo '```'; } >> "$GITHUB_STEP_SUMMARY"
fi
grep -q "^RESULT new_fail=0 " result.txt
