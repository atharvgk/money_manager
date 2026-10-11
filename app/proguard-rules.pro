# Release build only. Room, WorkManager, Compose and Vico ship their own keep rules.
# Workers are created by class name from WorkManager's database.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
