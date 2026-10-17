# Archive libraries use typed APIs; preserve archive metadata for useful errors.
-keepattributes Signature,InnerClasses,EnclosingMethod
-dontwarn org.slf4j.impl.StaticLoggerBinder
# Optional codecs/OSGi metadata are not used by the ZIP/RAR/7z feature set.
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.**
-dontwarn org.objectweb.asm.**
-dontwarn org.osgi.**
-dontwarn javax.annotation.**
