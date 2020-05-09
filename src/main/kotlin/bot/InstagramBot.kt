package bot

import api.InstagramAPI
import com.nfeld.jsonpathlite.JsonResult
import com.nfeld.jsonpathlite.extension.read
import khttp.responses.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.*
import kotlin.random.Random

class InstagramBot(
        maxLikesPerDay: Int = 1000,
        maxUnlikesPerDay: Int = 1000,
        maxFollowsPerDay: Int = 350,
        maxUnfollowsPerDay: Int = 350,
        maxCommentsPerDay: Int = 100,
        maxBlocksPerDay: Int = 100,
        maxUnblocksPerDay: Int = 100,
        maxMessagesPerDay: Int = 300,
        val blockedActionProtection: Boolean = true,
        val blockedActionSleep: Boolean = false,
        val blockedActionSleepDelay: Int = 300
) {

    val minSleepTime = 60
    val maxSleepTime = 120

    val api = InstagramAPI

    var startTime = Date()

    private val actions: List<String> = listOf(
        "likes", "unlikes", "follows", "unfollows", "comments",
        "blocks", "unblocks", "messages", "archived", "unarchived", "stories_viewed"
    )

    private val totalActionPerformed: MutableMap<String, Int> = actions.map { it to 0 }.toMap().toMutableMap()
    private var blockedActions = actions.map { it to false }.toMap().toMutableMap()
    private var sleepingActions = actions.map { it to false }.toMap().toMutableMap()
    private var maxActionPerDays: Map<String, Int> = mutableMapOf(
        "likes" to maxLikesPerDay, "unlikes" to maxUnlikesPerDay,
        "follows" to maxFollowsPerDay, "unfollows" to maxUnfollowsPerDay, "comments" to maxCommentsPerDay,
        "blocks" to maxBlocksPerDay, "unblocks" to maxUnblocksPerDay, "messages" to maxMessagesPerDay
    )


    val username: String
        get() = api.username
    val password: String
        get() = api.password
    val userId: String
        get() = api.userId
    val statusCode: Int
        get() = api.statusCode
    val lastJson: JsonResult?
        get() = api.lastJSON
    val lastResponse: Response
        get() = api.lastResponse

    private fun resetCounters() {
        totalActionPerformed.replaceAll { t, u -> 0 }
        blockedActions.replaceAll { _, _ -> false }
        startTime = Date()
    }

    private fun reachedLimit(key: String): Boolean {
        val currentTime = Date()
        val passedDays = currentTime.compareTo(startTime)
        if (passedDays > 0) {
            resetCounters()
        }

        return (maxActionPerDays.getValue(key) - totalActionPerformed.getValue(key)) <= 0
    }

    fun prepare(username: String, password: String) {
        api.username = username
        api.password = password
        api.prepare()
    }

    fun login(forceLogin: Boolean = false): Boolean {
        return api.login(forceLogin = forceLogin)
    }

    fun logout(): Boolean {
        return api.logout()
    }

    // === USER INFO METHODS === //
    fun getUserInfoByID(username: String): JSONObject? {
        api.getUserInfoByID(username)
        return api.lastJSON?.read<JSONObject>("$.user")

    }

    fun getUserInfoByName(username: String): JSONObject? {
        api.getUserInfoByName(username)
        return api.lastJSON?.read<JSONObject>("$.user")
    }

    private fun getUserIdByName(username: String): String {
        return api.getUserIdByName(username)
    }


    private fun convertToUserId(value: String): String {
        return if (value.toLongOrNull() != null) value else {
            getUserIdByName(value.replace("@", ""))
        }
    }

    // === FOLLOWER/FOLLOWING METHODS === //
    fun getSelfFollowing(
            amountOfFollowing: Int = Int.MAX_VALUE, isUsername: Boolean = false,
            isFilterPrivate: Boolean = false, isFilterVerified: Boolean = false, fileNameToWrite: String = "",
            isOverwrite: Boolean = false
    ): Flow<String> {
        return api.getTotalFollowing(
            userId,
            amountOfFollowing,
            isUsername,
            isFilterPrivate,
            isFilterVerified,
            fileNameToWrite,
            isOverwrite
        )
    }

    fun getSelfFollowers(
            amountOfFollowers: Int = Int.MAX_VALUE, isUsername: Boolean = false,
            isFilterPrivate: Boolean = false, isFilterVerified: Boolean = false, fileNameToWrite: String = "",
            isOverwrite: Boolean = false
    ): Flow<String> {
        return api.getTotalFollowers(
            userId,
            amountOfFollowers,
            isUsername,
            isFilterPrivate,
            isFilterVerified,
            fileNameToWrite,
            isOverwrite
        )

    }

    fun getUserFollowing(
            username: String, amountOfFollowing: Int = Int.MAX_VALUE, isUsername: Boolean = false,
            isFilterPrivate: Boolean = false, isFilterVerified: Boolean = false, fileNameToWrite: String = "",
            isOverwrite: Boolean = false
    ): Flow<String> {
        return api.getTotalFollowing(
            convertToUserId(username),
            amountOfFollowing,
            isUsername,
            isFilterPrivate,
            isFilterVerified,
            fileNameToWrite,
            isOverwrite
        )
    }

    fun getUserFollowers(
            username: String, amountOfFollowers: Int = Int.MAX_VALUE, isUsername: Boolean = false,
            isFilterPrivate: Boolean = false, isFilterVerified: Boolean = false, fileNameToWrite: String = "",
            isOverwrite: Boolean = false
    ): Flow<String> {
        return api.getTotalFollowers(
            convertToUserId(username),
            amountOfFollowers,
            isUsername,
            isFilterPrivate,
            isFilterVerified,
            fileNameToWrite,
            isOverwrite
        )
    }

    // === USER STORIES METHODS === //
    fun getUserStoriesURL(username: String): Flow<String> = flow {
        api.getUserReel(convertToUserId(username))
        api.lastJSON?.read<Int>("$.media_count")?.let { it ->
            if (it > 0) {
                val items = api.lastJSON?.read<JSONArray>("$.items")
                items?.forEach {
                    if ((it as JSONObject).read<Int>("$.media_type") == 1) {
                        (it?.read<JSONObject>("$.image_versions2")?.read<JSONArray>("$.candidates")
                            ?.first() as JSONObject)?.read<String>(
                            "$.url"
                        )?.let { emit(it) }
                    } else if (it.read<Int>("$.media_type") == 2) {
                        (it?.read<JSONArray>("$.video_versions")?.first() as JSONObject)?.read<String>("$.url")
                            ?.let { emit(it) }
                    }
                }
            }
        }
    }

    // Emit all info about users's story
    fun getUsersStories(usernames: List<String>): Flow<JSONObject> = flow {
        api.getUsersReel(usernames.map { convertToUserId(it) })
        val reels = api.lastJSON?.read<JSONObject>("$.reels")
        reels?.keySet()?.forEach {
            val story = reels?.read<JSONObject>("$.${it}")!!
            if (story.has("items") && story?.read<JSONArray>("$.items")?.length()!! > 0) {
                emit(story)
            }
        }
    }

    // Emit all story items of user individually
    fun getUsersStoriesItems(usernames: List<String>): Flow<JSONObject> = flow {
        getUsersStories(usernames).collect {
            it?.read<JSONArray>("$.items")?.forEach {
                emit(it as JSONObject)
            }
        }
    }

    fun getSelfStoryViewers(): Flow<JSONObject> = flow {
        getUsersStoriesItems(listOf(username)).collect { it ->
            api.getSelfStoryViewers(it.get("id").toString())
            api.lastJSON?.read<JSONArray>("$.users")?.forEach {
                emit(it as JSONObject)
            }
        }
    }


    // === MEDIA METHODS === //
    fun getSavedMedias(): Flow<JSONObject> = flow {
        api.getSavedMedias()
        api.lastJSON?.read<JSONArray>("$.items")?.forEach { it ->
            (it as JSONObject)?.read<JSONObject>("$.media")?.let { emit(it) }
        }
    }

    fun getExploreTabMedias(amount: Int = 5): Flow<JSONObject> = flow {
        var counter = 0
        api.getExplore()
        api.lastJSON?.read<JSONArray>("$.items")?.forEach { it ->
            (it as JSONObject)?.read<JSONObject>("$.media")?.let {
                emit(it)
                counter += 1
                if (counter >= amount) {
                    return@flow
                }
            }
        }
    }


    // === USER METHODS === //
    fun getExploreTabUsers(amount: Int = 10): Flow<JSONObject> = flow {
        var counter = 0
        api.getExplore()
        api.lastJSON?.read<JSONArray>("$.items")?.forEach { it ->
            (it as JSONObject)?.read<JSONObject>("$.media")?.read<JSONObject>("$.user")?.let {
                emit(it)
                counter += 1
                if (counter >= amount) {
                    return@flow
                }
            }
        }
    }

    fun searchLocations(locationName: String, amount: Int = 5): Flow<JSONObject> = flow {
        api.searchLocations(locationName, amount)
        api.lastJSON?.read<JSONArray>("$.items")?.forEach { it ->
            (it as JSONObject)?.read<JSONObject>("$.location")?.let { emit(it) }
        }
    }

    fun getUsersByLocation(locationName: String, amount: Int = 5): Flow<JSONObject> = flow {
        var counter = 0
        var nextMaxId = ""

        val locationIds = searchLocations(locationName, amount).toList()
        locationIds.forEach { location ->
            api.getLocationFeed(location.get("pk").toString(), nextMaxId)

            val rankedItems = api.lastJSON?.read<JSONArray>("$.ranked_items")
            rankedItems?.forEach { rankedItem ->
                (rankedItem as JSONObject)?.read<JSONObject>("$.user")?.let {
                    emit(it)
                    counter += 1
                    if (counter >= amount) {
                        return@flow
                    }
                }
            }

            val items = api.lastJSON?.read<JSONArray>("$.items")
            items?.forEach { item ->
                (item as JSONObject)?.read<JSONObject>("$.user")?.let {
                    emit(it)
                    counter += 1
                    if (counter >= amount) {
                        return@flow
                    }
                }
            }


            api.lastJSON?.read<Boolean>("$.more_available")?.let { if (!it) return@flow }
            api.lastJSON?.read<String>("$.next_max_id")?.let { nextMaxId = it }
        }
    }

    fun getUsersTaggedInLocation(locationName: String, amount: Int = 5): Flow<JSONObject> = flow {
        var counter = 0
        var nextMaxId = ""

        val locationIds = searchLocations(locationName, amount).toList()
        locationIds.forEach { location ->
            api.getLocationFeed(location.get("pk").toString(), nextMaxId)

            val rankedItems = api.lastJSON?.read<JSONArray>("$.ranked_items")
            rankedItems?.forEach { rankedItem ->
                (rankedItem as JSONObject)?.read<JSONObject>("$.usertags")?.read<JSONArray>("$.in")
                    ?.read<JSONObject>("$.user")?.let {
                        emit(it)
                        counter += 1
                        if (counter >= amount) {
                            return@flow
                        }
                    }
            }

            val items = api.lastJSON?.read<JSONArray>("$.items")
            items?.forEach { item ->
                (item as JSONObject)?.read<JSONObject>("$.usertags")?.read<JSONArray>("$.in")
                    ?.read<JSONObject>("$.user")?.let {
                        emit(it)
                        counter += 1
                        if (counter >= amount) {
                            return@flow
                        }
                    }
            }

            api.lastJSON?.read<Boolean>("$.more_available")?.let { if (!it) return@flow }
            api.lastJSON?.read<String>("$.next_max_id")?.let { nextMaxId = it }
        }
    }

    fun getMediasByLocation(locationName: String, amount: Int = 5): Flow<JSONObject> = flow {
        var counter = 0
        var nextMaxId = ""

        val locationIds = searchLocations(locationName, amount).toList()
        locationIds.forEach { location ->

            api.getLocationFeed(location.get("pk").toString(), nextMaxId)

            val rankedItems = api.lastJSON?.read<JSONArray>("$.ranked_items")
            rankedItems?.forEach { rankedItem ->
                emit(rankedItem as JSONObject)
                counter += 1
                if (counter >= amount) {
                    return@flow
                }

            }

            val items = api.lastJSON?.read<JSONArray>("$.items")
            items?.forEach { item ->
                emit(item as JSONObject)
                counter += 1
                if (counter >= amount) {
                    return@flow
                }

            }


            api.lastJSON?.read<Boolean>("$.more_available")?.let { if (!it) return@flow }
            api.lastJSON?.read<String>("$.next_max_id")?.let { nextMaxId = it }
        }
    }

    // === ACCOUNT METHODS === //
    fun setAccountPublic(): Boolean {
        api.setAccountPublic()
        return api.lastJSON?.read<JSONObject>("$.user")?.read<Boolean>("$.is_private") == false
    }

    fun setAccountPrivate(): Boolean {
        api.setAccountPrivate()
        return api.lastJSON?.read<JSONObject>("$.user")?.read<Boolean>("$.is_private") == true
    }

    fun getProfileData(): JSONObject? {
        api.getProfileData()
        return api.lastJSON?.read<JSONObject>("$.user")
    }

    fun editProfile(
            url: String = "", phone: String, firstName: String, biography: String,
            email: String, gender: Int
    ): JSONObject? {
        api.editProfile(url, phone, firstName, biography, email, gender)
        return api.lastJSON?.read<JSONObject>("$.user")
    }

    fun getPendingFollowRequests(): Flow<JSONObject> = flow {
        api.getPendingFriendRequests()
        api.lastJSON?.read<JSONArray>("$.users")?.forEach { emit(it as JSONObject) }
    }

    fun getSelfUserMedias(): Flow<JSONObject> = flow {
        api.getSelfUserFeed()
        api.lastJSON?.read<JSONArray>("$.items")?.forEach { emit(it as JSONObject) }
    }

    fun getTimelineMedias(amount: Int = 8): Flow<JSONObject> = flow {
        var counter = 0
        var nextMaxId = ""

        while (true) {
            if (api.getTimeline(maxId = nextMaxId)) {
                val feedItems = api.lastJSON?.read<JSONArray>("$.feed_items")
                feedItems?.forEach { it ->
                    (it as JSONObject)?.read<JSONObject>("$.media_or_ad")?.let {
                        emit(it)
                        counter += 1
                        if (counter >= amount) {
                            return@flow
                        }
                    }
                }

                api.lastJSON?.read<Boolean>("$.more_available")?.let { if (!it) return@flow }
                api.lastJSON?.read<String>("$.next_max_id")?.let { nextMaxId = it }
            } else {
                return@flow
            }
        }
    }

    fun getTotalUserMedias(username: String): Flow<JSONObject> {
        return api.getTotalUserFeed(convertToUserId(username))
    }

    fun getTotalSelfMedias(): Flow<JSONObject> {
        return api.getTotalUserFeed(userId)
    }

    @ExperimentalCoroutinesApi
    fun getLastUserMedias(username: String, amount: Int): Flow<JSONObject> {
        return api.getLastUserFeed(convertToUserId(username), amount)
    }

    fun getHashTagMedias(hashTag: String, amount: Int): Flow<JSONObject> {
        return api.getTotalHashTagMedia(hashTag, amount)
    }

    fun getLikedMedias(amount: Int): Flow<JSONObject> {
        return api.getTotalLikedMedia(amount)
    }

    fun getMediaInfo(mediaId: String): JSONObject? {
        api.getMediaInfo(mediaId)
        return api.lastJSON?.read<JSONArray>("$.items")?.first() as JSONObject
    }

    fun getTimelineUsers(): Flow<JSONObject> = flow {
        api.getTimeline()
        val feedItems = api.lastJSON?.read<JSONArray>("$.feed_items")
        feedItems?.forEach { it ->
            (it as JSONObject)?.read<JSONObject>("$.media_or_ad")?.read<JSONObject>("$.user")?.let {
                emit(it)
            }
        }
    }

    fun getHashTagUsers(hashTag: String, amount: Int = 10): Flow<JSONObject> {
        return api.getTotalHashTagUsers(hashTag, amount)
    }

    fun getUserTagMedias(username: String): Flow<JSONObject> = flow {
        api.getUserTagMedias(convertToUserId(username))
        api.lastJSON?.read<JSONArray>("$.items")?.forEach {
            emit(it as JSONObject)
        }
    }

    fun getMediaComments(mediaId: String, amount: Int = 5): Flow<JSONObject> = flow {
        var counter = 0
        var nextMaxId = ""

        while (true) {
            if (api.getMediaComments(mediaId, nextMaxId)) {
                api.lastJSON?.read<JSONArray>("$.comments")?.forEach {
                    emit(it as JSONObject)
                    counter += 1
                    if (counter >= amount) {
                        return@flow
                    }
                }

                api.lastJSON?.read<Boolean>("$.has_more_comments")?.let { if (!it) return@flow }
                api.lastJSON?.read<String>("$.next_max_id")?.let { nextMaxId = it }
            } else {
                return@flow
            }
        }
    }

    fun getMediaCommenter(mediaId: String, amount: Int = 10): Flow<JSONObject> = flow {
        var counter = 0
        var nextMaxId = ""

        while (true) {
            if (api.getMediaComments(mediaId, nextMaxId)) {
                api.lastJSON?.read<JSONArray>("$.comments")?.forEach {
                    (it as JSONObject)?.read<JSONObject>("$.user")?.let {
                        emit(it)
                        counter += 1
                        if (counter >= amount) {
                            return@flow
                        }
                    }
                }

                api.lastJSON?.read<Boolean>("$.has_more_comments")?.let { if (!it) return@flow }
                api.lastJSON?.read<String>("$.next_max_id")?.let { nextMaxId = it }
            } else {
                return@flow
            }
        }
    }

    fun getMediaLiker(mediaId: String): Flow<JSONObject> = flow {
        api.getMediaLiker(mediaId)
        api.lastJSON?.read<JSONArray>("$.users")?.forEach { emit(it as JSONObject) }
    }

    fun getCommentLiker(commentId: String): Flow<JSONObject> = flow {
        api.getCommentLiker(commentId)
        api.lastJSON?.read<JSONArray>("$.users")?.forEach { emit(it as JSONObject) }
    }

    @ExperimentalCoroutinesApi
    suspend fun getUserLiker(username: String, mediaAmount: Int = 5): List<JSONObject> {
        val userLiker = mutableListOf<JSONObject>()
        getLastUserMedias(username, mediaAmount).collect { it ->
            getMediaLiker(it.get("pk").toString()).collect {
                userLiker.add(it)
            }
        }

        return userLiker.distinctBy { it.get("username") }
    }


    fun getMediaIdFromLink(mediaLink: String): String? {
        if (!mediaLink.contains("instagram.com/p/")) {
            return null
        }

        var result = 0L
        val alphabet = mapOf(
            'A' to 0, 'B' to 1, 'C' to 2, 'D' to 3, 'E' to 4, 'F' to 5,
            'G' to 6, 'H' to 7, 'I' to 8, 'J' to 9, 'K' to 10, 'L' to 11, 'M' to 12, 'N' to 13, 'O' to 14,
            'P' to 15, 'Q' to 16, 'R' to 17, 'S' to 18, 'T' to 19, 'U' to 20, 'V' to 21, 'W' to 22, 'X' to 23,
            'Y' to 24, 'Z' to 25, 'a' to 26, 'b' to 27, 'c' to 28, 'd' to 29, 'e' to 30, 'f' to 31, 'g' to 32,
            'h' to 33, 'i' to 34, 'j' to 35, 'k' to 36, 'l' to 37, 'm' to 38, 'n' to 39, 'o' to 40, 'p' to 41,
            'q' to 42, 'r' to 43, 's' to 44, 't' to 45, 'u' to 46, 'v' to 47, 'w' to 48, 'x' to 49, 'y' to 50,
            'z' to 51, '0' to 52, '1' to 53, '2' to 54, '3' to 55, '4' to 56, '5' to 57, '6' to 58, '7' to 59,
            '8' to 60, '9' to 61, '-' to 62, '-' to 63
        )

        val link = mediaLink.split("/")
        val code = link.subList(link.indexOf("p") + 1, link.size - 1).toString().removePrefix("[").removeSuffix("]")
        code.forEach { result = (result * 64) + alphabet.getValue(it) }
        return result.toString()
    }

    fun getLinkFromMediaId(mediaId: String): String {
        val alphabet = mapOf(
            'A' to 0, 'B' to 1, 'C' to 2, 'D' to 3, 'E' to 4, 'F' to 5,
            'G' to 6, 'H' to 7, 'I' to 8, 'J' to 9, 'K' to 10, 'L' to 11, 'M' to 12, 'N' to 13, 'O' to 14,
            'P' to 15, 'Q' to 16, 'R' to 17, 'S' to 18, 'T' to 19, 'U' to 20, 'V' to 21, 'W' to 22, 'X' to 23,
            'Y' to 24, 'Z' to 25, 'a' to 26, 'b' to 27, 'c' to 28, 'd' to 29, 'e' to 30, 'f' to 31, 'g' to 32,
            'h' to 33, 'i' to 34, 'j' to 35, 'k' to 36, 'l' to 37, 'm' to 38, 'n' to 39, 'o' to 40, 'p' to 41,
            'q' to 42, 'r' to 43, 's' to 44, 't' to 45, 'u' to 46, 'v' to 47, 'w' to 48, 'x' to 49, 'y' to 50,
            'z' to 51, '0' to 52, '1' to 53, '2' to 54, '3' to 55, '4' to 56, '5' to 57, '6' to 58, '7' to 59,
            '8' to 60, '9' to 61, '-' to 62, '-' to 63
        )

        var id = mediaId.toLong()
        var result = ""
        while (id > 0) {
            val char = (id % 64).toInt()
            id /= 64
            result += alphabet.filterValues { it == char }.keys.first()
        }
        return "https://instagram.com/p/${result.reversed()}/"
    }

    fun getInbox(): Flow<JSONObject> = flow {
        api.getInboxV2()
        api.lastJSON?.read<JSONObject>("$.inbox")?.read<JSONArray>("$.threads")?.forEach {
            emit(it as JSONObject)
        }
    }

    fun searchUsers(username: String): Flow<JSONObject> = flow {
        api.searchUsers(username)
        api.lastJSON?.read<JSONArray>("$.users")?.forEach { emit(it as JSONObject) }
    }

    fun getMutedUsers(): Flow<JSONObject> = flow {
        api.getMutedUsers(mutedContentType = "stories")
        api.lastJSON?.read<JSONArray>("$.users")?.forEach { emit(it as JSONObject) }
    }

    fun getPendingInbox(): Flow<JSONObject> = flow {
        api.getPendingInbox()
        api.lastJSON?.read<JSONObject>("$.inbox")?.read<JSONArray>("$.threads")?.forEach {
            emit(it as JSONObject)
        }
    }

    private suspend fun like(
            mediaId: String, containerModule: String = "feed_short_url",
            feedPosition: Int = 0, username: String = "", userId: String = "",
            hashTagName: String = "", hashTagId: String = "", entityPageName: String = "", entityPageId: String = ""
    ): Boolean {

        if (!reachedLimit("likes")) {
            if (blockedActions["likes"] == true) {
                println("Your Like action is blocked")
                if (blockedActionProtection) {
                    println("Blocked action protection active, Skipping like action")
                }
                return false
            }

            val sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
            println("Sleeping $sleepTimeInMillis seconds")
            delay(sleepTimeInMillis * 1000L)
            api.like(
                mediaId = mediaId, containerModule = containerModule, feedPosition = feedPosition,
                username = username, userId = userId, hashTagName = hashTagName,
                hashTagId = hashTagId, entityPageName = entityPageName, entityPageId = entityPageId
            )

            if (api.lastJSON?.read<String>("$.message") == "feedback_required") {
                println("Like action is blocked")
                if (!blockedActionSleep) {
                    if (blockedActionProtection) {
                        blockedActions["likes"] = true
                    }
                } else {
                    if (sleepingActions["likes"] == true && blockedActionProtection) {
                        println("This is the second blocked like action. \nActivating blocked protection for like action")
                        sleepingActions["likes"] = false
                        blockedActions["likes"] = true
                    } else {
                        println("Like action is going to sleep for $blockedActionSleepDelay seconds")
                        sleepingActions["likes"] = true
                        delay(blockedActionSleepDelay * 1000L)
                    }
                }
                return false
            } else if (api.lastJSON?.read<String>("$.status") == "ok") {
                println("Liked media - $mediaId")
                totalActionPerformed["likes"] = totalActionPerformed["likes"]!!.plus(1)
                if (blockedActionSleep && sleepingActions["likes"] == true) {
                    sleepingActions["likes"] = false
                }
                return true
            }
        }

        println("out of likes for today")
        return false
    }

    fun likeMedias(
            mediaIds: List<String>, username: String = "", userId: String = "",
            hashTagName: String = "", hashTagId: String = ""
    ):
            Flow<String> = flow {

        var feedPosition = 0
        mediaIds.forEach {
            if (reachedLimit("likes")) {
                println("out of likes for today")
                return@flow
            }

            if (like(
                        mediaId = it,
                        feedPosition = feedPosition,
                        username = username,
                        userId = userId,
                        hashTagName = hashTagName,
                        hashTagId = hashTagId
                    )
            ) {
                emit(it)
            } else {
                delay(10 * 1000L)
            }
            feedPosition += 1
        }
    }


    suspend fun likeComment(commentId: String): Boolean {
        if (!reachedLimit("likes")) {
            if (blockedActions.get("likes") == true) {
                println("Your Like action is blocked")
                if (blockedActionProtection) {
                    println("Blocked action protection active, Skipping like action")
                }
                return false
            }

            val sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
            println("Sleeping $sleepTimeInMillis seconds")
            delay(sleepTimeInMillis * 1000L)

            api.likeComment(commentId)
            if (api.lastJSON?.read<String>("$.message") == "feedback_required") {
                println("Like action is blocked")
                blockedActions["likes"] = true
                return false
            } else if (api.lastJSON?.read<String>("$.status") == "ok") {
                println("Liked comment - $commentId")
                totalActionPerformed["likes"] = totalActionPerformed["likes"]!!.plus(1)
                if (blockedActionSleep && sleepingActions["likes"] == true) {
                    sleepingActions["likes"] = false
                }
                return true
            }
        }

        println("out of likes for today")
        return false
    }

    suspend fun likeTimelineMedias(amount: Int = 5): Flow<String> {
        val mediaIds = mutableListOf<String>()
        getTimelineMedias(amount).toList().forEach {
            mediaIds.add(it?.read<Long>("$.pk").toString())
        }
        return likeMedias(mediaIds = mediaIds)
    }

    suspend fun likeMediaComments(mediaId: String, amount: Int = 5): Flow<String> = flow {
        getMediaComments(mediaId, amount).toList().forEach {
            if (!it?.read<Boolean>("has_liked_comment")!!) {
                val commentId = it.read<Long>("$.pk").toString()
                if (likeComment(commentId)) {
                    emit(commentId)
                } else {
                    delay(10 * 1000L)
                }
            }
        }
    }

    @ExperimentalCoroutinesApi
    suspend fun likeUserMedias(username: String, amount: Int = 5): Flow<String> {
        val mediaIds = mutableListOf<String>()
        getLastUserMedias(convertToUserId(username), amount).toList().forEach {
            mediaIds.add(it.read<Long>("$.pk").toString())
        }
        return likeMedias(mediaIds = mediaIds)
    }

    suspend fun likeExploreTabMedias(amount: Int): Flow<String> {
        val mediaIds = mutableListOf<String>()
        getExploreTabMedias(amount).toList().forEach {
            mediaIds.add(it.get("pk").toString())
        }
        return likeMedias(mediaIds = mediaIds)
    }

    suspend fun likeHashTagMedias(hashTag: String, amount: Int = 5): Flow<String> {
        val mediaIds = mutableListOf<String>()
        getHashTagMedias(hashTag, amount).toList().forEach {
            mediaIds.add(it.read<Long>("$.pk").toString())
        }
        return likeMedias(mediaIds = mediaIds)
    }

    suspend fun likeLocationMedias(locationName: String, amount: Int = 5): Flow<String> {
        val mediaIds = mutableListOf<String>()
        getMediasByLocation(locationName, amount).toList().forEach {
            mediaIds.add(it.read<Long>("$.pk").toString())
        }

        return likeMedias(mediaIds = mediaIds)
    }

    @ExperimentalCoroutinesApi
    suspend fun likeUserFollowers(username: String, amountOfFollowers: Int = 1, amountOfMedias: Int = 1): Flow<String> {
        val mediaIds = mutableListOf<String>()
        val followers = getUserFollowers(username, amountOfFollowers).toList()
        followers.forEach { it ->
            val medias = getLastUserMedias(it, amountOfMedias).toList()
            medias.forEach {
                mediaIds.add(it.read<Long>("$.pk").toString())
            }

        }
        return likeMedias(mediaIds)
    }


    @ExperimentalCoroutinesApi
    suspend fun likeUserFollowing(username: String, amountOfFollowing: Int = 1, amountOfMedias: Int = 1): Flow<String> {
        val mediaIds = mutableListOf<String>()
        val following = getUserFollowing(username, amountOfFollowing).toList()
        following.forEach { it ->
            val medias = getLastUserMedias(it, amountOfMedias).toList()
            medias.forEach {
                mediaIds.add(it.read<Long>("$.pk").toString())
            }

        }
        return likeMedias(mediaIds)
    }

    suspend fun unlike(mediaId: String): Boolean {
        if (!reachedLimit("unlikes")) {

            val sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
            println("Sleeping $sleepTimeInMillis seconds")
            delay(sleepTimeInMillis * 1000L)

            if (api.unlike(mediaId)) {
                totalActionPerformed["unlikes"] = totalActionPerformed["unlikes"]!!.plus(1)
                return true
            }
        }

        println("out of unlikes for today")
        return false
    }

    fun unlikeComment(commentId: String): Boolean {
        return api.unlikeComment(commentId)
    }

    suspend fun unlikeMediaComments(mediaId: String): Flow<String> = flow {
        getMediaComments(mediaId, 10).toList().forEach {
            if (it.read<Boolean>("has_liked_comment")!!) {
                val commentId = it.read<Long>("$.pk").toString()
                if (unlikeComment(commentId)) {
                    emit(commentId)
                } else {
                    delay(10 * 1000L)
                }
            }
        }
    }

    fun unlikeMedias(mediaIds: List<String>): Flow<String> = flow {
        mediaIds.forEach {
            if (unlike(mediaId = it)) {
                emit(it)
            } else {
                delay(10 * 1000L)
            }
        }
    }

    @ExperimentalCoroutinesApi
    suspend fun unlikeUserMedias(username: String, amount: Int = 5): Flow<String> {
        val mediaIds = mutableListOf<String>()
        getLastUserMedias(convertToUserId(username), amount).toList().forEach {
            mediaIds.add(it.read<Long>("$.pk").toString())
        }
        return unlikeMedias(mediaIds = mediaIds)
    }

    suspend fun downloadMedia(url: String, username: String, folderName: String, fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            api.downloadMedia(url, username, folderName, fileName)
        }
    }

    fun downloadUserStories(username: String): Flow<String> = flow {
        getUserStoriesURL(username).collect {
            val filename = it.split("/").last().split(".").first()
            if (downloadMedia(it, username, "stories", "$filename.jpg")) {
                emit(filename)
            }
        }
    }

    fun changePassword(newPassword: String): Boolean {
        return api.changePassword(newPassword)
    }

    suspend fun watchUsersStories(usernames: List<String>): Boolean {
        val unseenReels = mutableListOf<JSONObject>()
        getUsersStories(usernames.map { convertToUserId(it) }).collect { it ->
            val lastReelSeenAt = if (it.has("seen")) it.read<Long>("$.seen")!! else 0
            it.read<JSONArray>("$.items")?.forEach {
                if ((it as JSONObject).read<Long>("$.taken_at")!! > lastReelSeenAt) {
                    unseenReels.add(it)
                }
            }
        }

        println("Going to watch ${unseenReels.size} stories")
        totalActionPerformed["stories_viewed"] = totalActionPerformed["stories_viewed"]!!.plus(unseenReels.size)
        return api.watchReels(reels = unseenReels)
    }


    /*
     It will return 3 values.
        1. URL of media
        2. Caption of media
        3. True if media is photo, false if video
     */
    private fun getMediaURLAndDescription(
            mediaId: String,
            isSaveDescription: Boolean
    ): Flow<Triple<String, String, Boolean>> = flow {
        val mediaInfo = getMediaInfo(mediaId)

        val caption = if (isSaveDescription) mediaInfo?.read<JSONObject>("$.caption")?.read<String>("$.text")!! else ""

        when (mediaInfo?.read<Int>("$.media_type")) {
            1 -> {
                val image =
                    mediaInfo.read<JSONObject>("$.image_versions2")?.read<JSONArray>("$.candidates")
                        ?.first() as JSONObject
                val url = image.read<String>("$.url")!!
                emit(Triple(url, caption, true))
                return@flow
            }
            2 -> {
                val video = mediaInfo.read<JSONArray>("$.video_versions")?.first() as JSONObject
                val url = video.read<String>("$.url")!!
                emit(Triple(url, caption, false))
                return@flow
            }
            8 -> {
                mediaInfo.read<JSONArray>("$.carousel_media")?.forEach {
                    when ((it as JSONObject).read<Int>("$.media_type")) {
                        1 -> {
                            val image =
                                it.read<JSONObject>("$.image_versions2")?.read<JSONArray>("$.candidates")
                                    ?.first() as JSONObject
                            val url = image.read<String>("$.url")!!
                            emit(Triple(url, caption, true))
                        }
                        2 -> {
                            val video = it.read<JSONArray>("$.video_versions")?.first() as JSONObject
                            val url = video.read<String>("$.url")!!
                            emit(Triple(url, caption, false))
                        }
                    }
                }
            }
        }
    }


    @ExperimentalCoroutinesApi
    suspend fun downloadUserMedias(username: String, amount: Int, isSaveDescription: Boolean = false): Flow<String> =
        flow {
            var needToSaveDescription = isSaveDescription
            getLastUserMedias(username, amount).collect { it ->
                getMediaURLAndDescription(it.read<Long>("$.pk").toString(), isSaveDescription).collect {
                    val filename = it.first.split("/").last().split(".").first()
                    val folderName = if (it.third) "photos" else "videos"
                    val fileType = if (it.third) ".jpg" else ".mp4"
                    if (downloadMedia(it.first, username, folderName, "$filename$fileType")) {
                        if (needToSaveDescription) {
                            File("$folderName/$username", "$filename.txt").printWriter().use { out ->
                                out.print(it.second)
                            }
                            needToSaveDescription = false
                        }
                        emit("$filename$fileType")
                    }
                }
                needToSaveDescription = isSaveDescription
            }
        }

    private suspend fun follow(userId: String): Boolean {
        if (!reachedLimit("follows")) {
            if (blockedActions["follows"] == true) {
                println("Your Follow action is blocked")
                if (blockedActionProtection) {
                    println("Blocked action protection active, Skipping follow action")
                }
                return false
            }

            val sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
            println("Sleeping $sleepTimeInMillis seconds")
            delay(sleepTimeInMillis * 1000L)

            api.follow(convertToUserId(userId))
            if (api.lastJSON?.read<String>("$.message") == "feedback_required") {
                println("Follow action is blocked")
                if (!blockedActionSleep) {
                    if (blockedActionProtection) {
                        blockedActions["follows"] = true
                    }
                } else {
                    if (sleepingActions["follows"] == true && blockedActionProtection) {
                        println("This is the second blocked follow action. \nActivating blocked protection for follow action")
                        sleepingActions["follows"] = false
                        blockedActions["follows"] = true
                    } else {
                        println("Follow action is going to sleep for $blockedActionSleepDelay seconds")
                        sleepingActions["follows"] = true
                        delay(blockedActionSleepDelay * 1000L)
                    }
                }
                return false
            } else if (api.lastJSON?.read<String>("$.status") == "ok") {
                println("Followed user - $userId")
                totalActionPerformed["follows"] = totalActionPerformed["follows"]!!.plus(1)
                if (blockedActionSleep && sleepingActions["follows"] == true) {
                    sleepingActions["follows"] = false
                }
                return true
            }
        }

        println("out of follows for today")
        return false
    }

    // Need to filter already followed and unfollowed users before performing action
    fun followUsers(usernames: List<String>): Flow<String> = flow {
        usernames.forEach {
            if (reachedLimit("follows")) {
                println("out of follows for today")
                return@flow
            }

            if (follow(it)) {
                emit(it)
            } else {
                delay(10 * 1000L)
            }
        }
    }

    suspend fun followUserFollowers(
            username: String, amountOfFollowers: Int = Int.MAX_VALUE, isUsername: Boolean = false,
            isFilterPrivate: Boolean = false, isFilterVerified: Boolean = false, fileNameToWrite: String = "",
            isOverwrite: Boolean = false
    ): Flow<String> {
        val followers = getUserFollowers(
            username, amountOfFollowers, isUsername, isFilterPrivate,
            isFilterVerified, fileNameToWrite, isOverwrite
        ).toList()

        return followUsers(followers)
    }

    suspend fun followUserFollowing(
            username: String, amountOfFollowing: Int = Int.MAX_VALUE, isUsername: Boolean = false,
            isFilterPrivate: Boolean = false, isFilterVerified: Boolean = false, fileNameToWrite: String = "",
            isOverwrite: Boolean = false
    ): Flow<String> {
        val following = getUserFollowing(
            username, amountOfFollowing, isUsername, isFilterPrivate,
            isFilterVerified, fileNameToWrite, isOverwrite
        ).toList()

        return followUsers(following)
    }

    private suspend fun unfollow(userId: String): Boolean {
        if (!reachedLimit("unfollows")) {
            if (blockedActions["unfollows"] == true) {
                println("Your Unfollow action is blocked")
                if (blockedActionProtection) {
                    println("Blocked action protection active, Skipping unfollow action")
                }
                return false
            }

            val sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
            println("Sleeping $sleepTimeInMillis seconds")
            delay(sleepTimeInMillis * 1000L)

            api.unfollow(convertToUserId(userId))
            if (api.lastJSON?.read<String>("$.message") == "feedback_required") {
                println("Unfollow action is blocked")
                if (!blockedActionSleep) {
                    if (blockedActionProtection) {
                        blockedActions["unfollows"] = true
                    }
                } else {
                    if (sleepingActions["unfollows"] == true && blockedActionProtection) {
                        println("This is the second blocked follow action. \nActivating blocked protection for unfollow action")
                        sleepingActions["unfollows"] = false
                        blockedActions["unfollows"] = true
                    } else {
                        println("Unfollow action is going to sleep for $blockedActionSleepDelay seconds")
                        sleepingActions["unfollows"] = true
                        delay(blockedActionSleepDelay * 1000L)
                    }
                }
                return false
            } else if (api.lastJSON?.read<String>("$.status") == "ok") {
                println("Unfollowed user - $userId")
                totalActionPerformed["unfollows"] = totalActionPerformed["unfollows"]!!.plus(1)
                if (blockedActionSleep && sleepingActions["unfollows"] == true) {
                    sleepingActions["unfollows"] = false
                }
                return true
            }
        }

        println("out of unfollows for today")
        return false
    }

    fun unfollowUsers(usernames: List<String>): Flow<String> = flow {
        usernames.forEach {
            if (reachedLimit("unfollows")) {
                println("out of unfollows for today")
                return@flow
            }

            if (unfollow(it)) {
                emit(it)
            } else {
                delay(10 * 1000L)
            }
        }
    }

    suspend fun unfollowNonFollowers(): Flow<String> {
        val nonFollowers = getSelfFollowing().toSet().subtract(getSelfFollowers().toSet()).toList()
        return unfollowUsers(nonFollowers)
    }

    fun approvePendingFollowRequest(username: String): Boolean {
        return api.approvePendingFollowRequest(convertToUserId(username))
    }

    fun rejectPendingFollowRequest(username: String): Boolean {
        return api.rejectPendingFollowRequest(convertToUserId(username))
    }

    suspend fun approveAllPendingFollowRequests(): Flow<String> = flow {
        getPendingFollowRequests().collect {
            if (approvePendingFollowRequest(it.read<Long>("$.pk").toString())) {
                emit(it.read<String>("$.username")!!)
            }
        }
    }

    suspend fun rejectAllPendingFollowRequests(): Flow<String> = flow {
        getPendingFollowRequests().collect {
            if (rejectPendingFollowRequest(it.read<Long>("$.pk").toString())) {
                emit(it.read<String>("$.username")!!)
            }
        }
    }


    private fun extractURL(text: String): String {
        val pattern =
            """((?:(?:http|https|Http|Https|rtsp|Rtsp)://(?:(?:[a-zA-Z0-9${'$'}\-\_\.\+\!\*\'\(\)\,\;\?\&\=]|(?:%[a-fA-F0-9]{2})){1,64}(?::(?:[a-zA-Z0-9${'$'}\-\_\.\+\!\*\'\(\)\,\;\?\&\=]|(?:%[a-fA-F0-9]{2})){1,25})?@)?)?(?:(?:(?:[a-zA-Z0-9\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF\_][a-zA-Z0-9\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF\_\-]{0,64}\.)+(?:(?:aero|arpa|asia|a[cdefgilmnoqrstuwxz])|(?:biz|b[abdefghijmnorstvwyz])|(?:cat|com|coop|c[acdfghiklmnoruvxyz])|d[ejkmoz]|(?:edu|e[cegrstu])|f[ijkmor]|(?:gov|g[abdefghilmnpqrstuwy])|h[kmnrtu]|(?:info|int|i[delmnoqrst])|(?:jobs|j[emop])|k[eghimnprwyz]|l[abcikrstuvy]|(?:mil|mobi|museum|m[acdeghklmnopqrstuvwxyz])|(?:name|net|n[acefgilopruz])|(?:org|om)|(?:pro|p[aefghklmnrstwy])|qa|r[eosuw]|s[abcdeghijklmnortuvyz]|(?:tel|travel|t[cdfghjklmnoprtvwz])|u[agksyz]|v[aceginu]|w[fs]|(?:\u03B4\u03BF\u03BA\u03B9\u03BC\u03AE|\u0438\u0441\u043F\u044B\u0442\u0430\u043D\u0438\u0435|\u0440\u0444|\u0441\u0440\u0431|\u05D8\u05E2\u05E1\u05D8|\u0622\u0632\u0645\u0627\u06CC\u0634\u06CC|\u0625\u062E\u062A\u0628\u0627\u0631|\u0627\u0644\u0627\u0631\u062F\u0646|\u0627\u0644\u062C\u0632\u0627\u0626\u0631|\u0627\u0644\u0633\u0639\u0648\u062F\u064A\u0629|\u0627\u0644\u0645\u063A\u0631\u0628|\u0627\u0645\u0627\u0631\u0627\u062A|\u0628\u06BE\u0627\u0631\u062A|\u062A\u0648\u0646\u0633|\u0633\u0648\u0631\u064A\u0629|\u0641\u0644\u0633\u0637\u064A\u0646|\u0642\u0637\u0631|\u0645\u0635\u0631|\u092A\u0930\u0940\u0915\u094D\u0937\u093E|\u092D\u093E\u0930\u0924|\u09AD\u09BE\u09B0\u09A4|\u0A2D\u0A3E\u0A30\u0A24|\u0AAD\u0ABE\u0AB0\u0AA4|\u0B87\u0BA8\u0BCD\u0BA4\u0BBF\u0BAF\u0BBE|\u0B87\u0BB2\u0B99\u0BCD\u0B95\u0BC8|\u0B9A\u0BBF\u0B99\u0BCD\u0B95\u0BAA\u0BCD\u0BAA\u0BC2\u0BB0\u0BCD|\u0BAA\u0BB0\u0BBF\u0B9F\u0BCD\u0B9A\u0BC8|\u0C2D\u0C3E\u0C30\u0C24\u0C4D|\u0DBD\u0D82\u0D9A\u0DCF|\u0E44\u0E17\u0E22|\u30C6\u30B9\u30C8|\u4E2D\u56FD|\u4E2D\u570B|\u53F0\u6E7E|\u53F0\u7063|\u65B0\u52A0\u5761|\u6D4B\u8BD5|\u6E2C\u8A66|\u9999\u6E2F|\uD14C\uC2A4\uD2B8|\uD55C\uAD6D|xn--0zwm56d|xn--11b5bs3a9aj6g|xn--3e0b707e|xn--45brj9c|xn--80akhbyknj4f|xn--90a3ac|xn--9t4b11yi5a|xn--clchc0ea0b2g2a9gcd|xn--deba0ad|xn--fiqs8s|xn--fiqz9s|xn--fpcrj9c3d|xn--fzc2c9e2c|xn--g6w251d|xn--gecrj9c|xn--h2brj9c|xn--hgbk6aj7f53bba|xn--hlcj6aya9esc7a|xn--j6w193g|xn--jxalpdlp|xn--kgbechtv|xn--kprw13d|xn--kpry57d|xn--lgbbat1ad8j|xn--mgbaam7a8h|xn--mgbayh7gpa|xn--mgbbh1a71e|xn--mgbc0a9azcg|xn--mgberp4a5d4ar|xn--o3cw4h|xn--ogbpf8fl|xn--p1ai|xn--pgbs0dh|xn--s9brj9c|xn--wgbh1c|xn--wgbl6a|xn--xkc2al3hye2a|xn--xkc2dl3a5ee0h|xn--yfro4i67o|xn--ygbi2ammx|xn--zckzah|xxx)|y[et]|z[amw]))|(?:(?:25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9])\.(?:25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\.(?:25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\.(?:25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[0-9])))(?::\d{1,5})?(?:/(?:(?:[a-zA-Z0-9\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF\;\/\?\:\@\&\=\#\~\-\.\+\!\*\'\(\)\,\_])|(?:%[a-fA-F0-9]{2}))*)?)(?:\b|${'$'})""".trimMargin()
        val matches = Regex(pattern).findAll(text)
        return matches.map { it.groupValues[1] }.toList().map { "\"$it\"" }.toString()
    }

    suspend fun sendMessage(usernames: List<String>, text: String, threadId: String = ""): Boolean {
        if (reachedLimit("messages")) {
            println("out of messages for today")
            return false
        }

        val sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
        println("Sleeping $sleepTimeInMillis seconds")
        delay(sleepTimeInMillis * 1000L)

        val urls = extractURL(text)
        val itemType = if (urls != "[]") "link" else "text"

        if (api.sendDirectItem(
                    itemType = itemType, users = usernames.map { convertToUserId(it) },
                    options = mapOf("text" to text, "urls" to urls, "threadId" to threadId)
                )
        ) {
            totalActionPerformed["messages"] = totalActionPerformed["messages"]!!.plus(1)
            return true
        }

        return false
    }

    fun sendMessagesToUsersIndividually(usernames: List<String>, text: String): Flow<String> = flow {
        usernames.forEach {
            if (reachedLimit("messages")) {
                println("out of messages for today")
                return@flow
            }

            if (sendMessage(listOf(it), text)) {
                emit(it)
            } else {
                delay(10 * 1000L)
            }
        }
    }

    suspend fun sendMedia(mediaId: String, usernames: List<String>, text: String, threadId: String = ""): Boolean {
        if (reachedLimit("messages")) {
            println("out of messages for today")
            return false
        }

        val sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
        println("Sleeping $sleepTimeInMillis seconds")
        delay(sleepTimeInMillis * 1000L)

        val media = getMediaInfo(mediaId)

        val mediaTye = media?.read<Int>("$.media_type").toString()
        val mediaID = media?.read<Long>("$.id").toString()

        if (api.sendDirectItem(
                    itemType = "media_share", users = usernames.map { convertToUserId(it) },
                    options = mapOf(
                        "text" to text, "threadId" to threadId,
                        "media_type" to mediaTye, "media_id" to mediaID
                    )
                )
        ) {
            totalActionPerformed["messages"] = totalActionPerformed["messages"]!!.plus(1)
            return true
        }

        return false
    }

    fun sendMediasToUsersIndividually(mediaId: String, usernames: List<String>, text: String): Flow<String> = flow {
        usernames.forEach {
            if (reachedLimit("messages")) {
                println("out of messages for today")
                return@flow
            }

            if (sendMedia(mediaId, listOf(it), text)) {
                emit(it)
            } else {
                delay(10 * 1000L)
            }
        }
    }

    suspend fun sendHashTag(hashTag: String, usernames: List<String>, text: String, threadId: String = ""): Boolean {
        if (reachedLimit("messages")) {
            println("out of messages for today")
            return false
        }

        val sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
        println("Sleeping $sleepTimeInMillis seconds")
        delay(sleepTimeInMillis * 1000L)

        if (api.sendDirectItem(
                    itemType = "hashtag", users = usernames.map { convertToUserId(it) },
                    options = mapOf("text" to text, "threadId" to threadId, "hashtag" to hashTag)
                )
        ) {
            totalActionPerformed["messages"] = totalActionPerformed["messages"]!!.plus(1)
            return true
        }

        return false
    }

    fun sendHashTagToUsersIndividually(hashTag: String, usernames: List<String>, text: String): Flow<String> = flow {
        usernames.forEach {
            if (reachedLimit("messages")) {
                println("out of messages for today")
                return@flow
            }

            if (sendHashTag(hashTag, listOf(it), text)) {
                emit(it)
            } else {
                delay(10 * 1000L)
            }
        }
    }

    suspend fun sendProfile(profileId: String, usernames: List<String>, text: String, threadId: String = ""): Boolean {
        if (reachedLimit("messages")) {
            println("out of messages for today")
            return false
        }

        val sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
        println("Sleeping $sleepTimeInMillis seconds")
        delay(sleepTimeInMillis * 1000L)

        if (api.sendDirectItem(
                    itemType = "profile", users = usernames.map { convertToUserId(it) },
                    options = mapOf("text" to text, "threadId" to threadId, "profile_user_id" to profileId)
                )
        ) {
            totalActionPerformed["messages"] = totalActionPerformed["messages"]!!.plus(1)
            return true
        }

        return false
    }

    fun sendProfileToUsersIndividually(profileId: String, usernames: List<String>, text: String): Flow<String> = flow {
        usernames.forEach {
            if (reachedLimit("messages")) {
                println("out of messages for today")
                return@flow
            }

            if (sendProfile(profileId, listOf(it), text)) {
                emit(it)
            } else {
                delay(10 * 1000L)
            }
        }
    }

    suspend fun sendLike(usernames: List<String>, threadId: String = ""): Boolean {
        if (reachedLimit("messages")) {
            println("out of messages for today")
            return false
        }

        val sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
        println("Sleeping $sleepTimeInMillis seconds")
        delay(sleepTimeInMillis * 1000L)

        if (api.sendDirectItem(
                    itemType = "like", users = usernames.map { convertToUserId(it) },
                    options = mapOf("threadId" to threadId)
                )
        ) {
            totalActionPerformed["messages"] = totalActionPerformed["messages"]!!.plus(1)
            return true
        }

        return false
    }

    fun sendLikeToUsersIndividually(usernames: List<String>): Flow<String> = flow {
        usernames.forEach {
            if (reachedLimit("messages")) {
                println("out of messages for today")
                return@flow
            }

            if (sendLike(listOf(it))) {
                emit(it)
            } else {
                delay(10 * 1000L)
            }
        }
    }

    suspend fun sendPhoto(usernames: List<String>, filePath: String, threadId: String = ""): Boolean {
        if (reachedLimit("messages")) {
            println("out of messages for today")
            return false
        }

        val sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
        println("Sleeping $sleepTimeInMillis seconds")
        delay(sleepTimeInMillis * 1000L)

        if (api.sendDirectItem(
                    itemType = "photo", users = usernames.map { convertToUserId(it) },
                    options = mapOf("filePath" to filePath, "threadId" to threadId)
                )
        ) {
            totalActionPerformed["messages"] = totalActionPerformed["messages"]!!.plus(1)
            return true
        }

        return false
    }

    fun sendPhotoToUsersIndividually(usernames: List<String>, filePath: String): Flow<String> = flow {
        usernames.forEach {
            if (reachedLimit("messages")) {
                println("out of messages for today")
                return@flow
            }

            if (sendPhoto(listOf(it), filePath)) {
                emit(it)
            } else {
                delay(10 * 1000L)
            }
        }
    }

    fun getPendingThreadRequests(): Flow<JSONObject> {
        return api.getPendingThreads()
    }

    private fun approvePendingThreadRequest(threadId: String): Boolean {
        return api.approvePendingThread(threadId)
    }

    private fun hidePendingThreadRequest(threadId: String): Boolean {
        return api.hidePendingThread(threadId)
    }

    private fun rejectPendingThreadRequest(threadId: String): Boolean {
        return api.rejectPendingThread(threadId)
    }

    // Need to check what can be returned here
    suspend fun approveAllPendingThreadRequests(): Flow<String> = flow {
        getPendingThreadRequests().collect {
            if (approvePendingThreadRequest(it?.read<Long>("$.thread_id").toString())) {
                emit(it?.read<Long>("$.thread_id").toString())
            } else {
                delay(10 * 1000L)
            }
        }
    }

    suspend fun hideAllPendingThreadRequests(): Flow<String> = flow {
        getPendingThreadRequests().collect {
            if (hidePendingThreadRequest(it?.read<Long>("$.thread_id").toString())) {
                emit(it?.read<Long>("$.thread_id").toString())
            } else {
                delay(10 * 1000L)
            }
        }
    }

    suspend fun rejectAllPendingThreadRequests(): Flow<String> = flow {
        getPendingThreadRequests().collect {
            if (rejectPendingThreadRequest(it?.read<Long>("$.thread_id").toString())) {
                emit(it?.read<Long>("$.thread_id").toString())
            } else {
                delay(10 * 1000L)
            }
        }
    }

    fun deleteMedia(mediaId: String): Boolean {
        return api.deleteMedia(mediaId)
    }

    fun deleteMedias(mediaIds: List<String>): Flow<String> = flow {
        mediaIds.forEach {
            if (deleteMedia(it)) {
                emit(it)
            } else {
                delay(10 * 1000L)
            }
        }
    }

    fun deleteComment(mediaId: String, commentId: String): Boolean {
        return api.deleteComment(mediaId, commentId)
    }

    private fun archive(mediaId: String, undo: Boolean = false): Boolean {
        val media = getMediaInfo(mediaId)
        val mediaType = media?.read<Int>("$.media_type")!!
        if (api.archiveMedia(mediaId, mediaType, undo)) {
            if (!undo) {
                totalActionPerformed["archived"] = totalActionPerformed["archived"]!!.plus(1)
            } else {
                totalActionPerformed["unarchived"] = totalActionPerformed["unarchived"]!!.plus(1)
            }
            return true
        }

        return false
    }

    private fun archiveMedia(mediaId: String): Boolean {
        return archive(mediaId, false)
    }

    private fun unarchiveMedia(mediaId: String): Boolean {
        return archive(mediaId, true)
    }

    fun archiveMedias(mediaIds: List<String>): Flow<String> = flow {
        mediaIds.forEach {
            if (archiveMedia(it)) {
                emit(it)
            } else {
                delay(10 * 1000L)
            }
        }
    }

    fun unarchiveMedias(mediaIds: List<String>): Flow<String> = flow {
        mediaIds.forEach {
            if (unarchiveMedia(it)) {
                emit(it)
            } else {
                delay(10 * 1000L)
            }
        }
    }

    suspend fun isMediaCommented(mediaId: String): Boolean {
        return getMediaCommenter(mediaId, Int.MAX_VALUE).toList().map { it?.read<String>("$.username") }
            .contains(this.username)
    }

    private suspend fun comment(mediaId: String, commentText: String): Boolean {
        if (isMediaCommented(mediaId)) {
            return true
        }

        if (!reachedLimit("comments")) {
            if (blockedActions["comments"] == true) {
                println("Your Comment action is blocked")
                if (blockedActionProtection) {
                    println("Blocked action protection active, Skipping comment action")
                }
                return false
            }

            val sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
            println("Sleeping $sleepTimeInMillis seconds")
            delay(sleepTimeInMillis * 1000L)

            api.comment(mediaId = mediaId, commentText = commentText)
            if (api.lastJSON?.read<String>("$.message") == "feedback_required") {
                println("Comment action is blocked")
                if (!blockedActionSleep) {
                    if (blockedActionProtection) {
                        blockedActions["comments"] = true
                    }
                } else {
                    if (sleepingActions["comments"] == true && blockedActionProtection) {
                        println("This is the second blocked like action. \nActivating blocked protection for comments action")
                        sleepingActions["comments"] = false
                        blockedActions["comments"] = true
                    } else {
                        println("Comment action is going to sleep for $blockedActionSleepDelay seconds")
                        sleepingActions["comments"] = true
                        delay(blockedActionSleepDelay * 1000L)
                    }
                }
                return false
            } else if (api.lastJSON?.read<String>("$.status") == "ok") {
                println("Commented media - $mediaId")
                totalActionPerformed["comments"] = totalActionPerformed["comments"]!!.plus(1)
                if (blockedActionSleep && sleepingActions["comments"] == true) {
                    sleepingActions["comments"] = false
                }
                return true
            }
        }

        println("out of comments for today")
        return false
    }

    suspend fun replyToComment(mediaId: String, parentCommentId: String, commentText: String): Boolean {
        if (!isMediaCommented(mediaId)) {
            println("Media is not commented yet")
            return false
        }

        if (!reachedLimit("comments")) {
            if (blockedActions["comments"] == true) {
                println("Your Comment action is blocked")
                if (blockedActionProtection) {
                    println("Blocked action protection active, Skipping comment action")
                }
                return false
            }

            if (commentText[0] != '@') {
                println(
                    "A reply must start with mention, so '@' be the first " +
                            "char, followed by username you're replying to"
                )
                return false
            }
            if (commentText.split(" ")[0].removeRange(0, 1) == this.username) {
                println("You can't reply to yourself")
                return false
            }

            val sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
            println("Sleeping $sleepTimeInMillis seconds")
            delay(sleepTimeInMillis * 1000L)

            api.replyToComment(mediaId = mediaId, parentCommentId = parentCommentId, commentText = commentText)

            if (api.lastJSON?.read<String>("$.message") == "feedback_required") {
                println("Comment action is blocked")
                return false
            } else if (api.lastJSON?.read<String>("$.status") == "ok") {
                println("Commented media - $mediaId")
                totalActionPerformed["comments"] = totalActionPerformed["comments"]!!.plus(1)
                return true
            }
        }

        println("out of comments for today")
        return false
    }

    fun commentMedias(mediaIds: List<String>, commentText: String): Flow<String> = flow {
        mediaIds.forEach {
            if (reachedLimit("comments")) {
                println("out of comments for today")
                return@flow
            }

            if (comment(mediaId = it, commentText = commentText)) {
                emit(it)
            } else {
                delay(10 * 1000L)
            }
        }
    }

    suspend fun commentExploreTabMedias(commentText: String, amountOfMedias: Int): Flow<String> {
        val mediaIds = mutableListOf<String>()
        getExploreTabMedias(amountOfMedias).toList().forEach {
            mediaIds.add(it.get("pk").toString())
        }
        return commentMedias(mediaIds = mediaIds, commentText = commentText)
    }

    suspend fun commentHashTagMedias(hashTag: String, commentText: String, amountOfMedias: Int = 5): Flow<String> {
        val mediaIds = mutableListOf<String>()
        getHashTagMedias(hashTag, amountOfMedias).toList().forEach {
            mediaIds.add(it?.read<Long>("$.pk").toString())
        }
        return commentMedias(mediaIds = mediaIds, commentText = commentText)
    }

    @ExperimentalCoroutinesApi
    suspend fun commentUserMedias(username: String, commentText: String, amountOfMedias: Int = 5): Flow<String> {
        val mediaIds = mutableListOf<String>()
        getLastUserMedias(convertToUserId(username), amountOfMedias).toList().forEach {
            mediaIds.add(it?.read<Long>("$.pk").toString())
        }
        return commentMedias(mediaIds = mediaIds, commentText = commentText)
    }

    suspend fun commentLocationMedias(
            locationName: String,
            commentText: String,
            amountOfMedias: Int = 5
    ): Flow<String> {
        val mediaIds = mutableListOf<String>()
        getMediasByLocation(locationName, amountOfMedias).toList().forEach {
            mediaIds.add(it?.read<Long>("$.pk").toString())
        }

        return commentMedias(mediaIds = mediaIds, commentText = commentText)
    }

    suspend fun commentTimelineMedias(commentText: String, amountOfMedias: Int = 5): Flow<String> {
        val mediaIds = mutableListOf<String>()
        getTimelineMedias(amountOfMedias).toList().forEach {
            mediaIds.add(it?.read<Long>("$.pk").toString())

        }
        return commentMedias(mediaIds = mediaIds, commentText = commentText)
    }

    private suspend fun block(username: String): Boolean {
        if (!reachedLimit("blocks")) {
            val sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
            println("Sleeping $sleepTimeInMillis seconds")
            delay(sleepTimeInMillis * 1000L)

            if (api.block(convertToUserId(username))) {
                totalActionPerformed["blocks"] = totalActionPerformed["blocks"]!!.plus(1)
                return true
            }
        }

        println("out of blocks for today")
        return false
    }

    private suspend fun unblock(username: String): Boolean {
        if (!reachedLimit("unblocks")) {
            val sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
            println("Sleeping $sleepTimeInMillis seconds")
            delay(sleepTimeInMillis * 1000L)

            if (api.unblock(convertToUserId(username))) {
                totalActionPerformed["unblocks"] = totalActionPerformed["unblocks"]!!.plus(1)
                return true
            }
        }

        println("out of unblocks for today")
        return false
    }

    fun blockUsers(usernames: List<String>): Flow<String> = flow {
        usernames.forEach {
            if (block(it)) {
                emit(it)
            } else {
                delay(10 * 1000L)
                return@flow
            }
        }
    }

    fun unblockUsers(usernames: List<String>): Flow<String> = flow {
        usernames.forEach {
            if (unblock(it)) {
                emit(it)
            } else {
                delay(10 * 1000L)
            }
        }
    }
}