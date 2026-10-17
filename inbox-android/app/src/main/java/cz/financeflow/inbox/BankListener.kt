package cz.financeflow.inbox

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject
import java.util.concurrent.Executors

//  Čte notifikace JEN z bankovních aplikací, které uživatel vybral. Ostatní se nečtou ani neukládají.
//  Z telefonu odejde jen notifikace, která vypadá jako platba (obsahuje částku s měnou) a není to
//  přihlašovací / ověřovací kód.
class BankListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val n = Nastaveni(this)
        if (!n.souhlas || sbn.packageName !in n.banky) return
        val no = sbn.notification ?: return
        if (sbn.isOngoing || (no.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val e = no.extras ?: return
        val titulek = e.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
        val o = Filtr.zpracuj(sbn.packageName, nazevAplikace(this, sbn.packageName), titulek, text, sbn.postTime) ?: return
        if (n.pridej(o)) Odesilac.odesliVse(applicationContext)
    }

    companion object {
        fun nazevAplikace(ctx: Context, pkg: String): String = try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) { pkg }
    }
}

object Filtr {
    private val MENA = Regex("""(\d[\d\s.,]*\s*(Kč|CZK|EUR|€|USD|\$|GBP|£))|((Kč|CZK|EUR|€|USD|\$|GBP|£)\s*\d)""", RegexOption.IGNORE_CASE)
    //  Krátká slova (PIN, OTP, kód) jen jako samostatná slova – „SPINKA“ nebo „ESKYMO OTPOČINEK“ projdou.
    private val CITLIVE = Regex("""((^|\W)(kód|kod|code|PIN|OTP|SMS kód)(\W|$))|heslo|password|ověřovac|overovac|verifik|verification|přihlášen|prihlasen|login""", RegexOption.IGNORE_CASE)

    /** Vrátí položku fronty, nebo null (není to platba / je to citlivé). */
    fun zpracuj(pkg: String, app: String, titulek: String, text: String, postedAt: Long): JSONObject? {
        val vse = "$titulek $text"
        if (text.isBlank() || !MENA.containsMatchIn(vse) || CITLIVE.containsMatchIn(vse)) return null
        //  Stejná notifikace se často zobrazí znovu (aktualizace) → stejné id = žádný duplikát.
        val id = "n" + (postedAt / 60000) + "_" + Integer.toHexString((pkg + "|" + vse).hashCode())
        return JSONObject()
            .put("id", id).put("pkg", pkg).put("app", app.take(100))
            .put("title", titulek.take(300)).put("text", text.take(1000))
            .put("postedAt", postedAt).put("stav", "ceka")
    }
}

object Odesilac {
    private val vlakno = Executors.newSingleThreadExecutor()

    /** Pošle všechny čekající položky; neúspěšné zůstanou ve frontě a zkusí se příště. */
    fun odesliVse(ctx: Context, hotovo: (() -> Unit)? = null) {
        vlakno.execute {
            val n = Nastaveni(ctx)
            if (n.cilUid.isNotEmpty()) {
                val fb = FirebaseRest(n)
                n.fronta().filter { it.optString("stav") != "odeslano" }.forEach { o ->
                    try {
                        fb.posli(o)
                        n.nastavStav(o.getString("id"), "odeslano")
                    } catch (e: FirebaseRest.Chyba) {
                        //  401/403 = telefon byl ve webu odpojen
                        if (e.kod == 401 || e.kod == 403) n.nastavStav(o.getString("id"), "chyba", "Odmítnuto – telefon je možná odpojený")
                        else n.nastavStav(o.getString("id"), "chyba", e.message ?: "")
                    } catch (e: Exception) {
                        n.nastavStav(o.getString("id"), "chyba", "Bez připojení – zkusí se znovu")
                    }
                }
            }
            hotovo?.invoke()
        }
    }
}
