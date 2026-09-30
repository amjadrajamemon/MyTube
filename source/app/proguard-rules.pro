# TubeDeck ProGuard rules


# NewPipe Extractor runtime classes. See THIRD_PARTY_NOTICES.md.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter { *; }
-dontwarn org.mozilla.javascript.tools.**
