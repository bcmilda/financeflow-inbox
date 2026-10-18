# FinanceFlow Inbox

Nadstavba webové appky FinanceFlow (`bcmilda/financeflow`): Android appka, která posílá bankovní notifikace do 📥 Doručených na webu (ADR-230, ADR-232).
Není to nativní appka FinanceFlow – ta je samostatný projekt n0.2 (`financeflow-native`, plán v `doc/PLAN_NATIVNI_APLIKACE.md` webového repa).

## 📲 FinanceFlow Inbox (Android) · fáze 1 · v0.1.0

Malá Android appka, která čte **notifikace z vybraných bankovních aplikací** a posílá jejich text
do FinanceFlow. Ve webu se objeví v **Import z banky → 📲 Notifikace z telefonu → 📥 Doručené**.

Rozpoznání (částka, obchodník, banka) dělá **web** stávajícím parserem. Když banka změní text
notifikace, opraví se parser ve webu a appku není potřeba aktualizovat.

**Nic se nezapíše samo.** Každou platbu ve webu:
- ✅ přidáš do Transakcí (zvolíš kategorii a peněženku), nebo
- 🧾 **spojíš s naskenovanou účtenkou** (stejná částka ± 1 Kč, datum ± 2 dny). Účtenka je podrobnější a platbu převezme, nová transakce nevznikne. Když účtenku naskenuješ až potom, web nabídne „Převzít účtenkou“, nebo
- ✕ zahodíš.

### Co appka pošle a co ne
- **Čte jen aplikace, které zaškrtneš** (bankovní se předvyberou podle názvu). Ostatní notifikace ignoruje a nikam neukládá.
- **Z telefonu odejde jen zpráva s částkou** (Kč, CZK, €, EUR, $, £). Přihlašovací a ověřovací kódy, PIN a hesla **nikdy**.
- Text se uloží do tvého účtu ve Firebase (EU) jako návrh. Telefon do účtu smí jen **přidat** návrh – nic nepřečte, nic nesmaže (pravidla databáze v11.89).
- Telefon se přihlašuje **anonymně** a s účtem se propojí jednorázovým kódem. Appka nezná tvůj e-mail ani heslo.

### Jak ji dostat do telefonu
1. **Přes GitHub (doporučeno):** v repu `bcmilda/financeflow-inbox` se APK sestaví samo při každém pushi.
   - Na GitHubu: **Actions → Inbox Android → poslední běh → Artifacts → financeflow-inbox-apk**.
   - Stáhni ZIP a v něm je `app-debug.apk`.
2. **Nebo v Android Studiu:** *Open* → složka `inbox-android` → tlačítko ▶ Run (telefon připojený kabelem).

Instalace APK mimo Google Play: v telefonu povol „Instalovat neznámé aplikace“ pro prohlížeč / Soubory.

### První spuštění (4 kroky v appce)
1. **Přístup k oznámením** → přečti upozornění → „Souhlasím“ → v nastavení Androidu zapni *FinanceFlow Inbox*.
2. **Propojit s FinanceFlow** → appka ukáže kód (8 znaků, platí 15 minut) → ve webu **Import z banky → 📲 → zadej kód → Propojit telefon**. Do 5 s se v appce objeví ✓.
3. **Bankovní aplikace** → zkontroluj zaškrtnuté.
4. **Testovací platba** → ve webu se v 📥 Doručené objeví „Platba kartou 1,00 Kč TESTOVACI PLATBA“. Zahoď ji.

### Co musí být nasazené ve webu
- **Pravidla databáze v11.89** (`database.rules.json` → Firebase Console). Bez nich telefon nic nezapíše.
- **Web v11.89** (`js/inbox.js` a další).
- Ve Firebase **Authentication → Anonymous** zapnuté (FinanceFlow ho už používá).
- Kdyby byl API klíč v Google Cloud omezený jen na web (HTTP referrer), appka posílá hlavičku `Referer: https://financeflow.cz/`. Když to nestačí, přidej v Google Cloud Console ke klíči povolení pro Android.

### Omezení
- **Debug APK** (není podepsané pro Google Play). Pro Play je potřeba:
  - podpisový klíč;
  - formulář Data safety;
  - zásady ochrany soukromí (už máme na webu);
  - Google kontroluje appky, které čtou oznámení – výrazné upozornění před souhlasem už appka má.
- **iPhone:** iOS cizí notifikace číst nedovolí.
- Neodeslané platby (bez internetu) zůstávají ve frontě a odejdou s další notifikací, nebo po klepnutí na „Odeslat čekající“.

### Struktura
```
inbox-android/
  app/src/main/java/cz/financeflow/inbox/
    MainActivity.kt   – obrazovka (4 kroky)
    BankListener.kt   – čtení notifikací (NotificationListenerService), filtr částka / citlivé, odesílání
    FirebaseRest.kt   – anonymní přihlášení, párovací kód, zápis do users/{uid}/inbox (REST, bez SDK)
    Nastaveni.kt      – nastavení a fronta v telefonu
.github/workflows/inbox-android.yml – sestavení APK
```
Bez knihoven navíc – jen Android (minSdk 26 = Android 8).
