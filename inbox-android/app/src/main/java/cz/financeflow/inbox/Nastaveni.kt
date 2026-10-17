package cz.financeflow.inbox

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

//  Konfigurace FinanceFlow (stejná jako webová appka – veřejné údaje, žádné tajemství).
object Konfig {
    const val API_KEY = "AIzaSyDtEdQw4WccmEzxXzMwPQlenqfnjoiVw4A"
    const val DB_URL = "https://financeflow-a249c-default-rtdb.europe-west1.firebasedatabase.app"
    //  Kdyby byl klíč v Google Cloud omezený na web, požadavky ponesou tuhle hlavičku Referer.
    const val REFERER = "https://financeflow.cz/"
}

//  Vše, co si appka pamatuje, je jen v telefonu (SharedPreferences).
class Nastaveni(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("ff_inbox", Context.MODE_PRIVATE)

    var idToken: String
        get() = p.getString("idToken", "") ?: ""
        set(v) = p.edit().putString("idToken", v).apply()
    var refreshToken: String
        get() = p.getString("refreshToken", "") ?: ""
        set(v) = p.edit().putString("refreshToken", v).apply()
    var tokenPlatiDo: Long
        get() = p.getLong("tokenPlatiDo", 0L)
        set(v) = p.edit().putLong("tokenPlatiDo", v).apply()
    /** Anonymní uid telefonu (Firebase Auth). */
    var devUid: String
        get() = p.getString("devUid", "") ?: ""
        set(v) = p.edit().putString("devUid", v).apply()
    /** Uid účtu FinanceFlow, se kterým je telefon propojený („" = nepropojeno). */
    var cilUid: String
        get() = p.getString("cilUid", "") ?: ""
        set(v) = p.edit().putString("cilUid", v).apply()
    var kod: String
        get() = p.getString("kod", "") ?: ""
        set(v) = p.edit().putString("kod", v).apply()
    var kodVytvoren: Long
        get() = p.getLong("kodVytvoren", 0L)
        set(v) = p.edit().putLong("kodVytvoren", v).apply()
    var souhlas: Boolean
        get() = p.getBoolean("souhlas", false)
        set(v) = p.edit().putBoolean("souhlas", v).apply()
    var vychoziVybrano: Boolean
        get() = p.getBoolean("vychoziVybrano", false)
        set(v) = p.edit().putBoolean("vychoziVybrano", v).apply()

    /** Balíčky bankovních aplikací, jejichž notifikace se čtou. Ostatní se ignorují. */
    var banky: Set<String>
        get() = p.getStringSet("banky", emptySet())?.toSet() ?: emptySet()
        set(v) = p.edit().putStringSet("banky", HashSet(v)).apply()

    // ── fronta zachycených notifikací (posledních 100) ──
    fun fronta(): MutableList<JSONObject> {
        val s = p.getString("fronta", "[]") ?: "[]"
        val a = try { JSONArray(s) } catch (e: Exception) { JSONArray() }
        val out = ArrayList<JSONObject>()
        for (i in 0 until a.length()) out.add(a.getJSONObject(i))
        return out
    }
    @Synchronized
    fun ulozFrontu(f: List<JSONObject>) {
        val a = JSONArray()
        f.takeLast(100).forEach { a.put(it) }
        p.edit().putString("fronta", a.toString()).apply()
    }
    @Synchronized
    fun pridej(o: JSONObject): Boolean {
        val f = fronta()
        if (f.any { it.optString("id") == o.optString("id") }) return false
        f.add(o)
        ulozFrontu(f)
        return true
    }
    @Synchronized
    fun nastavStav(id: String, stav: String, chyba: String = "") {
        val f = fronta()
        f.filter { it.optString("id") == id }.forEach { it.put("stav", stav); it.put("chyba", chyba) }
        ulozFrontu(f)
    }

    fun odhlasit() {
        p.edit().remove("cilUid").remove("kod").remove("kodVytvoren").apply()
    }
}
