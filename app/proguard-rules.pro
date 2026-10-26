# Strumwise R8 rules. JNI rules for the audio engine live in core-audio/consumer-rules.pro
# and are applied automatically to this app.

# kotlinx.serialization: keep generated serializers for song charts and nav routes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
