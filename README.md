# Ultra Keyboard

Android tastatura (IME) sa klasičnim 3×4 rasporedom kao na starim telefonima
(multi-tap), sa punom podrškom za TalkBack, srpsku latinicu i ćirilicu, simbole
i emotikone.

## Kako otvoriti projekat

1. Skini/otpakuj ovaj folder.
2. Otvori **Android Studio** → *Open* → izaberi folder `UltraKeyboard`.
3. Pri prvom otvaranju, Android Studio će tražiti da preuzme Gradle distribuciju
   (definisano u `gradle/wrapper/gradle-wrapper.properties`) — to je normalno,
   sačekaj da završi sinhronizaciju.
4. Poveži telefon (ili pokreni emulator) i klikni **Run**.

## Kako uključiti tastaturu na telefonu

1. Pokreni instaliranu aplikaciju "Ultra Keyboard" — otvoriće se ekran sa
   uputstvom i dugmetom **"Otvori podešavanja tastature"**.
2. U Podešavanjima → Sistem → Jezici i unos → Tastature → Upravljaj
   tastaturama, uključi "Ultra Keyboard".
3. U bilo kom polju za tekst, dugo pritisni traku za razmak (ili ikonicu
   planete/globusa) i izaberi "Ultra Keyboard" kao aktivnu.

## Kako radi unos

- **Tasteri 2–9**: pritisni više puta zaredom da promeniš slovo (npr. 2,2,2 = c).
  Sačekaj kratko (0.9s) i slovo se potvrđuje — sledeći pritisak na isti taster
  počinje novo slovo.
- **Taster 1**: interpunkcija (. , ? ! ' ")
- **Taster 0**: razmak (dug pritisak nije potreban, prvi pritisak je razmak)
- **Taster \***: kratak pritisak = brzi simboli (- : ; @ / *); **dug pritisak**
  otvara pun panel simbola.
- **Taster #**: kratak pritisak = novi red (Enter); **dug pritisak** otvara
  panel emotikona.
- **⇧ (Shift)**: jedan pritisak = veliko sledeće slovo; dupli pritisak (brzo
  dva puta) = zaključano veliko pisanje (Caps Lock); ponovo = isključi.
- **LAT/ЋИР**: prebacuje latinicu ↔ ćirilicu.
- **⌫**: briše.

## Pristupačnost (TalkBack)

Svaki taster je pravi `Button` (ne crtež na canvasu), pa TalkBack automatski
podržava: fokusiranje dodirom, čitanje opisa (`contentDescription`), i
navigaciju prevlačenjem prsta. Pošto TalkBack po sistemskom pravilu traži
**dupli dodir** za aktivaciju bilo kog dugmeta, multi-tap ciklus se radi tako
što se isti taster dvostruko-dodirne uzastopno (svaki dupli-dodir = jedan
"klik" na taj broj, kao pritisak na starom telefonu).

Svaki put kad se slovo promeni ili unese, aplikacija to **naglas najavljuje**
(`announceForAccessibility`), tako da korisnik čuje trenutno slovo bez potrebe
da diže prst i istražuje ekran.

## Slanje loga za testiranje (bez adb-a)

Aplikacija sad sama upisuje log u fajl na telefonu dok se koristi tastatura.
Kad neko testira i naiđe na problem:

1. Otvori aplikaciju "Ultra Keyboard" (ikonica na telefonu).
2. Klikne **"Pošalji log (za testiranje)"** — otvara se meni za deljenje
   (WhatsApp, Email, itd.) sa log fajlom spremnim za slanje.
3. Po želji, **"Obriši log"** pre novog testa da log ne bude pun starih
   pokušaja.

## Potpisana (release) verzija — za GitHub / Play Store

U projektu se sada nalazi tvoj lični Ultra potpisni ključ:
- `keystore/ultra-release-key.jks` — sam ključ
- `keystore/PASSWORD_SACUVAJ_OVO.txt` — lozinka (ista za store i key)
- `keystore.properties` — fajl koji Gradle čita da bi automatski potpisao release build

**KRITIČNO**: ovaj ključ je zauvek identitet "Ultra" aplikacija na Play Store-u.
Ako ga izgubiš, **nikad više nećeš moći da objaviš update** za istu aplikaciju
pod istim imenom — moraš praviti potpuno novu aplikaciju od nule. Zato:

1. Odmah napravi rezervnu kopiju `keystore/ultra-release-key.jks` i lozinke iz
   `PASSWORD_SACUVAJ_OVO.txt` NEGDE VAN ovog foldera (npr. lozinkom zaštićen
   cloud folder, USB koji ne gubiš, password manager).
2. Isti ključ ćemo koristiti za SVE buduće "Ultra" aplikacije (Creative
   Suite, AI Camera...) — jedan ključ, jedan brend.
3. `keystore.properties` i sam `keystore/` folder su namerno u `.gitignore`
   — **nikad ne idu na GitHub**. Samo izvorni kod ide tamo.

Da napraviš potpisani APK:
```
gradlew.bat assembleRelease
```
Fajl će se pojaviti u `app\build\outputs\apk\release\app-release.apk` —
to je verzija potpisana tvojim ključem, spremna za deljenje ili dalje korake
ka Play Store-u (koji dodatno traži .aab fajl — `gradlew.bat bundleRelease`
kad dođe vreme za to).

## GitHub Actions — automatski build potpisanog APK-a

Workflow (`.github/workflows/build-release.yml`) sam pravi potpisan APK na
GitHub-ovom serveru pri svakom push-u na `main`, i pravi pravi GitHub Release
(sa APK-om prikačenim za preuzimanje) kad god pušuješ tag koji počinje sa `v`
(npr. `v1.0`).

Pošto `keystore/` i `keystore.properties` **namerno nisu** u repo-u (vidi
`.gitignore`), workflow ih sam sastavlja iz **GitHub Secrets** pri svakom
build-u, koristi ih, i odbaci — nikad ne završe u kodu ili istoriji repo-a.

### Jednokratno podešavanje (pre prvog push-a)

**1. Pretvori keystore u base64 tekst** (u cmd-u, u folderu projekta):
```
powershell -Command "[Convert]::ToBase64String([IO.File]::ReadAllBytes('keystore\ultra-release-key.jks')) | Out-File -Encoding ascii keystore_base64.txt"
```
Ovo pravi fajl `keystore_base64.txt` sa dugačkim tekstom (bez razmaka/novih
redova) - to je tvoj keystore, samo u tekstualnom obliku pogodnom za Secrets.

**2. Otvori lozinku**:
```
type keystore\PASSWORD_SACUVAJ_OVO.txt
```

**3. Na GitHub-u**: otvori repo → **Settings → Secrets and variables →
Actions → New repository secret**, i dodaj četiri:

| Ime tajne | Vrednost |
|---|---|
| `KEYSTORE_BASE64` | ceo sadržaj `keystore_base64.txt` |
| `KEYSTORE_PASSWORD` | lozinka iz `PASSWORD_SACUVAJ_OVO.txt` |
| `KEY_ALIAS` | `ultra_keyboard` |
| `KEY_PASSWORD` | ista lozinka kao `KEYSTORE_PASSWORD` |

**4. Obriši `keystore_base64.txt`** posle kopiranja u GitHub (bio je samo
privremen, sadrži tajni ključ u čitljivom obliku - ne treba da ostane na
disku niti da se slučajno doda u git).

Posle ovoga, svaki push automatski pravi APK (vidi se pod repo-ov **Actions**
tab, dugme **Artifacts** na dnu svakog build-a), a push-ovanje verzionog taga
pravi pravi Release spreman za deljenje linkom.

## Poznata ograničenja / ideje za dalje

 Dž, Lj, Nj se kucaju kao dva odvojena slova (d+ž, l+j, n+j) — namerno
  pojednostavljeno za prvu verziju.
- Nema još predikcije reči (T9 rečnik) — dogovoreno da se doda kasnije.
- Raspored slova po tasterima je lako promeniti u `KeyMaps.kt`.
- Ikonica aplikacije je jednostavan plejsholder (vector drawable) — zameni po
  želji sa `app/src/main/res/drawable/ic_launcher.xml` ili pravim mipmap
  setom.
- Nije testirano na pravom uređaju iz ovog razgovora (nemam pristup Android
  SDK/emulatoru u ovom okruženju) — javi mi šta javi Android Studio pri prvom
  build-u pa rešavamo zajedno.
