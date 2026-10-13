# Koppiy 1.3 — senin adımların

1. GitHub'da koppiy deposunda `app/koppiy.keystore` dosyasını sil (eski anahtar, depo herkese açık).
2. Zip'teki dosyaları depoya yükle (`.github` klasörünü Add file ile elle oluştur: `.github/workflows/build.yml`).
3. Settings → Secrets and variables → Actions → New repository secret:
   - `KEYSTORE_B64` = KEYSTORE_B64.txt içindeki yazının tamamı
   - `KEYSTORE_PASSWORD` = KEYSTORE_PASSWORD.txt içindeki yazı
   Bu iki dosyayı kimseyle paylaşma, depoya yükleme.
4. Actions sekmesinde derleme yeşil olunca Releases → "Koppiy (en güncel)" → Koppiy.apk indir.
5. Telefondan eski Koppiy 1.1/1.2'yi kaldır (imza değişti, bir kereye mahsus). Ekran Kopya'yı silme, sadece erişilebilirliğini kapat.
6. Kur, Koppiy'yi aç, Kurulum adımlarını yap. Hızlı ayar kutucuğunu elle ekle.
7. Arkadaşlara README'deki bağlantıyı ver; bir kez eskisini silip yenisini kurmaları gerek.
