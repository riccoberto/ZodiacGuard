# ZodiacGuard

App Android per registrare entrate e uscite di lavoro, note giornaliere e attivita,
con riepiloghi settimanali e mensili, esportazione PDF, backup e marcatura rapida NFC.

## Build

Servono JDK 17 e Android SDK Platform 34 con Build Tools 34.0.0.

```powershell
.\gradlew.bat build
```

L'APK debug viene generato in `app/build/outputs/apk/debug/app-debug.apk`.
Il workflow GitHub Actions installa automaticamente i componenti SDK richiesti
e pubblica l'APK come artifact.
