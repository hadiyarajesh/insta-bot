package util

import khttp.structures.cookie.Cookie
import khttp.structures.cookie.CookieJar
import java.io.File

class CookiePersistor(private val resource: String) {
    // Check if persisted cookie is exists
    fun exist(): Boolean {
        return File(resource).exists()
    }

    // Save account and cookies to storage
    fun save(account: String, cookieJar: CookieJar) {
        val cksList = arrayListOf<String>()
        cookieJar.entries.forEach {
            cksList.add("$it")
        }
        val cookiesString = cksList.toList().joinToString("#")
        File(resource).printWriter().use { out ->
            out.print("account->$account\ncookies->$cookiesString")
        }
    }

    // Load cookies and account from storage
    fun load(): CookieDisk {
        val jar = CookieJar()
        val split = File(resource).readText().split("\n")
        val account = split[0].split("->")[1]
        val cookiesString = split[1].split("->")[1].split("#")
        cookiesString.forEach { it ->
            val cks = Cookie(it)
            jar.setCookie(cks)
        }
        return CookieDisk(account, jar)
    }

    // Delete cookies
    fun destroy() {
        if (exist()) {
            File(resource).delete()
        }
    }
}

data class CookieDisk(val account: String, val cookieJar: CookieJar)
