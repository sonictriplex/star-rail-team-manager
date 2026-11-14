# R8-Regeln für den Release-Build.
# kotlinx.serialization, OkHttp und Compose bringen ihre eigenen Regeln mit. Hier steht nur, was die App
# selbst braucht. Fehlende Klassen zeigen sich im Staging-Build (./gradlew :app:assembleStaging).

# Datenklassen, die per JSON gelesen und geschrieben werden (Austauschformat, Stammdaten, API-Antworten)
-keep,includedescriptorclasses class de.sonictriplex.starrailteams.data.**$$serializer { *; }
-keepclassmembers class de.sonictriplex.starrailteams.data.** {
    *** Companion;
}
-keepclasseswithmembers class de.sonictriplex.starrailteams.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
