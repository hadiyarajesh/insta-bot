package api

import util.LoginException
import com.nfeld.jsonpathlite.JsonPath
import khttp.get
import khttp.post
import khttp.structures.cookie.CookieJar
import util.Crypto
import util.HTTP
import java.io.File
import kotlin.random.Random

// Generic class to send GET/POST request
class Request {
    private var url: String = ""
    private var data: String = ""
    private var isGet = true
    var persistedCookies: CookieJar? = null
    var headers = HTTP.HEADERS
    private var extraSignature: MutableMap<String, String>? = null

    fun prepare(
            endpoint: String?,
            payload: String = "",
            header: Map<String, String>? = null,
            extraSig: Map<String, String>? = null,
            API_URL: String = HTTP.API_URL
    ): Request {
        url = "$API_URL$endpoint"
        data = payload
        isGet = data.isEmpty()
        extraSig?.let { extraSignature?.putAll(it) }
        header?.let { headers.putAll(it) }
        val extraHeaders = mapOf(
            "X-IG-Connection-Speed" to "-1kbps",
            "X-IG-Bandwidth-Speed-KBPS" to Random.Default.nextInt(7000, 10000).toString(),
            "X-IG-Bandwidth-TotalBytes-B" to Random.Default.nextInt(500000, 900000).toString(),
            "X-IG-Bandwidth-TotalTime-MS" to Random.Default.nextInt(50, 150).toString()
        )
        headers.putAll(extraHeaders)

        return this
    }

    fun send(isLogin: Boolean = false): Boolean {

        if (!InstagramAPI.isLoggedIn && !isLogin) {
            throw LoginException("Please login first")
        }

        val response = if (isGet) {
            get(url = url, headers = headers, cookies = persistedCookies)
        } else {
            val signature = data.let { Crypto.signData(it) }
            val payload = mutableMapOf(
                "signed_body" to "${signature.signed}.${signature.payload}",
                "ig_sig_key_version" to signature.sigKeyVersion
            )

            extraSignature?.let { payload.putAll(it) }
            post(url = url, headers = headers, data = payload, cookies = persistedCookies)
        }

        if (persistedCookies == null) {
            persistedCookies = response.cookies
        } else {
            persistedCookies?.putAll(response.cookies)
        }

        InstagramAPI.totalRequests += 1
        InstagramAPI.lastResponse = response
        InstagramAPI.statusCode = response.statusCode

        if (response.statusCode == 200) {
            InstagramAPI.lastJSON = JsonPath.parseOrNull(response.text)
            return true
        } else {
            if (response.statusCode != 404) {
                InstagramAPI.lastJSON = JsonPath.parseOrNull(response.text)

                if (InstagramAPI.lastJSON?.read<String>("$.message") == "feedback_required") {
                    println("ATTENTION! feedback required")
                }
            }

            when (response.statusCode) {
                429 -> {
                    val sleepMinutes = 5L
                    println("Request return 429, it means too many request. I will go to sleep for $sleepMinutes minutes")
                    Thread.sleep(sleepMinutes * 60 * 1000)
                }
                400 -> {
                    InstagramAPI.lastJSON = JsonPath.parseOrNull(response.text)
                    when {
                        InstagramAPI.lastJSON?.read<Boolean>("$.two_factor_required") == true -> {
                            // Perform interactive two factor authentication
                            return InstagramAPI.performTwoFactorAuth()
                        }

                        InstagramAPI.lastJSON?.read<String>("$.message") == "challenge_required" -> {
                            // Perform interactive challenge solving
                            return InstagramAPI.solveChallenge()
                        }

                        else -> {
                            println("Instagram's error message: ${InstagramAPI.lastJSON?.read<String>("$.message")}, STATUS_CODE: ${response.statusCode}")
                            return false
                        }
                    }
                }
                403 -> {
                    InstagramAPI.lastJSON = JsonPath.parseOrNull(response.text)

                    if (InstagramAPI.lastJSON?.read<String>("$.message") == "login_required") {
                        println("Re-login required. Clearing cookie file")
                        val cookieFile = File(InstagramAPI.username)
                        if (cookieFile.exists()) {
                            if (cookieFile.delete()) {
                                println("Cookie file cleared successfully")
                            }
                        }
                        println("Cookie file does not found")
                    } else {
                        println("Something went wrong. ${response.text}")
                    }
                    return false
                }
                405 -> {
                    println("This method is not allowed")
                    return false
                }
            }
        }

        return false
    }
}