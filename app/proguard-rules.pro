# Release builds are shrunk with R8 (app/build.gradle.kts). Everything below is for Apache POI,
# which writes the Excel export. POI is a desktop-Java library, so it needs two kinds of rules.
# POI's own code is otherwise left to R8: the export reaches it through ordinary calls, so R8
# keeps just what the export uses (about 4 MB less than keeping all of POI).
#
# Check any change here on a release build: export from the app and open the workbook. A missing
# rule shows up only at runtime, as "Couldn't create the Excel file" with the cause in logcat.

# --- 1. Keep what is loaded by name at runtime -------------------------------------------------
# XMLBeans builds every workbook part from schema types that it looks up by class name, starting
# from the TypeSystemHolder in org.apache.poi.schemas and the .xsb files beside it. The generated
# interfaces and their *Impl classes are only ever reached that way, so R8 cannot see them.
-keep class org.apache.xmlbeans.** { *; }
-keep class org.apache.poi.schemas.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-keep class com.microsoft.schemas.** { *; }
-keep class org.etsi.uri.** { *; }
-keep class org.w3.x2000.** { *; }

# These POI classes read data files stored next to them in the jar, by a name relative to their
# own package. If R8 renames or moves them, the lookup misses. The sheets' auto-filters reach
# FunctionMetadataReader through POI's formula parser ("resource 'functionMetadata.txt' not found").
-keepnames class org.apache.poi.ss.formula.function.FunctionMetadataReader
-keepnames class org.apache.poi.xssf.usermodel.XSSFBuiltinTableStyle
-keepnames class org.apache.poi.hssf.usermodel.StaticFontMetrics

# --- 2. Optional features whose libraries the app does not bundle ------------------------------
# POI and its dependencies reference these, but only from features the export never uses. They
# are missing from the debug build in exactly the same way.
# Desktop Java (AWT, Swing, Image I/O, StAX); Android has none of them.
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn javax.xml.stream.**
# Digital signatures and encryption of OOXML files.
-dontwarn javax.xml.crypto.**
-dontwarn org.apache.jcp.xml.dsig.**
-dontwarn org.apache.xml.security.**
-dontwarn org.bouncycastle.**
-dontwarn org.ietf.jgss.**
-dontwarn org.w3c.dom.events.**
-dontwarn org.w3c.dom.traversal.**
# Rendering slides and images to SVG or PDF.
-dontwarn org.apache.batik.**
-dontwarn org.w3c.dom.svg.**
-dontwarn org.apache.pdfbox.**
-dontwarn de.rototor.pdfbox.**
# The XMLBeans schema compiler, its XPath engine and build-tool plugins.
-dontwarn com.github.javaparser.**
-dontwarn net.sf.saxon.**
-dontwarn org.apache.tools.ant.**
-dontwarn org.apache.maven.**
-dontwarn com.sun.org.apache.xml.internal.resolver.**
# Compression formats POI's ZIP handling never meets in an .xlsx.
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
# Logging back ends and OSGi; POI logs through log4j-api, which falls back to a no-op logger.
-dontwarn org.apache.logging.log4j.**
-dontwarn org.osgi.**
# poi-ooxml-lite ships only the schema classes POI itself needs; some of those still mention
# Word, PowerPoint, VML and signature types that were left out.
-dontwarn org.openxmlformats.schemas.**
-dontwarn com.microsoft.schemas.**
-dontwarn org.etsi.uri.**
-dontwarn org.w3.x2000.**
