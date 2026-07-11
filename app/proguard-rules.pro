# Minification is disabled (isMinifyEnabled = false). If you enable it, Media3
# uses reflection for session/Auto plumbing — add keep rules and re-test Android Auto
# on the Desktop Head Unit before shipping.
-keep class androidx.media3.** { *; }
