# Wonderdesk: optimise everything, but keep our own classes and readable names for crash reports.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-keep,allowoptimization class dev.wonderdesk.** { *; }
-dontwarn org.slf4j.**
-dontwarn javax.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn com.google.errorprone.**
-dontwarn org.ietf.jgss.**
# SMB (Wayfinder network shares) finds its handlers by reflection.
-keep class com.hierynomus.** { *; }
-keep class net.engio.** { *; }
-keepattributes RuntimeVisibleAnnotations
