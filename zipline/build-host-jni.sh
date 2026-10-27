#!/usr/bin/env bash
# Build the checked-in JNI/QuickJS sources for this Linux JVM host.
# The release cross-platform build remains build.zig; this requires gcc and g++.
set -euo pipefail
cd "$(dirname "$0")"
case "$(uname -s):$(uname -m)" in
  Linux:aarch64) jni_arch=aarch64 ;;
  Linux:x86_64) jni_arch=amd64 ;;
  *) echo "build-host-jni.sh supports Linux aarch64 and x86_64" >&2; exit 1 ;;
esac
out="$(pwd)/build/host-jni/$jni_arch"
mkdir -p "$out"
version=$(cat native/quickjs/VERSION)
for src in native/common/context-no-eval.c native/common/finalization-registry.c native/common/global-gc.c native/quickjs/cutils.c native/quickjs/dtoa.c native/quickjs/libregexp.c native/quickjs/libunicode.c native/quickjs/quickjs.c; do
  "${CC:-gcc}" -O2 -fPIC -std=gnu99 -DCONFIG_VERSION="\"$version\"" -Inative/include/share -Inative/include/unix -c "$src" -o "$out/$(basename "$src").o"
done
for src in native/Context.cpp native/ExceptionThrowers.cpp native/InboundCallChannel.cpp native/OutboundCallChannel.cpp native/quickjs-jni.cpp; do
  "${CXX:-g++}" -O2 -fPIC -std=c++11 -Inative/include/share -Inative/include/unix -c "$src" -o "$out/$(basename "$src").o"
done
mkdir -p "src/jvmMain/resources/jni/$jni_arch"
"${CXX:-g++}" -shared "$out"/*.o -lm -ldl -pthread -o "src/jvmMain/resources/jni/$jni_arch/libquickjs.so"
