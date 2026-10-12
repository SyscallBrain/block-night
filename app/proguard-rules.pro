# kotlinx.serialization and Compose ship their own consumer rules; the app only uses
# generated serializers (Type.serializer()), so no extra keep rules are needed.

# Keep readable stack traces in the local crash log.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
