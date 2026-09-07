# Die App laedt ihre Daten aus JSON-Assets ueber org.json (Framework-API).
# Es gibt keine Reflection auf Modellklassen, daher sind keine keep-Regeln
# fuer das Datenmodell noetig.

# Compose bringt eigene Regeln mit (consumer rules der Bibliothek).
-dontwarn org.jetbrains.annotations.**
