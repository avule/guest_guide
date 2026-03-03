# GuestGuide

Android aplikacija za upravljanje smještajem koja povezuje domaćine i goste. Domaćin kreira profil apartmana sa svim potrebnim informacijama, a gost pristupa tim podacima putem jedinstvenog pristupnog koda.

## Funkcionalnosti

### Admin (Domaćin)
- Registracija i prijava putem Firebase Auth
- Kreiranje i upravljanje više apartmana
- Postavljanje Wi-Fi podataka, kućnih pravila i lokacije
- Dodavanje preporuka (restorani, znamenitosti, vinarije...)
- Dodavanje važnih kontakata (taxi, policija, hitna)
- Generisanje jedinstvenog pristupnog koda za goste
- Izmjena profila (email, lozinka)

### Gost
- Pristup apartmanu putem pristupnog koda
- Pamćenje zadnjeg koda lokalno (SharedPreferences)
- Pregled Wi-Fi podataka sa opcijom kopiranja lozinke
- Pregled kućnih pravila
- Prikaz lokacije na Google Maps (statička mapa + navigacija)
- Pozivanje vlasnika jednim klikom
- Pretraga i filtriranje preporuka po kategorijama
- Pregled preporučenih mjesta sa ocjenama i lokacijama
- Pregled važnih kontakata

## Arhitektura

Projekat koristi **MVVM** (Model-View-ViewModel) arhitekturu:

```
com.example.guestguide/
├── data/model/          # Data klase (Apartment, Contact, Recommendation)
├── ui/
│   ├── admin/           # Admin ekrani + AdminDialogHelper
│   ├── guest/           # Gost ekrani
│   ├── welcome/         # Početni ekran
│   └── adapter/         # RecyclerView adapteri
├── viewmodel/           # SharedViewModel (Firebase operacije)
└── utils/               # Resource wrapper za async stanja
```

## Tehnologije

| Kategorija | Tehnologija |
|------------|-------------|
| Jezik | Kotlin |
| Arhitektura | MVVM |
| Backend | Firebase Firestore, Firebase Auth, Firebase Storage |
| Lokalno skladištenje | SharedPreferences |
| Navigacija | Jetpack Navigation + Safe Args |
| Async | Kotlin Coroutines + StateFlow |
| UI | Material Design + View Binding |
| Slike | Glide |
| Mape | Google Maps Static API |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 34 (Android 14) |

## Pokretanje projekta

### Preduvjeti
- Android Studio Hedgehog ili noviji
- JDK 8+
- Google account za Firebase

### Koraci

1. **Kloniraj repozitorij**
   ```bash
   git clone https://github.com/<username>/GuestGuide.git
   ```

2. **Firebase setup**
   - Kreiraj projekat na [Firebase Console](https://console.firebase.google.com/)
   - Omogući **Authentication** (Email/Password)
   - Omogući **Cloud Firestore**
   - Preuzmi `google-services.json` i stavi u `app/` direktorij

3. **Google Maps API ključ**
   - Kreiraj API ključ na [Google Cloud Console](https://console.cloud.google.com/)
   - Omogući **Maps Static API**
   - Dodaj u `local.properties`:
     ```
     MAPS_API_KEY=tvoj_api_kljuc
     ```

4. **Pokreni aplikaciju**
   - Otvori projekat u Android Studio
   - Sync Gradle
   - Run na emulatoru ili fizičkom uređaju

## Navigacija aplikacije

```
Welcome Screen
├── [Unesi kod] → Guest Home → Istraži grad
└── [Admin]     → Login → Admin Setup (upravljanje apartmanima)
```

## Firestore struktura

```
apartments/
└── {accessCode}/
    ├── name, location, wifiSsid, wifiPassword
    ├── ownerContact, houseRules, imageUrl, ownerId
    ├── recommendations/
    │   └── {id}/ → name, description, category, rating, mapLink
    └── contacts/
        └── {id}/ → name, number, type
```

## Autor

Razvijeno kao projekat za fakultet.
