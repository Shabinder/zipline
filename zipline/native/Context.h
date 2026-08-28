/*
 * Copyright (C) 2019 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#ifndef QUICKJS_ANDROID_CONTEXT_H
#define QUICKJS_ANDROID_CONTEXT_H

#include <jni.h>
#include <string>
#include <vector>
#include <unordered_map>
#include "quickjs/quickjs.h"

class JSRuntime;
class JSContext;
class InboundCallChannel;

class Context {
public:
  Context(JNIEnv *env);
  ~Context();

  InboundCallChannel* getInboundCallChannel(JNIEnv*, jstring name);
  void setOutboundCallChannel(JNIEnv*, jstring name, jobject callChannel);
  jobject execute(JNIEnv*, jbyteArray byteCode);
  jbyteArray compile(JNIEnv*, jstring source, jstring file);
  void setInterruptHandler(JNIEnv* env, jobject interruptHandler);
  jobject memoryUsage(JNIEnv*);
  void setMemoryLimit(JNIEnv* env, jlong limit);
  void setGcThreshold(JNIEnv* env, jlong gcThreshold);
  void gc(JNIEnv* env);
  void setMaxStackSize(JNIEnv* env, jlong stackSize);
  jint executePendingJobs(JNIEnv* env);

  jobject toJavaObject(JNIEnv*, const JSValue& value, bool throwOnUnsupportedType = true);
  void throwJsException(JNIEnv*, const JSValue& value) const;
  JSValue throwJavaExceptionFromJs(JNIEnv*) const;

  JNIEnv* getEnv() const;

  std::string toCppString(JNIEnv* env, jstring string) const;
  JSValue toJsString(JNIEnv* env, jstring string) const;
  jstring toJavaString(JNIEnv* env, const JSValueConst& value) const;

  /**
   * Copies a Java byte[][] into the engine as a JavaScript array of Int8Array, one memcpy each.
   *
   * JS_NewArrayBufferCopy allocates and copies in C, so the engine owns what it collects and the
   * JVM array can be released immediately. That single copy is the point: as text the same bytes
   * cost a transcode, a 1.33-3.57x inflation, and a parse.
   */
  JSValue toJsInt8ArrayArray(JNIEnv* env, jobjectArray buffers) const;

  /**
   * Copies a JavaScript array of typed arrays back out, honouring each view's byteOffset and
   * byteLength.
   *
   * Reading `.buffer` alone would be wrong: Kotlin/JS ByteArray *is* an Int8Array, and one may be
   * a view into a larger buffer. A detached buffer yields no data and raises rather than crashes.
   */
  jobjectArray toJavaByteArrayArray(JNIEnv* env, const JSValueConst& value) const;

  JavaVM* javaVm;
  const jint jniVersion;
  JSRuntime *jsRuntime;
  JSContext *jsContext;
  JSContext *jsContextForCompiling;
  JSClassID outboundCallChannelClassId;
  JSAtom lengthAtom;
  JSAtom callAtom;
  JSAtom takeResultBuffersAtom;
  JSAtom disconnectAtom;
  jclass booleanClass;
  jclass integerClass;
  jclass doubleClass;
  jclass objectClass;
  jclass stringClass;
  jclass memoryUsageClass;
  jstring stringUtf8;
  jclass quickJsExceptionClass;
  jmethodID booleanValueOf;
  jmethodID integerValueOf;
  jmethodID doubleValueOf;
  jmethodID stringGetBytes;
  jmethodID stringConstructor;
  jmethodID memoryUsageConstructor;
  jmethodID quickJsExceptionConstructor;
  jclass interruptHandlerClass;
  jmethodID interruptHandlerPoll;
  jobject interruptHandler;
  std::vector<InboundCallChannel*> callChannels;
  std::unordered_map<std::string, jclass> globalReferences;
};

#endif //QUICKJS_ANDROID_CONTEXT_H
