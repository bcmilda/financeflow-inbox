package cz.financeflow.inbox

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

//  Jediná obrazovka: 1) přístup k oznámením, 2) propojení s FinanceFlow, 3) bankovní aplikace,
//  4) poslední zachycené platby. Rozhraní je postavené v kódu (bez XML), ať je projekt co nejmenší.
class MainActivity : Activity() {

    private lateinit var n: Nastaveni
    private lateinit var obsah: LinearLayout
    private val hl = Handler(Looper.getMainLooper())
    private val sit = Executors.newSingleThreadExecutor()
    private var vsechnyAplikace = false

    //  Barvy jako ve FinanceFlow (tmavý motiv)
    private val POZADI = Color.parseColor("#0F1220")
    private val KARTA = Color.parseColor("#1A1E2E")
    private val TEXT = Color.parseColor("#E8EAF2")
    private val POPIS = Color.parseColor("#A8AEC8")
    private val ZELENA = Color.parseColor("#22C55E")
    private val ZLUTA = Color.parseColor("#FBBF24")
    private val CERVENA = Color.parseColor("#F87171")

    //  Bankovní aplikace podle názvu – předvybrané při prvním spuštění (uživatel to může změnit).
    private val BANKA_RE = Regex("""bank|banka|george|revolut|air ?bank|my air|fio|čsob|csob|\bkb\b|komerční|moneta|raiffeisen|unicredit|creditas|wise|n26|paypal|twisto|partners|equa|max banka|trinity|oberbank|portu|google wallet""", RegexOption.IGNORE_CASE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        n = Nastaveni(this)
        window.decorView.setBackgroundColor(POZADI)
        val sv = ScrollView(this)
        obsah = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(20), dp(16), dp(32)) }
        sv.addView(obsah)
        setContentView(sv)
    }

    override fun onResume() {
        super.onResume()
        vykresli()
        if (n.cilUid.isEmpty() && n.kod.isNotEmpty()) hlidejPropojeni()
        if (n.cilUid.isNotEmpty()) overPropojeni()
    }

    override fun onPause() {
        super.onPause()
        hl.removeCallbacksAndMessages(null)
    }

    // ── pomocné prvky UI ──
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun text(t: String, vel: Float = 15f, barva: Int = TEXT, tucne: Boolean = false) = TextView(this).apply {
        this.text = t; textSize = vel; setTextColor(barva); if (tucne) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(2), 0, dp(2))
    }
    private fun tlacitko(t: String, akce: () -> Unit) = Button(this).apply {
        this.text = t; isAllCaps = false; setOnClickListener { akce() }
    }
    private fun karta(titul: String): LinearLayout {
        val k = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { setColor(KARTA); cornerRadius = dp(14).toFloat() }
        }
        k.addView(text(titul, 16f, TEXT, true))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(12)
        obsah.addView(k, lp)
        return k
    }

    // ── stav ──
    private fun pristupPovolen(): Boolean {
        val s = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        val ja = ComponentName(this, BankListener::class.java).flattenToString()
        return s.split(":").any { it == ja }
    }

    private fun vykresli() {
        obsah.removeAllViews()
        obsah.addView(text("FinanceFlow Inbox", 22f, TEXT, true))
        obsah.addView(text("Platby z notifikací banky → 📥 Doručené ve FinanceFlow. Nic se nezapíše samo – každou platbu ve FinanceFlow potvrdíš.", 13f, POPIS).apply { setPadding(0, dp(4), 0, dp(14)) })

        // 1) přístup k oznámením
        val k1 = karta("1. Přístup k oznámením")
        if (pristupPovolen() && n.souhlas) k1.addView(text("✓ Povoleno – appka čte jen vybrané bankovní aplikace.", 14f, ZELENA))
        else {
            k1.addView(text("Bez přístupu appka notifikace z banky neuvidí.", 14f, POPIS))
            k1.addView(tlacitko("Povolit přístup k oznámením") { ukazSouhlas() })
        }

        // 2) propojení
        val k2 = karta("2. Propojení s FinanceFlow")
        when {
            n.cilUid.isNotEmpty() -> {
                k2.addView(text("✓ Propojeno s tvým účtem FinanceFlow.", 14f, ZELENA))
                k2.addView(tlacitko("Odpojit") { odpoj() })
            }
            n.kod.isNotEmpty() && System.currentTimeMillis() - n.kodVytvoren < 15 * 60_000 -> {
                k2.addView(text("Opiš tenhle kód ve FinanceFlow:\nImport z banky → 📲 Notifikace z telefonu → Propojit telefon", 14f, POPIS))
                k2.addView(text(n.kod.chunked(4).joinToString(" "), 32f, ZLUTA, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10)); setTextIsSelectable(true) })
                k2.addView(text("Kód platí 15 minut. Jakmile ho ve FinanceFlow potvrdíš, tady se objeví ✓.", 12f, POPIS))
                k2.addView(tlacitko("Zkontrolovat propojení") { overPropojeni(true) })
            }
            else -> {
                k2.addView(text("Telefon se propojí jednorázovým kódem – bez hesla, appka nezná tvůj e-mail.", 14f, POPIS))
                k2.addView(tlacitko("Propojit s FinanceFlow") { zalozKod() })
            }
        }

        // 3) bankovní aplikace
        val k3 = karta("3. Bankovní aplikace")
        k3.addView(text("Notifikace se čtou JEN z těchto aplikací. Odejde jen zpráva s částkou (Kč, €…); přihlašovací a ověřovací kódy nikdy.", 12f, POPIS))
        val aplikace = seznamAplikaci()
        if (!n.vychoziVybrano) {
            n.banky = aplikace.filter { BANKA_RE.containsMatchIn(it.second) }.map { it.first }.toSet()
            n.vychoziVybrano = true
        }
        val vybrane = n.banky
        val zobrazit = if (vsechnyAplikace) aplikace else aplikace.filter { it.first in vybrane || BANKA_RE.containsMatchIn(it.second) }
        if (zobrazit.isEmpty()) k3.addView(text("Žádná bankovní aplikace nenalezena – klepni na „Zobrazit všechny aplikace“.", 13f, ZLUTA))
        zobrazit.forEach { (pkg, nazev) ->
            k3.addView(CheckBox(this).apply {
                text = nazev; setTextColor(TEXT); isChecked = pkg in vybrane
                setOnCheckedChangeListener { _, z -> n.banky = if (z) n.banky + pkg else n.banky - pkg }
            })
        }
        k3.addView(tlacitko(if (vsechnyAplikace) "Jen bankovní aplikace" else "Zobrazit všechny aplikace") { vsechnyAplikace = !vsechnyAplikace; vykresli() })

        // 4) poslední zachycené
        val k4 = karta("4. Poslední zachycené platby")
        val f = n.fronta().reversed().take(15)
        if (f.isEmpty()) k4.addView(text("Zatím nic. Po první platbě kartou se tu objeví.", 13f, POPIS))
        val fmt = SimpleDateFormat("d. M. HH:mm", Locale("cs", "CZ"))
        f.forEach { o ->
            val stav = o.optString("stav")
            val (ikona, barva) = when (stav) { "odeslano" -> "✓ odesláno" to ZELENA; "chyba" -> "⚠ " + o.optString("chyba") to CERVENA; else -> "⏳ čeká" to ZLUTA }
            k4.addView(text("${fmt.format(Date(o.optLong("postedAt")))} · ${o.optString("app")}", 12f, POPIS))
            k4.addView(text("${o.optString("title")} ${o.optString("text")}".trim().take(160), 14f, TEXT))
            k4.addView(text(ikona, 12f, barva).apply { setPadding(0, 0, 0, dp(8)) })
        }
        val radek = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        radek.addView(tlacitko("Odeslat čekající") { Odesilac.odesliVse(applicationContext) { hl.post { vykresli() } } })
        radek.addView(tlacitko("Testovací platba") { testovaci() })
        k4.addView(radek)
    }

    private fun seznamAplikaci(): List<Pair<String, String>> {
        val pm = packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val res = pm.queryIntentActivities(i, 0)
        return res.map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase(Locale("cs", "CZ")) }
    }

    // ── akce ──
    //  Výrazné upozornění PŘED žádostí o přístup (požadavek Google Play).
    private fun ukazSouhlas() {
        AlertDialog.Builder(this)
            .setTitle("Čtení notifikací z banky")
            .setMessage("FinanceFlow Inbox bude číst oznámení POUZE z bankovních aplikací, které vybereš v bodě 3.\n\n" +
                "• Z telefonu odejde jen text oznámení, které obsahuje částku (platba kartou, příchozí platba).\n" +
                "• Přihlašovací a ověřovací kódy se nikdy neodesílají.\n" +
                "• Text se uloží do tvého účtu FinanceFlow (Firebase, EU) jako návrh – transakce vznikne, až ji potvrdíš.\n" +
                "• Ostatní oznámení appka ignoruje a nikam neukládá.\n\n" +
                "V dalším kroku Android otevře nastavení „Přístup k oznámením“ – zapni tam FinanceFlow Inbox.")
            .setPositiveButton("Souhlasím, pokračovat") { _, _ ->
                n.souhlas = true
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            .setNegativeButton("Zrušit", null)
            .show()
    }

    private fun naPozadi(prace: () -> Unit) {
        sit.execute {
            try { prace() } catch (e: Exception) {
                hl.post { Toast.makeText(this, "Nepovedlo se: ${e.message?.take(120)}", Toast.LENGTH_LONG).show() }
            }
            hl.post { vykresli() }
        }
    }

    private fun zalozKod() = naPozadi {
        FirebaseRest(n).zalozKod()
        hl.post { hlidejPropojeni() }
    }

    //  Po zobrazení kódu se každých 5 s podívá, jestli ho uživatel už ve webu potvrdil.
    private fun hlidejPropojeni() {
        hl.removeCallbacksAndMessages(null)
        hl.postDelayed(object : Runnable {
            override fun run() {
                if (n.cilUid.isNotEmpty() || n.kod.isEmpty() || System.currentTimeMillis() - n.kodVytvoren > 15 * 60_000) return
                overPropojeni()
                hl.postDelayed(this, 5000)
            }
        }, 5000)
    }

    private fun overPropojeni(hlasit: Boolean = false) {
        sit.execute {
            try {
                val uid = FirebaseRest(n).zjistiPropojeni()
                val bylo = n.cilUid
                n.cilUid = uid
                if (uid.isNotEmpty() && bylo.isEmpty()) {
                    n.kod = ""
                    hl.post { Toast.makeText(this, "✓ Propojeno s FinanceFlow", Toast.LENGTH_LONG).show() }
                    Odesilac.odesliVse(applicationContext)
                } else if (hlasit && uid.isEmpty()) {
                    hl.post { Toast.makeText(this, "Ještě nepropojeno – opiš kód ve FinanceFlow", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                if (hlasit) hl.post { Toast.makeText(this, "Bez připojení: ${e.message?.take(80)}", Toast.LENGTH_SHORT).show() }
            }
            hl.post { vykresli() }
        }
    }

    private fun odpoj() {
        AlertDialog.Builder(this)
            .setMessage("Odpojit telefon od FinanceFlow? Notifikace přestanou chodit.")
            .setPositiveButton("Odpojit") { _, _ -> naPozadi { FirebaseRest(n).odpoj() } }
            .setNegativeButton("Zrušit", null).show()
    }

    //  Ověří celou cestu bez platby: zkušební položka se objeví ve FinanceFlow v 📥 Doručené.
    private fun testovaci() {
        if (n.cilUid.isEmpty()) { Toast.makeText(this, "Nejdřív propoj telefon (bod 2)", Toast.LENGTH_SHORT).show(); return }
        val o: JSONObject = Filtr.zpracuj(packageName, "FinanceFlow Inbox", "Test", "Platba kartou 1,00 Kč TESTOVACI PLATBA", System.currentTimeMillis()) ?: return
        n.pridej(o)
        Odesilac.odesliVse(applicationContext) { hl.post { vykresli(); Toast.makeText(this, "Testovací platba odeslána – podívej se do FinanceFlow", Toast.LENGTH_LONG).show() } }
    }
}
