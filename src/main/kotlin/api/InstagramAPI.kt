package api

import util.LoginException
import com.nfeld.jsonpathlite.JsonPath
import com.nfeld.jsonpathlite.JsonResult
import com.nfeld.jsonpathlite.extension.read
import khttp.get
import khttp.post
import khttp.responses.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import util.*
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.system.exitProcess

object InstagramAPI {
    var username: String = "username"
    var password: String = "password"
    var deviceId: String = "xxxx"
    var uuid: String = "xxxx"
    var userId: String = ""
    var token: String = "-"
    var rankToken: String = "-"
    var isLoggedIn: Boolean = false
    var lastJSON: JsonResult? = null
    lateinit var lastResponse: Response
    var statusCode: Int = 0
    var totalRequests: Int = 0
    private var request: Request = Request()
    private var cookiePersistor: CookiePersistor = CookiePersistor("")


    // Prepare Instagram API
    fun prepare() {
        deviceId = Crypto.generateDeviceId(username)
        uuid = Crypto.generateUUID(true)
        cookiePersistor = CookiePersistor(username)
        if (cookiePersistor.exist()) {
            val cookieDisk = cookiePersistor.load()
            val account = JSONObject(cookieDisk.account)
            if (account.getString("status").toLowerCase() == "ok") {
                println("Already logged in to Instagram")
                val jar = cookieDisk.cookieJar
                request.persistedCookies = jar
                isLoggedIn = true
                userId = jar.getCookie("ds_user_id")?.value.toString()
                token = jar.getCookie("csrftoken")?.value.toString()
                rankToken = "${userId}_$uuid"
            }
        } else {
            println("Cookie file does not exist, need to login first")
        }
    }

    private fun preLoginFlow() {
        println("Initiating pre login flow")
        readMSISDNHeader()
        syncLauncher(isLogin = true)
        syncDeviceFeatures()
        logAttribution()
        setContactPointPrefill()
    }

    private fun readMSISDNHeader(usage: String = "default"): Boolean {
        val payload = JSONObject()
            .put("device_id", this.uuid)
            .put("mobile_subno_usage", usage)

        val header = mapOf("X-DEVICE-ID" to uuid)

        return request.prepare(endpoint = Routes.msisdnHeader(), payload = payload.toString(), header = header)
            .send(true)
    }

    private fun syncLauncher(isLogin: Boolean = false): Boolean {
        val payload = JSONObject()
            .put("id", uuid)
            .put("server_config_retrieval", "1")
            .put("experiments", EXPERIMENTS.LAUNCHER_CONFIGS)

        if (!isLogin) {
            payload
                .put("_csrftoken", token)
                .put("_uid", userId)
                .put("_uuid", uuid)
        }

        return request.prepare(endpoint = Routes.launcherSync(), payload = payload.toString()).send(true)
    }

    private fun syncDeviceFeatures(): Boolean {
        val payload = JSONObject()
            .put("id", uuid)
            .put("server_config_retrieval", "1")
            .put("experiments", EXPERIMENTS.LOGIN_EXPERIMENTS)

        val header = mapOf("X-DEVICE-ID" to uuid)

        return request.prepare(endpoint = Routes.qeSync(), payload = payload.toString(), header = header).send(true)
    }

    private fun logAttribution(usage: String = "default"): Boolean {
        val payload = JSONObject()
            .put("adid", Crypto.generateUUID(true))

        return request.prepare(endpoint = Routes.logAttribution(), payload = payload.toString()).send(true)
    }

    private fun setContactPointPrefill(usage: String = "prefill"): Boolean {
        val payload = JSONObject()
            .put("id", this.uuid)
            .put("phone_id", Crypto.generateUUID(true))
            .put("_csrftoken", this.token)
            .put("usage", usage)

        return request.prepare(endpoint = Routes.contactPointPrefill(), payload = payload.toString()).send(true)
    }

    // Login to Instagram
    fun login(username: String, password: String, forceLogin: Boolean = false): Boolean {
        if (!isLoggedIn || forceLogin) {
            preLoginFlow()

            val payload = JSONObject()
                .put("_csrftoken", "missing")
                .put("device_id", deviceId)
                .put("_uuid", uuid)
                .put("username", username)
                .put("password", password)
                .put("login_attempt_count", "0")

            if (request.prepare(endpoint = Routes.login(), payload = payload.toString()).send(true)) {
                saveSuccessfulLogin()
                return true
            } else {
                println("Username or password is incorrect.")
                exitProcess(1)
            }
        }
        return false
    }

    private fun saveSuccessfulLogin() {
        cookiePersistor.save(lastResponse.text, lastResponse.cookies)
        val account = lastResponse.jsonObject
        if (account.getString("status").toLowerCase() == "ok") {
            val jar = lastResponse.cookies
            isLoggedIn = true
            userId = jar.getCookie("ds_user_id")?.value.toString()
            token = jar.getCookie("csrftoken")?.value.toString()
            rankToken = "${userId}_$uuid"

            println("Logged in successfully")
            postLoginFlow()
        }
    }

    // Sync features after successful login
    private fun postLoginFlow() {
        println("Initiating post login flow")
        syncLauncher(isLogin = false)
        syncUserFeatures()
        // Update feed and timeline
        getTimeline()
        getReelsTrayFeed(reason = "cold_start")
        getSuggestedSearches("users")
        getSuggestedSearches("blended")
        // DM update
        getRankedRecipients("reshare", true)
        getRankedRecipients("save", true)
        getInboxV2()
        getPresence()
        getRecentActivity()
        // Config and other stuffs
        getLoomFetchConfig()
        getProfileNotice()
        getBatchFetch()
        getExplore(true)
        getAutoCompleteUserList()
    }

    // Perform interactive two step verification process
    fun performTwoFactorAuth(): Boolean {
        println("Two-factor authentication required")
        println("Enter 2FA verification code: ")
        val twoFactorAuthCode = readLine()
        val twoFactorAuthID = lastJSON?.read<JSONObject>("$.two_factor_info")?.read<String>("$.two_factor_identifier")
        val payload = JSONObject()
            .put("username", username)
            .put("verification_code", twoFactorAuthCode)
            .put("two_factor_identifier", twoFactorAuthID)
            .put("password", password)
            .put("device_id", deviceId)
            .put("ig_sig_key_version", KEY.SIG_KEY_VERSION)


        if (request.prepare(endpoint = Routes.twoFactorAuth(), payload = payload.toString()).send(true)) {
            if (lastJSON?.read<String>("$.status") == "ok") {
                return true
            }
        } else {
            println(lastJSON?.read<String>("$.message"))
        }

        return false
    }

    // Perform interactive challenge solving
    fun solveChallenge(): Boolean {
        println("Checkpoint challenge required")
        val challengeUrl = lastJSON?.read<JSONObject>("$.challenge")?.read<String>("$.api_path")?.removeRange(0, 1)
        request.prepare(endpoint = challengeUrl).send(true)

        val choices = getChallengeChoices()
        choices.forEach { println(it) }
        print("Enter your choice: ")
        val selectedChoice = readLine()?.toInt()

        val payload = JSONObject()
            .put("choice", selectedChoice)

        if (request.prepare(endpoint = challengeUrl, payload = payload.toString()).send(true)) {
            println("A code has been sent to the method selected, please check.")
            println("Enter your code: ")
            val code = readLine()?.toInt()
            val secondPayload = JSONObject()
                .put("security_code", code)

            request.prepare(endpoint = challengeUrl, payload = secondPayload.toString()).send(true)
            if (lastJSON?.read<String>("$.action") == "close" && lastJSON?.read<String>("$.status") == "ok") {
                return true
            }
        }

        println("Failed to log in. Try again")
        return false
    }

    // Get challenge choices
    private fun getChallengeChoices(): List<String> {
        val choices: MutableList<String> = mutableListOf()
        if (lastJSON?.read<String>("$.step_name") == "select_verify_method") {
            choices.add("Checkpoint challenge received")

            val stepData = lastJSON?.read<JSONObject>("$.step_data")
            if (stepData?.has("phone_number") == true) {
                choices.add("0 - Phone ${stepData.get("$.phone_number")}")
            }

            if (stepData?.has("email") == true) {
                choices.add("0 - Phone ${stepData.get("$.email")}")
            }
        }

        if (lastJSON?.read<String>("$.step_name") == "delta_login_review") {
            choices.add("Login attempt challenge received")
            choices.add("0 - It was me")
            choices.add("1 - It wasn't me")
        }

        if (choices.isEmpty()) {
            println("No challenge found, might need to change password")
            println("Proceed with changing password? (y/n)")
            val choice = readLine()
            if (choice == "y") {
                println("Enter your new password:")
                val newPassword = readLine()
                if (changePassword(newPassword!!)) {
                    println("Password changed successfully. Please re-try now")
                } else {
                    println("Failed to change password.")
                }
            } else if (choice == "n") {
                println("You must need to change password to avoid being detected by Instagram")
            } else {
                println("Invalid input")
            }
            choices.add("0 - Nothing found")
            println("Please quit and retry again")
        }

        return choices
    }

    //Logout from instagram
    fun logout(): Boolean {
        if (request.prepare(endpoint = Routes.logout(), payload = "{}").send()) {
            cookiePersistor.destroy()
            println("Logged out from instagram")
            return true
        }
        return false
    }

    private fun syncUserFeatures(): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", token)
            .put("device_id", deviceId)
            .put("_uuid", uuid)
            .put("id", this.uuid)
            .put("experiments", EXPERIMENTS.EXPERIMENTS)

        val header = mapOf("X-DEVICE-ID" to uuid)

        return request.prepare(endpoint = Routes.qeSync(), payload = payload.toString(), header = header).send()
    }

    // Get zoneOffSet of current System timezone
    private fun getZoneOffSet(): String =
        ZoneId.of(Calendar.getInstance().timeZone.toZoneId().toString()).rules.getOffset(LocalDateTime.now()).toString()
            .replace(
                ":",
                ""
            )

    // Get timeline feed
    fun getTimeline(options: List<String> = listOf(), maxId: String = ""): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", token)
            .put("_uuid", uuid)
            .put("is_prefetch", 0)
            .put("phone_id", Crypto.generateUUID(true))
            .put("device_id", deviceId)
            .put("client_session_id", Crypto.generateUUID(true))
            .put("battery_level", Random.Default.nextInt(25, 100))
            .put("is_charging", Random.Default.nextInt(0, 1))
            .put("will_sound_on", Random.Default.nextInt(0, 1))
            .put("is_on_screen", true)
            .put("timezone_offset", getZoneOffSet())
            .put("reason", "cold_start_fetch")
            .put("is_pull_to_refresh", "0")

        if ("is_pull_to_refresh" in options) {
            payload
                .put("reason", "pull_to_refresh")
                .put("is_pull_to_refresh", "1")
        }

        val header = mapOf("X-Ads-Opt-Out" to "0")

        return request.prepare(endpoint = Routes.timeline(maxId = maxId), payload = payload.toString(), header = header)
            .send()
    }

    // Get Reels(Stories)
    fun getReelsTrayFeed(reason: String = "pull_to_refresh"): Boolean {
        // reason can be = cold_start or pull_to_refresh
        val payload = JSONObject()
            .put("supported_capabilities_new", EXPERIMENTS.SUPPORTED_CAPABILITIES)
            .put("reason", reason)
            .put("_csrftoken", token)
            .put("_uuid", uuid)

        return request.prepare(endpoint = Routes.reelsTrayFeed(), payload = payload.toString()).send()
    }

    // Get suggested searches
    private fun getSuggestedSearches(type: String = "users"): Boolean {
        val payload = JSONObject()
            .put("type", type)

        return request.prepare(endpoint = Routes.suggestedSearches(), payload = payload.toString()).send()
    }

    // Get ranked recipients
    private fun getRankedRecipients(mode: String, showThreads: Boolean, query: String = ""): Boolean {
        val payload = JSONObject()
            .put("mode", mode)
            .put("show_threads", showThreads)
            .put("use_unified_inbox", "true")

        if (query.isNotEmpty()) {
            payload
                .put("query", query)
        }

        return request.prepare(endpoint = Routes.rankedRecipients(), payload = payload.toString()).send()
    }

    // Get Direct messages
    fun getInboxV2(): Boolean {
        val payload = JSONObject()
            .put("persistentBadging", true)
            .put("use_unified_inbox", true)

        return request.prepare(Routes.inboxV2(), payload = payload.toString()).send()
    }

    // Get presence
    private fun getPresence(): Boolean {
        return request.prepare(Routes.presence()).send()
    }

    // Get recent activity of user
    private fun getRecentActivity(): Boolean {
        return request.prepare(endpoint = Routes.recentActivity()).send()
    }

    fun getFollowingRecentActivity(): Boolean {
        return request.prepare(endpoint = "news").send()
    }

    fun getLoomFetchConfig(): Boolean {
        return request.prepare(endpoint = Routes.loomFetchConfig()).send()
    }

    fun getProfileNotice(): Boolean {
        return request.prepare(endpoint = Routes.profileNotice()).send()
    }

    fun getBatchFetch(): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", token)
            .put("_uid", userId)
            .put("_uuid", uuid)
            .put("scale", 3)
            .put("version", 1)
            .put("vc_policy", "default")
            .put("surfaces_to_triggers", EXPERIMENTS.SURFACES_TO_TRIGGERS)
            .put("surfaces_to_queries", EXPERIMENTS.SURFACES_TO_QUERIES)

        return request.prepare(endpoint = Routes.batchFetch(), payload = payload.toString()).send()
    }


    // ====== MEDIA METHODS ===== //

    fun editMedia(mediaId: String, caption: String = ""): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)
            .put("caption_text", caption)

        return request.prepare(endpoint = Routes.editMedia(mediaId = mediaId), payload = payload.toString()).send()
    }

    fun removeSelfTagFromMedia(mediaId: String): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)

        return request.prepare(
            endpoint = Routes.removeSelfTagFromMedia(mediaId = mediaId),
            payload = payload.toString()
        ).send()
    }

    fun getMediaInfo(mediaId: String): Boolean {
        return request.prepare(endpoint = Routes.mediaInfo(mediaId = mediaId)).send()
    }

    fun archiveMedia(mediaId: String, mediaType: Int, undo: Boolean = false): Boolean {
        val action = if (undo) "undo_only_me" else "only_me"
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)
            .put("media_id", mediaId)

        return request.prepare(
            endpoint = Routes.archiveMedia(
                mediaId = mediaId,
                action = action,
                mediaType = mediaType
            ), payload = payload.toString()
        ).send()
    }

    fun deleteMedia(mediaId: String): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)
            .put("media_id", mediaId)

        return request.prepare(endpoint = Routes.deleteMedia(mediaId = mediaId), payload = payload.toString()).send()
    }

    private fun generateUserBreadCrumb(size: Int): String {
        val key = "iN4\$aGr0m"
        val timeElapsed = Random.nextInt(500, 1500) + (size * Random.nextInt(500, 1500))
        val textChangeEventCount = max(1, (size / Random.nextInt(3, 5)))
        val dt: Long = System.currentTimeMillis() * 1000

        val payload = "$size $timeElapsed ${textChangeEventCount.toFloat()} $dt"
        val signedKeyAndData = Crypto.generateHMAC(
            key.toByteArray(Charsets.US_ASCII).toString(),
            payload.toByteArray(Charsets.US_ASCII).toString()
        )

        return "${Base64.getEncoder().encodeToString(signedKeyAndData.toByteArray())}\n${Base64.getEncoder()
            .encodeToString(
                payload.toByteArray(Charsets.US_ASCII)
            )}"
    }

    fun comment(mediaId: String, commentText: String): Boolean {
        val payload = JSONObject()
            .put("container_module", "comments_v2")
            .put("user_breadcrumb", generateUserBreadCrumb(commentText.length))
            .put("idempotence_token", Crypto.generateUUID(true))
            .put("comment_text", commentText)
            .put("radio_type", "wifi-none")
            .put("device_id", this.deviceId)
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)

        return request.prepare(endpoint = Routes.comment(mediaId = mediaId), payload = payload.toString()).send()
    }

    fun replyToComment(mediaId: String, parentCommentId: String, commentText: String): Boolean {
        val payload = JSONObject()
            .put("comment_text", commentText)
            .put("replied_to_comment_id", parentCommentId)

        return request.prepare(endpoint = Routes.comment(mediaId = mediaId), payload = payload.toString()).send()
    }


    fun deleteComment(mediaId: String, commentId: String): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)

        return request.prepare(
            endpoint = Routes.deleteComment(mediaId = mediaId, commentId = commentId),
            payload = payload.toString()
        ).send()
    }


    fun getCommentLiker(commentId: String): Boolean {
        return request.prepare(endpoint = Routes.commentLikers(commentId = commentId)).send()
    }

    fun getMediaLiker(mediaId: String): Boolean {
        return request.prepare(endpoint = Routes.mediaLikers(mediaId = mediaId)).send()
    }

    fun likeComment(commentId: String): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)
            .put("is_carousel_bumped_post", false)
            .put("container_module", "comments_v2")
            .put("feed_position", "0")

        return request.prepare(endpoint = Routes.likeComment(commentId = commentId), payload = payload.toString())
            .send()
    }

    fun unlikeComment(commentId: String): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)
            .put("is_carousel_bumped_post", false)
            .put("container_module", "comments_v2")
            .put("feed_position", "0")

        return request.prepare(endpoint = Routes.unlikeComment(commentId = commentId), payload = payload.toString())
            .send()
    }

    fun like(
            mediaId: String, doubleTap: Int = 0, containerModule: String = "feed_short_url",
            feedPosition: Int = 0, username: String = "", userId: String = "",
            hashTagName: String = "", hashTagId: String = "", entityPageName: String = "", entityPageId: String = ""
    ): Boolean {

        val payload = JSONObject()
            .put("radio_type", "wifi-none")
            .put("device_id", this.deviceId)
            .put("media_id", mediaId)
            .put("container_module", containerModule)
            .put("feed_position", feedPosition.toString())
            .put("is_carousel_bumped_post", "false")

        if (containerModule == "feed_timeline") {
            payload
                .put("inventory_source", "media_or_ad")
        }

        if (username.isNotEmpty()) {
            payload
                .put("username", username)
                .put("user_id", userId)
        }

        if (hashTagName.isNotEmpty()) {
            payload
                .put("hashtag_name", hashTagName)
                .put("hashtag_id", hashTagId)
        }

        if (entityPageName.isNotEmpty()) {
            payload
                .put("entity_page_name", entityPageName)
                .put("entity_page_id", entityPageId)
        }

        payload
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)
            .put("d=", Random.nextInt(0, 1).toString())

        val dt = if (doubleTap != 0) Random.nextInt(0, 1).toString() else doubleTap.toString()
        val extraSig = mutableMapOf("d=" to dt)

        return request.prepare(
            endpoint = Routes.like(mediaId = mediaId),
            payload = payload.toString(),
            extraSig = extraSig
        ).send()
    }

    fun unlike(mediaId: String): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)
            .put("media_id", mediaId)
            .put("radio_type", "wifi-none")
            .put("is_carousel_bumped_post", "false")
            .put("container_module", "photo_view_other")
            .put("feed_position", "0")

        return request.prepare(endpoint = Routes.unlike(mediaId = mediaId), payload = payload.toString()).send()
    }

    fun getMediaComments(mediaId: String, maxId: String = ""): Boolean {
        return request.prepare(endpoint = Routes.mediaComments(mediaId = mediaId, maxId = maxId)).send()
    }

    fun getExplore(isPrefetch: Boolean = false): Boolean {
        val payload = JSONObject()
            .put("is_prefetch", isPrefetch)
            .put("is_from_promote", false)
            .put("timezone_offset", getZoneOffSet())
            .put("session_id", Crypto.generateUUID(true))
            .put("supported_capabilities_new", EXPERIMENTS.SUPPORTED_CAPABILITIES)

        if (isPrefetch) {
            payload
                .put("max_id", 0)
                .put("module", "explore_popular")
        }

        return request.prepare(endpoint = Routes.explore(), payload = payload.toString()).send()
    }

    // Get auto complete user list
    fun getAutoCompleteUserList(): Boolean {
        return request.prepare(endpoint = Routes.autoCompleteUserList()).send()
    }

    fun getMegaPhoneLog(): Boolean {
        return request.prepare(endpoint = Routes.megaphoneLog()).send()
    }

    fun expose(): Boolean {
        val payload = JSONObject()
            .put("id", uuid)
            .put("experiment", "ig_android_profile_contextual_feed")

        return request.prepare(endpoint = Routes.expose(), payload = payload.toString()).send()
    }

    fun getUserInfoByName(username: String): Boolean {
        return request.prepare(endpoint = Routes.userInfoByName(username = username)).send()
    }

    fun getUserInfoByID(userId: String): Boolean {
        return request.prepare(endpoint = Routes.userInfoById(userId = userId)).send()
    }

    fun getUserTagMedias(userId: String): Boolean {
        return request.prepare(endpoint = Routes.userTags(userId = userId, rankToken = rankToken)).send()
    }

    fun getSelfUserTags(): Boolean {
        return getUserTagMedias(userId)
    }

    fun getGeoMedia(userId: String): Boolean {
        return request.prepare(endpoint = Routes.geoMedia(userId = userId)).send()
    }

    fun getSelfGeoMedia(): Boolean {
        return getGeoMedia(userId)
    }


    // ====== FEED METHODS ===== //

    private fun getUserFeed(userId: String, maxId: String = "", minTimeStamp: String = ""): Boolean {
        return request.prepare(
            endpoint = Routes.userFeed(
                userId = userId,
                maxId = maxId,
                minTimeStamp = minTimeStamp,
                rankToken = rankToken
            )
        ).send()
    }

    fun getHashTagFeed(hashTag: String, maxId: String = ""): Boolean {
        return request.prepare(endpoint = Routes.hashTagFeed(hashTag = hashTag, maxId = maxId, rankToken = rankToken))
            .send()
    }

    fun getLocationFeed(locationId: String, maxId: String = ""): Boolean {
        return request.prepare(
            endpoint = Routes.locationFeed(
                locationId = locationId,
                maxId = maxId,
                rankToken = rankToken
            )
        ).send()
    }

    fun getPopularFeeds(): Boolean {
        return request.prepare(endpoint = Routes.popularFeed(rankToken = rankToken)).send()
    }

    private fun getLikedMedia(maxId: String = ""): Boolean {
        return request.prepare(endpoint = Routes.likedFeed(maxId = maxId)).send()
    }


    // ====== FRIENDSHIPS METHODS ===== //
    private fun getUserFollowers(userId: String, maxId: String = ""): Boolean {
        return request.prepare(endpoint = Routes.userFollowers(userId = userId, maxId = maxId, rankToken = rankToken))
            .send()
    }

    private fun getUserFollowings(userId: String, maxId: String = ""): Boolean {
        return request.prepare(endpoint = Routes.userFollowings(userId = userId, maxId = maxId, rankToken = rankToken))
            .send()
    }

    private fun getSelfUserFollowings(): Boolean {
        return getUserFollowings(userId)
    }


    fun follow(userId: String): Boolean {
        val payload = JSONObject()
            .put("radio_type", "wifi-none")
            .put("device_id", this.deviceId)
            .put("user_id", userId)
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)

        return request.prepare(endpoint = Routes.follow(userId = userId), payload = payload.toString()).send()
    }

    fun unfollow(userId: String): Boolean {
        val payload = JSONObject()
            .put("radio_type", "wifi-none")
            .put("device_id", this.deviceId)
            .put("user_id", userId)
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)

        return request.prepare(endpoint = Routes.unfollow(userId = userId), payload = payload.toString()).send()
    }

    fun removeFollower(userId: String): Boolean {
        val payload = JSONObject()
            .put("user_id", userId)
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)

        return request.prepare(endpoint = Routes.removeFollower(userId = userId), payload = payload.toString()).send()
    }

    fun block(userId: String): Boolean {
        val payload = JSONObject()
            .put("user_id", userId)
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)

        return request.prepare(endpoint = Routes.block(userId = userId), payload = payload.toString()).send()
    }

    fun unblock(userId: String): Boolean {
        val payload = JSONObject()
            .put("user_id", userId)
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)

        return request.prepare(endpoint = Routes.unblock(userId = userId), payload = payload.toString()).send()
    }

    fun getUserFriendship(userId: String): Boolean {
        val payload = JSONObject()
            .put("user_id", userId)
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)

        return request.prepare(endpoint = Routes.userFriendship(userId = userId), payload = payload.toString()).send()
    }

    fun muteUser(userId: String, isMutePosts: Boolean = false, isMuteStory: Boolean = false): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)

        if (isMutePosts) {
            payload
                .put("target_posts_author_id", userId)
        }

        if (isMuteStory) {
            payload
                .put("target_reel_author_id", userId)
        }

        return request.prepare(endpoint = Routes.muteUser(), payload = payload.toString()).send()
    }

    fun getMutedUsers(mutedContentType: String): Boolean {
        if (mutedContentType != "stories") {
            throw NotImplementedError("API does not support getting friends with provided muted content type")
        }

        return request.prepare(endpoint = Routes.getMutedUser()).send()
    }

    fun unmuteUser(userId: String, isUnmutePosts: Boolean = false, isUnmuteStory: Boolean = false): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)

        if (isUnmutePosts) {
            payload
                .put("target_posts_author_id", userId)
        }

        if (isUnmuteStory) {
            payload
                .put("target_reel_author_id", userId)
        }

        return request.prepare(endpoint = Routes.unmuteUser(), payload = payload.toString()).send()
    }

    fun getPendingFriendRequests(): Boolean {
        return request.prepare(endpoint = Routes.pendingFriendRequests()).send()
    }

    fun approvePendingFollowRequest(userId: String): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)
            .put("user_id", userId)

        return request.prepare(
            endpoint = Routes.approvePendingFollowRequest(userId = userId),
            payload = payload.toString()
        ).send()
    }

    fun rejectPendingFollowRequest(userId: String): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)
            .put("user_id", userId)

        return request.prepare(
            endpoint = Routes.rejectPendingFollowRequest(userId = userId),
            payload = payload.toString()
        ).send()
    }

    fun getDirectShare(): Boolean {
        return request.prepare(endpoint = Routes.directShare()).send()
    }

    private fun getTotalFollowersOrFollowings(
            userId: String, amount: Int = Int.MAX_VALUE, isFollower: Boolean = true, isUsername: Boolean = false,
            isFilterPrivate: Boolean = false, isFilterVerified: Boolean = false, fileNameToWrite: String = "",
            isOverwrite: Boolean = false
    ): Flow<String> = flow {

        val userType = if (isFollower) "follower_count" else "following_count"
        val userKey = if (isUsername) "username" else "pk"
        var nextMaxId = ""
        var sleepTrack = 0
        var counter = 0
        val total: Int
        val isWriteToFile = fileNameToWrite.isNotEmpty()
        val userInfo: JsonResult?

        getUserInfoByID(userId).let { userInfo = lastJSON }

        val user = userInfo?.read<JSONObject>("$.user")

        if (user != null) {

            if (user.read<Boolean>("$.is_private") == true) {
                return@flow
            }
            total = min(amount, user.read<Int>("$.${userType}")!!)

            if (total >= 20000) {
                println("Consider saving the result in file. This operation will take time")
            }
        } else {
            return@flow
        }

        if (isWriteToFile) {
            if (File(fileNameToWrite).exists()) {
                if (!isOverwrite) {
                    println("File $fileNameToWrite already exist. Not overwriting")
                    return@flow
                } else {
                    println("Overwriting $fileNameToWrite file")
                }
            }

            withContext(Dispatchers.IO) {
                File(fileNameToWrite).createNewFile()
            }

        }

        val type = if (isFollower) "Followers" else "Following"
        println("Getting $type of $userId")
        val br = if (isWriteToFile) File(fileNameToWrite).bufferedWriter() else null
        while (true) {
            if (isFollower) {
                getUserFollowers(userId, nextMaxId)
            } else {
                getUserFollowings(userId, nextMaxId)
            }

            lastJSON?.read<JSONArray>("$.users")?.forEach {
                val obj = it as JSONObject
                if (isFilterPrivate && obj.read<Boolean>("$.is_private") == true) {
                    return@forEach
                }
                if (isFilterVerified && obj.read<Boolean>("$.is_verified") == true) {
                    return@forEach
                }

                val key = obj.get(userKey).toString()
                emit(key)
                counter += 1

                if (isWriteToFile) {
                    br?.appendln(key)
                }

                if (counter >= total) {
                    withContext(Dispatchers.IO) {
                        br?.close()
                    }
                    return@flow
                }

                sleepTrack += 1
                if (sleepTrack >= 5000) {
                    val sleepTime = Random.nextLong(120, 180)
                    println("Waiting %.2f minutes due to too many requests.".format((sleepTime.toFloat() / 60)))
                    delay(sleepTime * 1000)
                    sleepTrack = 0
                }
            }

            if (lastJSON?.read<Boolean>("$.big_list") == false) {
                withContext(Dispatchers.IO) {
                    br?.close()
                }
                return@flow
            }

            nextMaxId =
                if (isFollower) lastJSON?.read<String>("$.next_max_id")
                    .toString() else lastJSON?.read<Int>("$.next_max_id").toString()
        }
    }

    fun getTotalFollowers(
            userId: String, amountOfFollowers: Int = Int.MAX_VALUE, isUsername: Boolean = false,
            isFilterPrivate: Boolean = false, isFilterVerified: Boolean = false, fileNameToWrite: String = "",
            isOverwrite: Boolean = false
    ): Flow<String> {
        return getTotalFollowersOrFollowings(
            userId = userId, amount = amountOfFollowers, isFollower = true, isUsername = isUsername,
            isFilterPrivate = isFilterPrivate, isFilterVerified = isFilterVerified, fileNameToWrite = fileNameToWrite,
            isOverwrite = isOverwrite
        )
    }

    fun getTotalFollowing(
            userId: String, amountOfFollowing: Int = Int.MAX_VALUE, isUsername: Boolean = false,
            isFilterPrivate: Boolean = false, isFilterVerified: Boolean = false, fileNameToWrite: String = "",
            isOverwrite: Boolean = false
    ): Flow<String> {
        return getTotalFollowersOrFollowings(
            userId = userId, amount = amountOfFollowing, isFollower = false, isUsername = isUsername,
            isFilterPrivate = isFilterPrivate, isFilterVerified = isFilterVerified, fileNameToWrite = fileNameToWrite,
            isOverwrite = isOverwrite
        )
    }

    fun getLastUserFeed(userId: String, amount: Int, minTimeStamp: String = ""): Flow<JSONObject> = flow {
        var counter = 0
        var nextMaxId = ""

        while (true) {
            if (getUserFeed(userId = userId, maxId = nextMaxId, minTimeStamp = minTimeStamp)) {
                val items = lastJSON?.read<JSONArray>("$.items")

                if (items != null) {
                    items.forEach {
                        emit(it as JSONObject)
                        counter += 1
                        if (counter >= amount) {
                            return@flow
                        }
                    }
                } else {
                    return@flow
                }

                if (lastJSON?.read<Boolean>("$.more_available") == false) {
                    return@flow
                }

                nextMaxId = lastJSON?.read<String>("$.next_max_id")!!
            } else {
                return@flow
            }
        }
    }

    fun getTotalHashTagMedia(hashTag: String, amount: Int = 10): Flow<JSONObject> = flow {
        var counter = 0
        var nextMaxId = ""

        while (true) {
            if (getHashTagFeed(hashTag = hashTag, maxId = nextMaxId)) {
                val rankedItems = lastJSON?.read<JSONArray>("$.ranked_items")
                rankedItems?.forEach {
                    emit(it as JSONObject)
                    counter += 1
                    if (counter >= amount) {
                        return@flow
                    }
                }
                val items = lastJSON?.read<JSONArray>("$.items")
                items?.forEach {
                    emit(it as JSONObject)
                    counter += 1
                    if (counter >= amount) {
                        return@flow
                    }
                }

                if (lastJSON?.read<Boolean>("$.more_available") == false) {
                    return@flow
                }

                nextMaxId = lastJSON?.read<String>("$.next_max_id")!!
            } else {
                return@flow
            }
        }
    }

    fun getTotalHashTagUsers(hashTag: String, amount: Int = 10): Flow<JSONObject> = flow {
        var counter = 0
        var nextMaxId = ""

        while (true) {
            if (getHashTagFeed(hashTag = hashTag, maxId = nextMaxId)) {
                val rankedItems = lastJSON?.read<JSONArray>("$.ranked_items")
                rankedItems?.forEach { it ->
                    val item = it as JSONObject
                    item.read<JSONObject>("$.user")?.let { emit(it) }
                    counter += 1
                    if (counter >= amount) {
                        return@flow
                    }
                }
                val items = lastJSON?.read<JSONArray>("$.items")
                items?.forEach { it ->
                    val item = it as JSONObject
                    item.read<JSONObject>("$.user")?.let { emit(it) }
                    counter += 1
                    if (counter >= amount) {
                        return@flow
                    }
                }

                if (lastJSON?.read<Boolean>("$.more_available") == false) {
                    return@flow
                }

                nextMaxId = lastJSON?.read<String>("$.next_max_id")!!
            } else {
                return@flow
            }
        }
    }

    fun getTotalLikedMedia(amount: Int): Flow<JSONObject> = flow {
        var counter = 0
        var nextMaxId = ""

        while (true) {
            if (getLikedMedia(maxId = nextMaxId)) {
                val items = lastJSON?.read<JSONArray>("$.items")
                items?.forEach {
                    emit(it as JSONObject)
                    counter += 1
                    if (counter >= amount) {
                        return@flow
                    }
                }

                if (lastJSON?.read<Boolean>("$.more_available") == false) {
                    return@flow
                }

                nextMaxId = lastJSON?.read<String>("$.next_max_id")!!
            } else {
                return@flow
            }
        }
    }

    fun changePassword(newPassword: String): Boolean {
        val payload = JSONObject()
            .put("old_password", this.password)
            .put("new_password1", newPassword)
            .put("new_password2", newPassword)

        return request.prepare(endpoint = Routes.changePassword(), payload = payload.toString()).send(true)
    }

    fun removeProfilePicture(): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)

        return request.prepare(endpoint = Routes.removeProfilePicture(), payload = payload.toString()).send()
    }

    fun setAccountPrivate(): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)

        return request.prepare(endpoint = Routes.setAccountPrivate(), payload = payload.toString()).send()
    }

    fun setAccountPublic(): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)

        return request.prepare(endpoint = Routes.setAccountPublic(), payload = payload.toString()).send()
    }

    fun setNameAndPhone(name: String = "", phone: String = ""): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)
            .put("first_name", name)
            .put("phone_number", phone)

        return request.prepare(endpoint = Routes.setNameAndPhone(), payload = payload.toString()).send()
    }

    fun getProfileData(): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)

        return request.prepare(endpoint = Routes.profileData(), payload = payload.toString()).send()
    }

    fun editProfile(
            url: String = "",
            phone: String,
            firstName: String,
            biography: String,
            email: String,
            gender: Int
    ): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)
            .put("external_url", url)
            .put("phone_number", phone)
            .put("username", this.username)
            .put("full_name", firstName)
            .put("biography", biography)
            .put("email", email)
            .put("gender", gender)

        return request.prepare(endpoint = Routes.editAccount(), payload = payload.toString()).send()
    }

    fun searchUsers(userName: String): Boolean {
        return request.prepare(endpoint = Routes.searchUser(userName = userName, rankToken = this.rankToken)).send()
    }

    fun searchHashTags(hashTagName: String, amount: Int = 50): Boolean {
        return request.prepare(
            endpoint = Routes.searchHashTag(
                hashTagName = hashTagName,
                amount = amount,
                rankToken = this.rankToken
            )
        ).send()
    }

    fun searchLocations(locationName: String, amount: Int = 50): Boolean {
        return request.prepare(
            endpoint = Routes.searchLocation(
                locationName = locationName,
                amount = amount,
                rankToken = this.rankToken
            )
        ).send()
    }

    fun getUserReel(userId: String): Boolean {
        return request.prepare(endpoint = Routes.userReel(userId = userId)).send()
    }

    fun getUsersReel(userIds: List<String>): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", token)
            .put("_uid", userId)
            .put("_uuid", uuid)
            .put("user_ids", userIds)

        return request.prepare(endpoint = Routes.multipleUsersReel(), payload = payload.toString()).send()
    }

    fun watchReels(reels: List<JSONObject>): Boolean {
        val storySeen: MutableMap<String, List<String>> = mutableMapOf()
        val currentTime = System.currentTimeMillis()
        val reverseSortedReels = reels.sortedByDescending { it.get("taken_at").toString() }
        //it.read<Long>("$.taken_at")

        for ((index, story) in reverseSortedReels.withIndex()) {
            val storySeenAt = currentTime - min(
                index + 1 + Random.nextLong(0, 2),
                max(0, currentTime - story.get("taken_at").toString().toLong())
            )
            storySeen["${story.get("id")}_${story.read<JSONObject>("$.user")?.get("pk").toString()}"] =
                listOf("${story.get("taken_at")}_${storySeenAt}")
//            storySeen["${JEToString(story["id"])}_${JEToString(story["user"]?.get("pk"))}"] = listOf("${JEToString(story["taken_at"])}_${storySeenAt}")
        }

        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)
            .put("reels", storySeen as Map<String, Any>?)

        return request.prepare(
            endpoint = Routes.watchReels(),
            payload = payload.toString(),
            API_URL = "https://i.instagram.com/api/v2/"
        ).send()
    }

    fun getUserStories(userId: String): Boolean {
        return request.prepare(endpoint = Routes.userStories(userId = userId)).send()
    }

    fun getSelfStoryViewers(storyId: String): Boolean {
        return request.prepare(endpoint = Routes.selfStoryViewers(storyId = storyId)).send()
    }

    fun getIGTVSuggestions(): Boolean {
        return request.prepare(endpoint = Routes.igtvSuggestions()).send()
    }

    fun getHashTagStories(hashTag: String): Boolean {
        return request.prepare(endpoint = Routes.hashTagStories(hashTag = hashTag)).send()
    }

    fun followHashTag(hashTag: String): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)

        return request.prepare(endpoint = Routes.followHashTag(hashTag = hashTag), payload = payload.toString()).send()
    }

    fun unfollowHashTag(hashTag: String): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)

        return request.prepare(endpoint = Routes.unfollowHashTag(hashTag = hashTag), payload = payload.toString())
            .send()
    }

    fun getTagsFollowedByUser(userId: String): Boolean {
        return request.prepare(endpoint = Routes.tagsFollowedByUser(userId = userId)).send()
    }

    fun getHashTagSelection(hashTag: String): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)
            .put("supported_tabs", "['top','recent','places']")
            .put("include_persistent", "true")

        return request.prepare(endpoint = Routes.hashTagSelection(hashTag = hashTag), payload = payload.toString())
            .send()
    }

    fun getMediaInsight(mediaId: String): Boolean {
        return request.prepare(endpoint = Routes.mediaInsight(mediaId = mediaId)).send()
    }

    fun getSelfInsight(): Boolean {
        return request.prepare(endpoint = Routes.selfInsight()).send()
    }

    fun saveMedia(mediaId: String, moduleName: String = "feed_timeline"): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)
            .put("radio_type", "wifi-none")
            .put("device_id", this.deviceId)
            .put("module_name", moduleName)

        return request.prepare(endpoint = Routes.saveMedia(mediaId = mediaId), payload = payload.toString()).send()
    }

    fun unsaveMedia(mediaId: String): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)

        return request.prepare(endpoint = Routes.unsaveMedia(mediaId = mediaId), payload = payload.toString()).send()
    }

    fun getSavedMedias(): Boolean {
        return request.prepare(endpoint = Routes.getSavedMedia()).send()
    }


    // ====== DIRECT(DM) METHODS ===== //

    fun sendDirectItem(itemType: String, users: List<String>, options: Map<String, String>? = null): Boolean {

        if (!isLoggedIn) {
            throw LoginException("Not logged in")
        }

        val payload: MutableMap<String, Any?> = mutableMapOf(
            "_csrftoken" to this.token,
            "_uid" to this.userId,
            "_uuid" to this.uuid,
            "client_context" to Crypto.generateUUID(true),
            "action" to "send_item",
            "recipient_users" to "[[${users.joinToString(separator = ",")}]]"
        )

        val header = mutableMapOf<String, String>()

        var endpoint = Routes.directItem(itemType = itemType)

        val text = if (options?.get("text")?.isNotEmpty() == true) options["text"] else ""

        if (options?.get("threadId")?.isNotEmpty() == true) {
            payload["thread_ids"] = options["threadId"]
        }

        if (itemType == "text") {
            payload["text"] = text
        } else if (itemType == "link" && options?.get("urls")?.isNotEmpty() == true) {
            payload["link_text"] = text
            payload["link_urls"] = options["urls"]
        } else if (itemType == "media_share" && options?.get("media_type")
                    ?.isNotEmpty() == true && options.get("media_id")?.isNotEmpty() == true
        ) {
            payload["text"] = text
            payload["media_type"] = options["media_type"]?.toInt()
            payload["media_id"] = options["media_id"]
        } else if (itemType == "hashtag" && options?.get("hashtag")?.isNotEmpty() == true) {
            payload["text"] = text
            payload["hashtag"] = options["hashtag"]
        } else if (itemType == "profile" && options?.get("profile_user_id")?.isNotEmpty() == true) {
            payload["text"] = text
            payload["profile_user_id"] = options["profile_user_id"]
        } else if (itemType == "photo" && options?.get("filePath")?.isNotEmpty() == true) {
            endpoint = Routes.directPhoto()
            val filePath = options["filePath"]
            val uploadId = (System.currentTimeMillis() * 1000).toString()
            val file = File(filePath!!)
            val photo = ByteArray(file.length().toInt())
            file.inputStream().read(photo)
            val photoData = listOf(
                "direct_temp_photo_${uploadId}.jpg",
                Base64.getEncoder().encodeToString(photo),
                "application/octet-stream",
                mapOf("Content-Transfer-Encoding" to "binary")
            )
            payload["photo"] = photoData
            payload["photo"] = photoData
            header["Content-type"] = "multipart/form-data"
        }

        val url = "${HTTP.API_URL}$endpoint"
        // Need to send separate request as it doesn't require signature
        request.prepare(endpoint = endpoint, payload = payload.toString(), header = header)
        val response = post(
            url,
            headers = request.headers,
            data = payload,
            cookies = request.persistedCookies,
            allowRedirects = true
        )

        lastResponse = response
        statusCode = lastResponse.statusCode

        return if (response.statusCode == 200) {
            lastJSON = JsonPath.parseOrNull(response.text)
            true
        } else {
            println("Failed to send item")
            false
        }
    }

    fun getPendingInbox(): Boolean {
        return request.prepare(endpoint = Routes.pendingInbox()).send()
    }

    fun getPendingThreads(): Flow<JSONObject> = flow {
        getPendingInbox()
        lastJSON?.read<JSONObject>("$.inbox")?.read<JSONArray>("$.threads")?.forEach {
            emit(it as JSONObject)
        }
    }

    fun approvePendingThread(threadId: String): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)

        return request.prepare(
            endpoint = Routes.approvePendingThread(threadId = threadId),
            payload = payload.toString()
        ).send()
    }

    fun hidePendingThread(threadId: String): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)

        return request.prepare(endpoint = Routes.hidePendingThread(threadId = threadId), payload = payload.toString())
            .send()
    }

    fun rejectPendingThread(threadId: String): Boolean {
        val payload = JSONObject()
            .put("_csrftoken", this.token)
            .put("_uid", this.userId)
            .put("_uuid", this.uuid)

        return request.prepare(
            endpoint = Routes.declinePendingThread(threadId = threadId),
            payload = payload.toString()
        ).send()
    }

    // ====== DOWNLOAD(PHOTO/VIDEO/STORY) METHODS ===== //

    fun downloadMedia(url: String, username: String, folderName: String, fileName: String): Boolean {
        val directory = File("$folderName/$username")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val file = File(directory, fileName)
        if (file.exists()) {
            println("media already exist")
            return true
        }

        request.prepare(endpoint = "")
        val response = get(url = url, headers = request.headers, cookies = request.persistedCookies, stream = true)
        return if (response.statusCode == 200) {
            response.contentIterator(chunkSize = 1024).forEach {
                file.appendBytes(it)
            }
            true
        } else {
            println("Failed to download media: ${response.text}")
            false
        }
    }

}