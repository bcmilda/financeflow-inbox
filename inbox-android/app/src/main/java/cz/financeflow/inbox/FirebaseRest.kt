package cz.financeflow.inbox

import android.os.Build
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

//  Firebase přes REST – bez Firebase SDK a bez Google přihlášení.
//  Telefon se přihlásí ANONYMNĚ (vlastní uid), účet FinanceFlow s ním propojí uživatel kódem ve webu.
//  Pravidla databáze (v11.89) dovolí telefonu jen PŘIDAT položku do users/{uid}/inbox propojeného účtu.
class FirebaseRest(private val n: Nastaveni) {

    class Chyba(val kod: Int, zprava: String) : Exception(zprava)

    private fun http(metoda: String, url: String, telo: String?, typ: String = "application/json"): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = metoda
        c.connectTimeout = 15000
        c.readTimeout = 15000
        c.setRequestProperty("Referer", Konfig.REFERER)
        if (telo != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "$typ; charset=utf-8")
            c.outputStream.use { it.write(telo.toByteArray(Charsets.UTF_8)) }
        }
        val kod = c.responseCode
        val proud = if (kod in 200..299) c.inputStream else c.errorStream
        val text = proud?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() } } ?: ""
        c.disconnect()
        if (kod !in 200..299) throw Chyba(kod, "HTTP $kod: ${text.take(200)}")
        return text
    }

    /** Platný idToken (anonymní účet telefonu se založí při prvním použití, token se obnovuje). */
    @Synchronized
    fun token(): String {
        val ted = System.currentTimeMillis()
        if (n.idToken.isNotEmpty() && ted < n.tokenPlatiDo - 60_000) return n.idToken
        if (n.refreshToken.isNotEmpty()) {
            try {
                val t = http("POST", "https://securetoken.googleapis.com/v1/token?key=${Konfig.API_KEY}",
                    "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(n.refreshToken, "UTF-8"),
                    "application/x-www-form-urlencoded")
                val o = JSONObject(t)
                n.idToken = o.getString("id_token")
                n.refreshToken = o.getString("refresh_token")
                n.devUid = o.getString("user_id")
                n.tokenPlatiDo = ted + o.optString("expires_in", "3600").toLong() * 1000
                return n.idToken
            } catch (e: Chyba) {
                if (e.kod != 400) throw e   // 400 = neplatný refresh token → založit znovu
            }
        }
        val t = http("POST", "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${Konfig.API_KEY}",
            JSONObject().put("returnSecureToken", true).toString())
        val o = JSONObject(t)
        n.idToken = o.getString("idToken")
        n.refreshToken = o.getString("refreshToken")
        n.devUid = o.getString("localId")
        n.tokenPlatiDo = ted + o.optString("expiresIn", "3600").toLong() * 1000
        return n.idToken
    }

    private fun db(cesta: String) = "${Konfig.DB_URL}/$cesta.json?auth=${token()}"

    /** Založí párovací kód (platí 15 minut), který uživatel opíše ve FinanceFlow. */
    fun zalozKod(): String {
        token()
        val abeceda = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"   // bez 0/O a 1/I
        val rnd = java.security.SecureRandom()
        val kod = (1..8).map { abeceda[rnd.nextInt(abeceda.length)] }.joinToString("")
        val telo = JSONObject()
            .put("devUid", n.devUid)
            .put("name", "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(60))
            .put("created", JSONObject().put(".sv", "timestamp"))
        http("PUT", db("inboxPairing/$kod"), telo.toString())
        n.kod = kod
        n.kodVytvoren = System.currentTimeMillis()
        return kod
    }

    /** Uid propojeného účtu, nebo "" (ještě nepropojeno / odpojeno ve webu). */
    fun zjistiPropojeni(): String {
        token()
        val t = http("GET", db("inboxLinks/${n.devUid}"), null)
        if (t == "null" || t.isBlank()) return ""
        return JSONObject(t).optString("uid", "")
    }

    fun odpoj() {
        try { http("DELETE", db("inboxLinks/${n.devUid}"), null) } catch (e: Exception) { }
        n.odhlasit()
    }

    /** Pošle jednu zachycenou notifikaci do users/{cil}/inbox/{id}. */
    fun posli(o: JSONObject) {
        val cil = n.cilUid
        if (cil.isEmpty()) throw Chyba(0, "Telefon není propojený s FinanceFlow")
        val telo = JSONObject()
            .put("dev", n.devUid)
            .put("app", o.optString("app"))
            .put("pkg", o.optString("pkg"))
            .put("title", o.optString("title"))
            .put("text", o.optString("text"))
            .put("postedAt", o.optLong("postedAt"))
            .put("v", 1)
        http("PUT", db("users/$cil/inbox/${o.getString("id")}"), telo.toString())
    }
}
