# JNI: native code looks these up by name (Java_com_strumwise_audio_NativeBridge_*), so R8 must not
# rename or strip them.
-keep class com.strumwise.audio.NativeBridge { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
