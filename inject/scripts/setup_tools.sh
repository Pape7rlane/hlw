#!/bin/bash
# 下载构建所需工具（smali/baksmali/r8/android.jar）
set -e
mkdir -p /tmp/tools && cd /tmp/tools
dl() { [ -f "$1" ] || python3 - "$2" "$1" <<'PY'
import urllib.request, sys
urllib.request.urlretrieve(sys.argv[1], sys.argv[2])
PY
}
dl baksmali.jar   https://repo1.maven.org/maven2/org/smali/baksmali/2.5.2/baksmali-2.5.2.jar
dl smali.jar      https://repo1.maven.org/maven2/org/smali/smali/2.5.2/smali-2.5.2.jar
dl r8.jar         https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.3.37/r8-8.3.37.jar
dl android.jar    https://github.com/Sable/android-platforms/raw/master/android-30/android.jar
echo "工具就绪: $(ls -1 /tmp/tools)"
