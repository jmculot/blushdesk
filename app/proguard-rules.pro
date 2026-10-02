# Release shrinking is currently disabled (see app/build.gradle.kts). If it is turned on, these keep
# rules are the starting point for Apache POI / XMLBeans, which instantiate schema types reflectively.
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-keep class org.apache.poi.** { *; }
-keep class com.fasterxml.aalto.** { *; }
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.osgi.**
