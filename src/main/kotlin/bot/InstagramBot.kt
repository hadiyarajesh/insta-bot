package bot

import api.InstagramAPI
import com.nfeld.jsonpathlite.JsonResult
import com.nfeld.jsonpathlite.extension.read
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import util.ACTIONS
import util.FILES
import util.ITEMTYPE
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.properties.Delegates
import kotlin.random.Random

@ExperimentalCoroutinesApi
class InstagramBot {

    private lateinit var maxActionPerDay: MutableMap<String, Int>
    private lateinit var totalActionPerformed: MutableMap<String, Int>
    private lateinit var blockAction: MutableMap<String, Boolean>
    private var sleepTimeInMillis by Delegates.notNull<Int>()

    init {
        runBlocking {
            maxActionPerDay = mutableMapOf()
            totalActionPerformed = mutableMapOf()
            blockAction = mutableMapOf()
            launch { setUpFiles() }
        }
    }

    /**
     * Minimum time in seconds to sleep before performing next action
     */
    var minSleepTime = 60

    /**
     * Maximum time in seconds to sleep before performing next action
     */
    var maxSleepTime = 120

    /**
     * Api client
     */
    private val api = InstagramAPI

    /**
     * Username of instagram user
     */
    val username: String
        get() = api.username

    /**
     * Password of instagram user
     */
    private val password: String
        get() = api.password

    /**
     * User id of instagram user
     */
    private val userId: String
        get() = api.userId

    /**
     * Status code of last response
     */
    val lastStatusCode: Int
        get() = api.statusCode

    /**
     * Last JSON response
     */
    val lastJson: JsonResult?
        get() = api.lastJSON


    //region Internal library methods
    /**
     * Check whether given action is blocked or not
     */
    private fun blockedAction(actionName: String): Boolean {
        return if (blockAction[actionName] == true) {
            println("${actionName.capitalize()} action is blocked by Instagram. Please try again later.")
            true
        } else {
            false
        }
    }

    /**
     * Check whether user reached limit of given action or not
     */
    private fun reachedLimit(actionName: String): Boolean {
        return if (maxActionPerDay.getValue(actionName) < totalActionPerformed.getValue(actionName)) {
            println("You've reached maximum safe limit of ${actionName.capitalize()} for today")
            true
        } else {
            false
        }
    }

    /**
     * Create necessary files in order to write logs in it
     */
    private suspend fun setUpFiles() {
        withContext(Dispatchers.IO) {
            val directory = File(FILES.DATA_DIRECTORY_NAME)
            val maxActionFile = File("${FILES.DATA_DIRECTORY_NAME}/${FILES.MAX_ACTIONS_FILE}")
            val totalActionFile = File("${FILES.DATA_DIRECTORY_NAME}/${FILES.TOTAL_ACTIONS_FILE}")
            val blockActionFile = File("${FILES.DATA_DIRECTORY_NAME}/${FILES.BLOCK_ACTION_FILE}")
            val likesFile = File("${FILES.DATA_DIRECTORY_NAME}/${FILES.LIKES_FILE}")
            val unlikesFile = File("${FILES.DATA_DIRECTORY_NAME}/${FILES.UNLIKES_FILE}")
            val commentsFile = File("${FILES.DATA_DIRECTORY_NAME}/${FILES.COMMENTS_FILE}")
            val followedFile = File("${FILES.DATA_DIRECTORY_NAME}/${FILES.FOLLOWED_FILE}")
            val unfollowedFile = File("${FILES.DATA_DIRECTORY_NAME}/${FILES.UNFOLLOWED_FILE}")
            val blocksFile = File("${FILES.DATA_DIRECTORY_NAME}/${FILES.BLOCKS_FILE}")
            val unblocksFile = File("${FILES.DATA_DIRECTORY_NAME}/${FILES.UNBLOCKS_FILE}")
            val archivedFile = File("${FILES.DATA_DIRECTORY_NAME}/${FILES.ARCHIVED_FILE}")
            val unArchivedFile = File("${FILES.DATA_DIRECTORY_NAME}/${FILES.UNARCHIVED_FILE}")
            val messagesFile = File("${FILES.DATA_DIRECTORY_NAME}/${FILES.MESSAGES_FILE}")
            val storiesFile = File("${FILES.DATA_DIRECTORY_NAME}/${FILES.STORIES_FILE}")

            if (!directory.exists()) {
                directory.mkdir()
            }
            if (!maxActionFile.exists()) {
                maxActionFile.createNewFile()
                initializeMaxActionFile(maxActionFile)
            }
            if (!totalActionFile.exists()) {
                totalActionFile.createNewFile()
                initializeTotalActionFile(totalActionFile, isForce = true)
            }
            initializeTotalActionFile(totalActionFile)

            if (!blockActionFile.exists()) {
                blockActionFile.createNewFile()
                initializeBlockActionFile(blockActionFile, isForce = true)
            }
            initializeBlockActionFile(blockActionFile)

            if (!likesFile.exists()) {
                likesFile.createNewFile()
            }
            if (!unlikesFile.exists()) {
                unlikesFile.createNewFile()
            }
            if (!commentsFile.exists()) {
                commentsFile.createNewFile()
            }
            if (!followedFile.exists()) {
                followedFile.createNewFile()
            }
            if (!unfollowedFile.exists()) {
                unfollowedFile.createNewFile()
            }
            if (!blocksFile.exists()) {
                blocksFile.createNewFile()
            }
            if (!unblocksFile.exists()) {
                unblocksFile.createNewFile()
            }
            if (!archivedFile.exists()) {
                archivedFile.createNewFile()
            }
            if (!unArchivedFile.exists()) {
                unArchivedFile.createNewFile()
            }
            if (!messagesFile.exists()) {
                messagesFile.createNewFile()
            }
            if (!storiesFile.exists()) {
                storiesFile.createNewFile()
            }

            readActionFiles(maxActionFile, totalActionFile, blockActionFile)
        }
    }

    /**
     * Initialize file containing maximum number of actions allowed per day
     */
    private fun initializeMaxActionFile(maxActionFile: File) {
        println("Initializing MaxAction File")
        maxActionFile.printWriter().use { out ->
            out.println("${ACTIONS.LIKES}-200")
            out.println("${ACTIONS.UNLIKES}-200")
            out.println("${ACTIONS.COMMENTS}-100")
            out.println("${ACTIONS.FOLLOWS}-100")
            out.println("${ACTIONS.UNFOLLOWS}-100")
            out.println("${ACTIONS.BLOCKS}-100")
            out.println("${ACTIONS.UNBLOCKS}-100")
            out.println("${ACTIONS.MESSAGES}-100")
            out.println("${ACTIONS.ARCHIVED}-100")
            out.println("${ACTIONS.UNARCHIVED}-100")
            out.println("${ACTIONS.STORIES_VIEWED}-2000")
        }
        maxActionFile.setReadOnly()
    }

    /**
     * Initialize file containing total number of actions performed per day. It will reset on every day
     */
    private fun initializeTotalActionFile(totalActionFile: File, isForce: Boolean = false) {
        val lastModifiedDate =
            Instant.ofEpochMilli(totalActionFile.lastModified()).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        if (lastModifiedDate < today || isForce) {
            println("Initializing TotalAction File")
            totalActionFile.printWriter().use { out ->
                out.println("${ACTIONS.LIKES}-0")
                out.println("${ACTIONS.UNLIKES}-0")
                out.println("${ACTIONS.COMMENTS}-0")
                out.println("${ACTIONS.FOLLOWS}-0")
                out.println("${ACTIONS.UNFOLLOWS}-0")
                out.println("${ACTIONS.BLOCKS}-0")
                out.println("${ACTIONS.UNBLOCKS}-0")
                out.println("${ACTIONS.MESSAGES}-0")
                out.println("${ACTIONS.ARCHIVED}-0")
                out.println("${ACTIONS.UNARCHIVED}-0")
                out.println("${ACTIONS.STORIES_VIEWED}-0")
            }
        }
    }

    /**
     * Initialize file containing whether actions is blocked or not. It will reset on every day
     */
    private fun initializeBlockActionFile(blockActionFile: File, isForce: Boolean = false) {
        val lastModifiedDate =
            Instant.ofEpochMilli(blockActionFile.lastModified()).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        if (lastModifiedDate < today || isForce) {
            println("Initializing BlockAction File")
            blockActionFile.printWriter().use { out ->
                out.println("${ACTIONS.LIKES}-false")
                out.println("${ACTIONS.UNLIKES}-false")
                out.println("${ACTIONS.COMMENTS}-false")
                out.println("${ACTIONS.FOLLOWS}-false")
                out.println("${ACTIONS.UNFOLLOWS}-false")
                out.println("${ACTIONS.BLOCKS}-false")
                out.println("${ACTIONS.UNBLOCKS}-false")
                out.println("${ACTIONS.MESSAGES}-false")
                out.println("${ACTIONS.ARCHIVED}-false")
                out.println("${ACTIONS.UNARCHIVED}-false")
                out.println("${ACTIONS.STORIES_VIEWED}-false")
            }
        }
    }

    /**
     * Read all action files and load data into memory
     */
    private fun readActionFiles(maxActionFile: File, totalActionFile: File, blockActionFile: File) {
        maxActionFile.readLines().forEach { line ->
            val action = line.split("-")
            maxActionPerDay[action.first()] = action.last().toInt()
        }
        totalActionFile.readLines().forEach { line ->
            val action = line.split("-")
            totalActionPerformed[action.first()] = action.last().toInt()
        }
        blockActionFile.readLines().forEach { line ->
            val action = line.split("-")
            blockAction[action.first()] = action.last().toBoolean()
        }
    }

    /**
     * Write all actions to Total action file when action performed successfully
     */
    private suspend fun writeTotalActionToFile() {
        withContext(Dispatchers.IO) {
            File("${FILES.DATA_DIRECTORY_NAME}/${FILES.TOTAL_ACTIONS_FILE}").printWriter().use { out ->
                totalActionPerformed.forEach { (action, value) ->
                    out.println("$action-$value")
                }
            }
        }
    }

    /**
     * Write to Block action file when given action is blocked
     */
    private suspend fun writeBlockActionToFile() {
        withContext(Dispatchers.IO) {
            File("${FILES.DATA_DIRECTORY_NAME}/${FILES.BLOCK_ACTION_FILE}").printWriter().use { out ->
                blockAction.forEach { (action, value) ->
                    out.println("$action-$value")
                }
            }
        }
    }

    /**
     * Write log to files when given action is performed
     */
    private suspend fun writeActionLog(fileName: String, content: String) {
        withContext(Dispatchers.IO) {
            val dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"))
            File("${FILES.DATA_DIRECTORY_NAME}/${fileName}").appendText(text = "$dateTime $content\n")
        }
    }
    //endregion


    //region Authentication methods
    /**
     * Prepare the api client before login
     * @param username Username of instagram account
     */
    fun prepare(username: String) {
        api.username = username
        api.prepare()
    }

    /**
     * Login to instagram with given username/password and interactively handle all scenarios of Two factor authentication code and/or challenge required and/or login confirmation by showing options in prompt.
     * @param username Username of instagram account
     * @param password Password of instagram account
     * @return Boolean indicating successfully logged in or not
     */
    fun login(username: String, password: String, forceLogin: Boolean = false): Boolean {
        api.password = password
        return api.login(username, password, forceLogin)
    }

    /**
     * Logout from Instagram
     * @return Boolean indicating successfully logged out or not
     */
    fun logout(): Boolean {
        return api.logout()
    }
    //endregion


    //region Account related methods
    /**
     * Set user(self) account as public
     */
    fun setAccountsPublic(): Boolean {
        return api.setAccountPublic()
    }

    /**
     * Set user(self) account as private
     */
    fun setAccountPrivate(): Boolean {
        return api.setAccountPrivate()
    }

    /**
     * Change password of user(self)
     */
    fun changePassword(newPassword: String): Boolean {
        return api.changePassword(newPassword)
    }

    /**
     * Get profile information of user(self)
     */
    fun getProfileData(): JSONObject? {
        api.getProfileData()
        return api.lastJSON?.read<JSONObject>("$.user")
    }

    /**
     * Edit profile of user(self)
     * @param url Website option shown on insta profile
     * @param phone User's phone number
     * @param firstName First name of user
     * @param biography Biography of user
     * @param email Email of user
     * @param gender Gender of user (1 for male, 2 for female, 3 for custom)
     */
    fun editProfile(
            firstName: String, biography: String, email: String, gender: Int,
            phone: String, url: String = ""
    ): JSONObject? {
        api.editProfile(url, phone, firstName, biography, email, gender)
        return api.lastJSON?.read<JSONObject>("$.user")
    }
    //endregion


    //region User info related methods
    /**
     * Get user information by given user id
     * @param userId UserId of user
     */
    private fun getUserInfoByID(userId: String): JSONObject? {
        return if (api.getUserInfoByID(userId)) {
            api.lastJSON?.read<JSONObject>("$.user")
        } else {
            null
        }
    }

    /**
     * Get user information by given username
     * @param username Username of user
     */
    fun getUserInfoByName(username: String): JSONObject? {
        return if (api.getUserInfoByName(username)) {
            api.lastJSON?.read<JSONObject>("$.user")
        } else {
            null
        }
    }

    /**
     * Get user id by given name
     */
    private fun getUserIdByName(username: String): String? {
        val userInfo = getUserInfoByName(username)
        return userInfo?.read<String>("$.pk")
    }

    /**
     * Get username by given user id
     */
    private fun getUserNameById(userId: String): String? {
        val userInfo = getUserInfoByID(userId)
        return userInfo?.read<String>("$.username")
    }

    /**
     * Convert username to user id
     */
    private fun convertToUserId(value: String): String {
        return (if (value.toLongOrNull() != null) value else {
            getUserIdByName(value.replace("@", ""))
        }).toString()
    }
    //endregion


    //region Follower/Following related methods
    /**
     * Get following list of self
     * @param amountOfFollowing Amount of following users to retrieve
     * @param isUsername Whether to retrieve username of following or not (if false, userid will be returned)
     * @param isFilterPrivate Whether to filter private account or not
     * @param isFilterVerified Whether to filter verified account or not
     * @param fileNameToWrite File name to write usernames of following list. This is especially useful when following list is large (> 10K)
     * @param isOverwrite Whether to replace existing usernames file or not
     * @return Flow of usernames/userids
     */
    fun getSelfFollowing(
            amountOfFollowing: Int = Int.MAX_VALUE, isUsername: Boolean = false,
            isFilterPrivate: Boolean = false, isFilterVerified: Boolean = false, fileNameToWrite: String = "",
            isOverwrite: Boolean = false
    ): Flow<String> {
        val amount = if(amountOfFollowing == Int.MAX_VALUE) "all" else amountOfFollowing.toString()
        println("Going to get $amount followings of $username")
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

    /**
     * Get follower list of self
     * @param amountOfFollowers Amount of follower users to retrieve
     * @param isUsername Whether to retrieve username of follower or not (if false, userid will be returned)
     * @param isFilterPrivate Whether to filter private account or not
     * @param isFilterVerified Whether to filter verified account or not
     * @param fileNameToWrite File name to write usernames of follower list. This is especially useful when follower list is large (> 10K)
     * @param isOverwrite Whether to replace existing usernames file or not
     * @return Flow of usernames/userids
     */
    fun getSelfFollowers(
            amountOfFollowers: Int = Int.MAX_VALUE, isUsername: Boolean = false,
            isFilterPrivate: Boolean = false, isFilterVerified: Boolean = false, fileNameToWrite: String = "",
            isOverwrite: Boolean = false
    ): Flow<String> {
        val amount = if(amountOfFollowers == Int.MAX_VALUE) "all" else amountOfFollowers.toString()
        println("Going to get $amount followers of $username")
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

    /**
     * Get following list of given user
     * @param amountOfFollowing Amount of following users to retrieve
     * @param isUsername Whether to retrieve username of following or not (if false, userid will be returned)
     * @param isFilterPrivate Whether to filter private account or not
     * @param isFilterVerified Whether to filter verified account or not
     * @param fileNameToWrite File name to write usernames of following list. This is especially useful when following list is large (> 10K)
     * @param isOverwrite Whether to replace existing usernames file or not
     * @return Flow of usernames/userids
     */
    fun getUserFollowing(
            username: String, amountOfFollowing: Int = Int.MAX_VALUE, isUsername: Boolean = false,
            isFilterPrivate: Boolean = false, isFilterVerified: Boolean = false, fileNameToWrite: String = "",
            isOverwrite: Boolean = false
    ): Flow<String> {
        val amount = if(amountOfFollowing == Int.MAX_VALUE) "all" else amountOfFollowing.toString()
        println("Going to get $amount followings of $username")
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

    /**
     * Get follower list of given user
     * @param amountOfFollowers Amount of follower users to retrieve
     * @param isUsername Whether to retrieve username of follower or not (if false, userid will be returned)
     * @param isFilterPrivate Whether to filter private account or not
     * @param isFilterVerified Whether to filter verified account or not
     * @param fileNameToWrite File name to write usernames of follower list. This is especially useful when follower list is large (> 10K)
     * @param isOverwrite Whether to replace existing usernames file or not
     * @return Flow of usernames/userids
     */
    fun getUserFollowers(
            username: String, amountOfFollowers: Int = Int.MAX_VALUE, isUsername: Boolean = false,
            isFilterPrivate: Boolean = false, isFilterVerified: Boolean = false, fileNameToWrite: String = "",
            isOverwrite: Boolean = false
    ): Flow<String> {
        val amount = if(amountOfFollowers == Int.MAX_VALUE) "all" else amountOfFollowers.toString()
        println("Going to get $amount followers of $username")
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
    //endregion


    //region Media related methods
    /**
     * Get media id from link
     */
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
            '8' to 60, '9' to 61, '-' to 62, '_' to 63
        )

        val link = mediaLink.split("/")
        val code = link.subList(link.indexOf("p") + 1, link.size - 1).toString().removePrefix("[").removeSuffix("]")
        code.forEach { result = (result * 64) + alphabet.getValue(it) }
        return result.toString()
    }

    /**
     * Get media link from id
     */
    fun getMediaLinkFromId(mediaId: String): String {
        val alphabet = mapOf(
            'A' to 0, 'B' to 1, 'C' to 2, 'D' to 3, 'E' to 4, 'F' to 5,
            'G' to 6, 'H' to 7, 'I' to 8, 'J' to 9, 'K' to 10, 'L' to 11, 'M' to 12, 'N' to 13, 'O' to 14,
            'P' to 15, 'Q' to 16, 'R' to 17, 'S' to 18, 'T' to 19, 'U' to 20, 'V' to 21, 'W' to 22, 'X' to 23,
            'Y' to 24, 'Z' to 25, 'a' to 26, 'b' to 27, 'c' to 28, 'd' to 29, 'e' to 30, 'f' to 31, 'g' to 32,
            'h' to 33, 'i' to 34, 'j' to 35, 'k' to 36, 'l' to 37, 'm' to 38, 'n' to 39, 'o' to 40, 'p' to 41,
            'q' to 42, 'r' to 43, 's' to 44, 't' to 45, 'u' to 46, 'v' to 47, 'w' to 48, 'x' to 49, 'y' to 50,
            'z' to 51, '0' to 52, '1' to 53, '2' to 54, '3' to 55, '4' to 56, '5' to 57, '6' to 58, '7' to 59,
            '8' to 60, '9' to 61, '-' to 62, '_' to 63
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

    /**
     * Get media info by given media id
     */
    fun getMediaInfo(mediaId: String): JSONObject? {
        api.getMediaInfo(mediaId)
        return api.lastJSON?.read<JSONArray>("$.items")?.first() as JSONObject
    }

    /**
     * Get list of saved medias of user(self)
     */
    fun getSavedMedias(): Flow<JSONObject> = flow {
        api.getSavedMedias()
        api.lastJSON?.read<JSONArray>("$.items")?.forEach { it ->
            (it as JSONObject)?.read<JSONObject>("$.media")?.let { emit(it) }
        }
    }

    /**
     * Get timeline medias of user(self)
     */
    fun getMediasByTimeline(amountOfMedias: Int = 5): Flow<JSONObject> = flow {
        var counter = 0
        var nextMaxId = ""

        while (true) {
            if (api.getTimeline(maxId = nextMaxId)) {
                val feedItems = api.lastJSON?.read<JSONArray>("$.feed_items")
                feedItems?.forEach { it ->
                    (it as JSONObject)?.read<JSONObject>("$.media_or_ad")?.let {
                        emit(it)
                        counter += 1
                        if (counter >= amountOfMedias) {
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

    /**
     * Get given number of medias liked user(self)
     */
    fun getLikedMedias(amountOfMedias: Int = Int.MAX_VALUE): Flow<JSONObject> {
        return api.getTotalLikedMedia(amountOfMedias)
    }

    /**
     * Get given number of medias of user(self)
     */
    fun getMediasBySelf(amountOfMedias: Int = Int.MAX_VALUE): Flow<JSONObject> {
        return api.getLastUserFeed(userId, amountOfMedias)
    }

    /**
     * Get given number of medias of given user
     */
    fun getMediasByUser(username: String, amountOfMedias: Int = Int.MAX_VALUE): Flow<JSONObject> {
        return api.getLastUserFeed(convertToUserId(username), amountOfMedias)
    }

    /**
     * Get medias tagged to given user
     */
    fun getMediasByTaggedUser(username: String): Flow<JSONObject> = flow {
        api.getUserTagMedias(convertToUserId(username))
        api.lastJSON?.read<JSONArray>("$.items")?.forEach {
            emit(it as JSONObject)
        }
    }

    /**
     * Get given number of medias of given hashtag
     */
    fun getMediasByHashTag(hashTag: String, amountOfMedias: Int = 5): Flow<JSONObject> {
        return api.getTotalHashTagMedia(hashTag, amountOfMedias)
    }

    /**
     * Get list of medias from explore page of user(self)
     */
    fun getMediasByExplorePage(amountOfMedias: Int = 5): Flow<JSONObject> = flow {
        var counter = 0
        api.getExplore()
        api.lastJSON?.read<JSONArray>("$.items")?.forEach { it ->
            (it as JSONObject)?.read<JSONObject>("$.media")?.let {
                emit(it)
                counter += 1
                if (counter >= amountOfMedias) {
                    return@flow
                }
            }
        }
    }

    /**
     * Get list of medias by given location
     */
    fun getMediasByLocation(
            locationName: String,
            amountOfMedias: Int = 5,
            amountOfLocation: Int = 5
    ): Flow<JSONObject> = flow {
        var counter = 0
        var nextMaxId = ""

        val locationIds = searchLocations(locationName, amountOfLocation).toList()
        locationIds.forEach { location ->

            api.getLocationFeed(location.get("pk").toString(), nextMaxId)

            val rankedItems = api.lastJSON?.read<JSONArray>("$.ranked_items")
            rankedItems?.forEach { rankedItem ->
                emit(rankedItem as JSONObject)
                counter += 1
                if (counter >= amountOfMedias) {
                    return@flow
                }

            }

            val items = api.lastJSON?.read<JSONArray>("$.items")
            items?.forEach { item ->
                emit(item as JSONObject)
                counter += 1
                if (counter >= amountOfMedias) {
                    return@flow
                }

            }


            api.lastJSON?.read<Boolean>("$.more_available")?.let { if (!it) return@flow }
            api.lastJSON?.read<String>("$.next_max_id")?.let { nextMaxId = it }
        }
    }

    /**
     * Get given number of comments of given media
     */
    fun getMediaComments(mediaId: String, amountOfComments: Int = 5): Flow<JSONObject> = flow {
        var counter = 0
        var nextMaxId = ""

        while (true) {
            if (api.getMediaComments(mediaId, nextMaxId)) {
                api.lastJSON?.read<JSONArray>("$.comments")?.forEach {
                    emit(it as JSONObject)
                    counter += 1
                    if (counter >= amountOfComments) {
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
    //endregion


    //region Users related methods
    /**
     * Get(search) list of users similar to given username
     */
    fun searchUsers(username: String): Flow<JSONObject> = flow {
        api.searchUsers(username)
        api.lastJSON?.read<JSONArray>("$.users")?.forEach { emit(it as JSONObject) }
    }

    /**
     * Get list of user that are muted by user(self)
     */
    fun getMutedUsers(): Flow<JSONObject> = flow {
        api.getMutedUsers(mutedContentType = "stories")
        api.lastJSON?.read<JSONArray>("$.users")?.forEach { emit(it as JSONObject) }
    }

    /**
     * Get list of timeline users of user(self)
     */
    fun getUsersByTimeline(): Flow<JSONObject> = flow {
        api.getTimeline()
        val feedItems = api.lastJSON?.read<JSONArray>("$.feed_items")
        feedItems?.forEach { it ->
            (it as JSONObject)?.read<JSONObject>("$.media_or_ad")?.read<JSONObject>("$.user")?.let {
                emit(it)
            }
        }
    }

    /**
     * Get given number of users of given hashtag
     */
    fun getUsersByHashTag(hashTag: String, amountOfUsers: Int = 10): Flow<JSONObject> {
        return api.getTotalHashTagUsers(hashTag, amountOfUsers)
    }

    /**
     * Get given number of commenter(user who commented on media) of given media
     */
    fun getMediaCommenters(mediaId: String, amountOfUsers: Int = 10): Flow<JSONObject> = flow {
        var counter = 0
        var nextMaxId = ""

        while (true) {
            if (api.getMediaComments(mediaId, nextMaxId)) {
                api.lastJSON?.read<JSONArray>("$.comments")?.forEach {
                    (it as JSONObject)?.read<JSONObject>("$.user")?.let {
                        emit(it)
                        counter += 1
                        if (counter >= amountOfUsers) {
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

    /**
     * Get likers(user who liked media) of given media
     */
    fun getMediaLikers(mediaId: String, amountOfUsers: Int = 10): Flow<JSONObject> = flow {
        var counter = 0

        api.getMediaLiker(mediaId)
        api.lastJSON?.read<JSONArray>("$.users")?.forEach {
            emit(it as JSONObject)
            counter += 1
            if (counter >= amountOfUsers) {
                return@flow
            }
        }
    }

    /**
     * Get likers(user who liked comment) of given comment
     */
    fun getCommentLikers(commentId: String): Flow<JSONObject> = flow {
        api.getCommentLiker(commentId)
        api.lastJSON?.read<JSONArray>("$.users")?.forEach { emit(it as JSONObject) }
    }

    /**
     * Get likers(user who liked recent medias) of given users
     */
    suspend fun getUserLikers(username: String, amountOfUsers: Int = 5): List<JSONObject> {
        val userLiker = mutableListOf<JSONObject>()
        getMediasByUser(username, amountOfUsers).collect { it ->
            getMediaLikers(it.get("pk").toString()).collect {
                userLiker.add(it)
            }
        }

        return userLiker.distinctBy { it.get("username") }
    }

    /**
     * Get list of users whose media appeared in explore page
     */
    fun getUsersByExplorePage(amountOfUsers: Int = 10): Flow<JSONObject> = flow {
        var counter = 0
        api.getExplore()
        api.lastJSON?.read<JSONArray>("$.items")?.forEach { it ->
            (it as JSONObject)?.read<JSONObject>("$.media")?.read<JSONObject>("$.user")?.let {
                emit(it)
                counter += 1
                if (counter >= amountOfUsers) {
                    return@flow
                }
            }
        }
    }

    /**
     * Get list of users tagged in explore page medias
     */
    fun getUsersTaggedInExplorePage(amountOfUsers: Int): Flow<JSONObject> = flow {
        var counter = 0
        api.getExplore()
        api.lastJSON?.read<JSONArray>("$.items")?.forEach { it ->
            (it as JSONObject)?.read<JSONObject>("$.media")?.let {
                it?.read<JSONObject>("$.usertags")?.read<JSONArray>("$.in")?.forEach {
                    (it as JSONObject)?.read<JSONObject>("$.user")?.let {
                        emit(it)
                        counter += 1
                        if (counter >= amountOfUsers) {
                            return@flow
                        }
                    }
                }
            }
        }
    }

    /**
     * Get list of all users whose media appeared in explore page and users that are tagged in it
     */
    fun getAllUsersByExplorePage(): Flow<JSONObject> = flow {
        api.getExplore()
        api.lastJSON?.read<JSONArray>("$.items")?.forEach { it ->
            (it as JSONObject)?.read<JSONObject>("$.media")?.read<JSONObject>("$.user")?.let {
                emit(it)
            }

            (it as JSONObject)?.read<JSONObject>("$.media")?.let {
                it?.read<JSONObject>("$.usertags")?.read<JSONArray>("$.in")?.forEach {
                    (it as JSONObject)?.read<JSONObject>("$.user")?.let {
                        emit(it)
                    }
                }
            }
        }
    }

    /**
     * Get list of users whose media appeared in given location
     */
    fun getUsersByLocation(locationName: String, amountOfUsers: Int = 5, amountOfLocation: Int = 5): Flow<JSONObject> =
        flow {
            var counter = 0
            var nextMaxId = ""

            val locationIds = searchLocations(locationName, amountOfLocation).toList()
            locationIds.forEach { location ->
                api.getLocationFeed(location.get("pk").toString(), nextMaxId)

                val rankedItems = api.lastJSON?.read<JSONArray>("$.ranked_items")
                rankedItems?.forEach { rankedItem ->
                    (rankedItem as JSONObject)?.read<JSONObject>("$.user")?.let {
                        emit(it)
                        counter += 1
                        if (counter >= amountOfUsers) {
                            return@flow
                        }
                    }
                }

                val items = api.lastJSON?.read<JSONArray>("$.items")
                items?.forEach { item ->
                    (item as JSONObject)?.read<JSONObject>("$.user")?.let {
                        emit(it)
                        counter += 1
                        if (counter >= amountOfUsers) {
                            return@flow
                        }
                    }
                }


                api.lastJSON?.read<Boolean>("$.more_available")?.let { if (!it) return@flow }
                api.lastJSON?.read<String>("$.next_max_id")?.let { nextMaxId = it }
            }
        }

    /**
     * Get list of users tagged in medias of given location
     */
    fun getUsersTaggedInLocation(
            locationName: String,
            amountOfUsers: Int = 5,
            amountOfLocation: Int = 5
    ): Flow<JSONObject> = flow {
        var counter = 0
        var nextMaxId = ""

        val locationIds = searchLocations(locationName, amountOfLocation).toList()
        locationIds.forEach { location ->
            api.getLocationFeed(location.get("pk").toString(), nextMaxId)

            val rankedItems = api.lastJSON?.read<JSONArray>("$.ranked_items")
            rankedItems?.forEach { rankedItem ->
                (rankedItem as JSONObject)?.read<JSONObject>("$.usertags")?.read<JSONArray>("$.in")
                    ?.read<JSONObject>("$.user")?.let {
                        emit(it)
                        counter += 1
                        if (counter >= amountOfUsers) {
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
                        if (counter >= amountOfUsers) {
                            return@flow
                        }
                    }
            }

            api.lastJSON?.read<Boolean>("$.more_available")?.let { if (!it) return@flow }
            api.lastJSON?.read<String>("$.next_max_id")?.let { nextMaxId = it }
        }
    }

    /**
     * Get list of all users whose media appeared in given location and users that are tagged in it
     */
    fun getAllUsersByLocation(locationName: String, amountOfUsers: Int, amountOfLocation: Int = 5): Flow<JSONObject> =
        flow {
            var counter = 0
            var nextMaxId = ""

            val locationIds = searchLocations(locationName, amountOfLocation).toList()
            locationIds.forEach { location ->
                api.getLocationFeed(location.get("pk").toString(), nextMaxId)

                val rankedItems = api.lastJSON?.read<JSONArray>("$.ranked_items")
                rankedItems?.forEach { rankedItem ->
                    (rankedItem as JSONObject)?.read<JSONObject>("$.user")?.let {
                        emit(it)
                        counter += 1
                        if (counter >= amountOfUsers) {
                            return@flow
                        }
                    }

                    (rankedItem as JSONObject)?.read<JSONObject>("$.usertags")?.read<JSONArray>("$.in")?.forEach {
                        (it as JSONObject)?.read<JSONObject>("$.user")?.let {
                            emit(it)
                            counter += 1
                            if (counter >= amountOfUsers) {
                                return@flow
                            }
                        }
                    }

                }

                val items = api.lastJSON?.read<JSONArray>("$.items")
                items?.forEach { item ->
                    (item as JSONObject)?.read<JSONObject>("$.user")?.let {
                        emit(it)
                        counter += 1
                        if (counter >= amountOfUsers) {
                            return@flow
                        }
                    }

                    (item as JSONObject)?.read<JSONObject>("$.usertags")?.read<JSONArray>("$.in")?.forEach {
                        (it as JSONObject)?.read<JSONObject>("$.user")?.let {
                            emit(it)
                            counter += 1
                            if (counter >= amountOfUsers) {
                                return@flow
                            }
                        }
                    }

                    api.lastJSON?.read<Boolean>("$.more_available")?.let { if (!it) return@flow }
                    api.lastJSON?.read<String>("$.next_max_id")?.let { nextMaxId = it }
                }

            }
        }
    //endregion


    //region Like/Unlike related methods
    /**
     * Generic method to perform like action
     */
    private suspend fun like(
            mediaId: String, containerModule: String = "feed_short_url",
            feedPosition: Int = 0, username: String = "", userId: String = "",
            hashTagName: String = "", hashTagId: String = "", entityPageName: String = "", entityPageId: String = ""
    ): Boolean {
        sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
        println("Waiting $sleepTimeInMillis seconds before liking a post")
        delay(sleepTimeInMillis * 1000L)

        api.like(
            mediaId = mediaId, containerModule = containerModule, feedPosition = feedPosition,
            username = username, userId = userId, hashTagName = hashTagName,
            hashTagId = hashTagId, entityPageName = entityPageName, entityPageId = entityPageId
        )

        return when {
            api.lastJSON?.read<String>("$.message") == "feedback_required" -> {
                println("Like action is blocked by Instagram. Please try again later.")
                blockAction[ACTIONS.LIKES] = true
                writeBlockActionToFile()
                false
            }
            api.lastJSON?.read<String>("$.status") == "ok" -> {
                val content = "Liked media ID:$mediaId Link:${getMediaLinkFromId(mediaId)}"
                println(content)
                totalActionPerformed[ACTIONS.LIKES] = totalActionPerformed[ACTIONS.LIKES]!! + 1
                writeTotalActionToFile()
                writeActionLog(FILES.LIKES_FILE, content)
                true
            }
            else -> {
                false
            }
        }
    }

    /**
     * Like given amount of medias
     */
    private fun likeMedias(
            mediaIds: List<String>, username: String = "", userId: String = "",
            hashTagName: String = "", hashTagId: String = ""
    ): Flow<String> = flow {
        var feedPosition = 0
        mediaIds.forEach {
            if (blockedAction(ACTIONS.LIKES) || reachedLimit(ACTIONS.LIKES)) {
                return@flow
            }
            if (like(
                        mediaId = it, feedPosition = feedPosition, username = username,
                        userId = userId, hashTagName = hashTagName, hashTagId = hashTagId
                    )
            ) {
                emit(it)
            } else {
                return@flow
            }
            feedPosition += 1
        }
    }

    /**
     * Like given amount of medias appeared on timeline of user(self)
     */
    suspend fun likeMediasByTimeline(amountOfMedias: Int = 5): Flow<String> {
        println("Going to like $amountOfMedias medias from user's timeline")
        val mediaIds = getMediasByTimeline(amountOfMedias).map { it.get("pk").toString() }.toList()
        return likeMedias(mediaIds = mediaIds)
    }

    /**
     * Like given amount of medias of given user
     */
    suspend fun likeMediasByUser(username: String, amountOfMedias: Int = 5): Flow<String> {
        println("Going to like $amountOfMedias medias of $username")
        val mediaIds = getMediasByUser(username, amountOfMedias).map { it.get("pk").toString() }.toList()
        return likeMedias(mediaIds = mediaIds)
    }

    /**
     * Like given amount of medias appeared in explore page of user(self)
     */
    suspend fun likeMediasByExplorePage(amountOfMedias: Int): Flow<String> {
        println("Going to like $amountOfMedias medias from explore page")
        val mediaIds = getMediasByExplorePage(amountOfMedias).map { it.get("pk").toString() }.toList()
        return likeMedias(mediaIds = mediaIds)
    }

    /**
     * Like given amount of medias having given hashtag
     */
    suspend fun likeMediasByHashTag(hashTag: String, amountOfMedias: Int = 5): Flow<String> {
        println("Going to like $amountOfMedias medias with having $hashTag hashtag")
        val mediaIds = getMediasByHashTag(hashTag, amountOfMedias).map { it.get("pk").toString() }.toList()
        return likeMedias(mediaIds = mediaIds)
    }

    /**
     * Like given amount of medias having given location
     */
    suspend fun likeMediasByLocation(
            locationName: String,
            amountOfMedias: Int = 5,
            amountOfLocation: Int = 5
    ): Flow<String> {
        println("Going to like $amountOfMedias medias having $locationName location")
        val mediaIds =
            getMediasByLocation(locationName, amountOfMedias, amountOfLocation).map { it.get("pk").toString() }.toList()
        return likeMedias(mediaIds = mediaIds)
    }

    /**
     * Like given amount of medias of followers of given user
     */
    suspend fun likeMediasByUserFollowers(
            username: String,
            amountOfFollowers: Int = 1,
            amountOfMedias: Int = 1
    ): Flow<String> {
        val mediaIds = mutableListOf<String>()
        val followers = getUserFollowers(username, amountOfFollowers).toList()
        followers.forEach { it ->
            val medias = getMediasByUser(it, amountOfMedias).toList()
            medias.forEach {
                mediaIds.add(it.read<Long>("$.pk").toString())
            }

        }
        return likeMedias(mediaIds)
    }

    /**
     * Like given amount of medias of followings of given user
     */
    suspend fun likeMediasByUserFollowing(
            username: String,
            amountOfFollowing: Int = 1,
            amountOfMedias: Int = 1
    ): Flow<String> {
        val mediaIds = mutableListOf<String>()
        val following = getUserFollowing(username, amountOfFollowing).toList()
        following.forEach { it ->
            val medias = getMediasByUser(it, amountOfMedias).toList()
            medias.forEach {
                mediaIds.add(it.read<Long>("$.pk").toString())
            }

        }
        return likeMedias(mediaIds)
    }

    /**
     * Generic method to perform like action on given comment
     */
    suspend fun likeComment(commentId: String): Boolean {
        sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
        println("Waiting $sleepTimeInMillis seconds before liking a comment")
        delay(sleepTimeInMillis * 1000L)

        api.likeComment(commentId)
        return when {
            api.lastJSON?.read<String>("$.message") == "feedback_required" -> {
                println("Like action is blocked by Instagram. Please try again later.")
                blockAction[ACTIONS.LIKES] = true
                writeBlockActionToFile()
                false
            }
            api.lastJSON?.read<String>("$.status") == "ok" -> {
                val content = "Liked media comment ID:$commentId"
                println(content)
                totalActionPerformed[ACTIONS.LIKES] = totalActionPerformed[ACTIONS.LIKES]!!.plus(1)
                writeTotalActionToFile()
                writeActionLog(FILES.LIKES_FILE, content)
                true
            }
            else -> {
                false
            }
        }
    }

    /**
     * Like given amount of comments of given media
     */
    suspend fun likeMediaComments(mediaId: String, amount: Int = 5): Flow<String> = flow {
        getMediaComments(mediaId, amount).toList().forEach {
            if (blockedAction(ACTIONS.LIKES) || reachedLimit(ACTIONS.LIKES)) {
                return@flow
            }
            if (!it?.read<Boolean>("has_liked_comment")!!) {
                val commentId = it.read<Long>("$.pk").toString()
                if (likeComment(commentId)) {
                    emit(commentId)
                } else {
                    return@flow
                }
            }
        }
    }

    /**
     * Generic method to perform unlike action
     */
    private suspend fun unlike(mediaId: String): Boolean {
        sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
        println("Waiting $sleepTimeInMillis seconds before unliking a post")
        delay(sleepTimeInMillis * 1000L)

        api.unlike(mediaId)
        return when {
            api.lastJSON?.read<String>("$.status") == "ok" -> {
                val content = "Unliked media ID:$mediaId Link:${getMediaLinkFromId(mediaId)}"
                println(content)
                totalActionPerformed[ACTIONS.UNLIKES] = totalActionPerformed[ACTIONS.UNLIKES]!! + 1
                writeTotalActionToFile()
                writeActionLog(FILES.UNLIKES_FILE, content)
                true
            }
            else -> {
                println("Unlike action is blocked by Instagram. Please try again later.")
                blockAction[ACTIONS.UNLIKES] = true
                writeBlockActionToFile()
                false
            }
        }
    }

    /**
     * Unlike(previously liked) given amount of medias
     */
    fun unlikeMedias(mediaIds: List<String>): Flow<String> = flow {
        mediaIds.forEach {
            if (blockedAction(ACTIONS.UNLIKES) || reachedLimit(ACTIONS.UNLIKES)) {
                return@flow
            }
            if (unlike(mediaId = it)) {
                emit(it)
            } else {
                return@flow
            }
        }
    }

    /**
     * Unlike(previously liked) given amount of medias of given user
     */
    suspend fun unlikeUserMedias(username: String, amount: Int = 5): Flow<String> {
        val mediaIds = getMediasByUser(username, amount).map { it.get("pk").toString() }.toList()
        return likeMedias(mediaIds = mediaIds)
    }

    /**
     * Generic method to perform Unlike action on given comment
     */
    suspend fun unlikeComment(commentId: String): Boolean {
        api.unlikeComment(commentId)
        return when {
            api.lastJSON?.read<String>("$.status") == "ok" -> {
                val content = "Liked media comment ID:$commentId"
                println(content)
                totalActionPerformed[ACTIONS.UNLIKES] = totalActionPerformed[ACTIONS.UNLIKES]!! + 1
                writeTotalActionToFile()
                writeActionLog(FILES.UNLIKES_FILE, content)
                true
            }
            else -> {
                println("Unlike action is blocked by Instagram. Please try again later.")
                blockAction[ACTIONS.UNLIKES] = true
                writeBlockActionToFile()
                false
            }
        }
    }

    /**
     * Unlike(previously liked) comments of given media
     */
    fun unlikeMediaComments(mediaId: String): Flow<String> = flow {
        getMediaComments(mediaId, 10).toList().forEach {
            if (blockedAction(ACTIONS.UNLIKES) || reachedLimit(ACTIONS.UNLIKES)) {
                return@flow
            }
            if (it.read<Boolean>("has_liked_comment")!!) {
                val commentId = it.read<Long>("$.pk").toString()
                if (unlikeComment(commentId)) {
                    emit(commentId)
                } else {
                    return@flow
                }
            }
        }
    }
    //endregion


    //region Comment related methods
    /**
     * Check whether media is commented by user(self) or not
     */
    private suspend fun isMediaCommented(mediaId: String): Boolean {
        return getMediaCommenters(mediaId).map { it.get("username").toString() }.toList()
            .contains(this.username)
    }

    /**
     * Generic method to perform comment action
     */
    private suspend fun comment(mediaId: String, commentText: String): Boolean {
        if (isMediaCommented(mediaId)) {
            return true
        }

        sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
        println("Waiting $sleepTimeInMillis seconds before commenting on a post")
        delay(sleepTimeInMillis * 1000L)

        api.comment(mediaId = mediaId, commentText = commentText)
        return when {
            api.lastJSON?.read<String>("$.message") == "feedback_required" -> {
                println("Comment action is blocked by Instagram. Please try again later.")
                blockAction[ACTIONS.COMMENTS] = true
                writeBlockActionToFile()
                false
            }
            api.lastJSON?.read<String>("$.status") == "ok" -> {
                val content = "Commented media ID:$mediaId Link:${getMediaLinkFromId(mediaId)} comment:\"$commentText\""
                println(content)
                totalActionPerformed[ACTIONS.COMMENTS] = totalActionPerformed[ACTIONS.COMMENTS]!! + 1
                writeTotalActionToFile()
                writeActionLog(FILES.COMMENTS_FILE, content)
                true
            }
            else -> {
                false
            }
        }
    }

    /**
     * Generic method to perform reply on given comment of given media
     */
    suspend fun replyToComment(mediaId: String, parentCommentId: String, commentText: String): Boolean {
        if (!isMediaCommented(mediaId)) {
            println("Media is not commented yet")
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

        sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
        println("Waiting $sleepTimeInMillis seconds before replying to comment")
        delay(sleepTimeInMillis * 1000L)

        api.replyToComment(mediaId = mediaId, parentCommentId = parentCommentId, commentText = commentText)

        return when {
            api.lastJSON?.read<String>("$.message") == "feedback_required" -> {
                println("Comment action is blocked by Instagram. Please try again later.")
                blockAction[ACTIONS.COMMENTS] = true
                writeBlockActionToFile()
                false
            }
            api.lastJSON?.read<String>("$.status") == "ok" -> {
                val content = "Commented $commentText on media ID:$mediaId over parent comment $parentCommentId"
                println(content)
                totalActionPerformed[ACTIONS.COMMENTS] = totalActionPerformed[ACTIONS.COMMENTS]!! + 1
                writeTotalActionToFile()
                writeActionLog(FILES.COMMENTS_FILE, content)
                true
            }
            else -> {
                false
            }
        }
    }

    /**
     * Comment given amount of medias with given comment text
     */
    fun commentMedias(mediaIds: List<String>, commentList: List<String>): Flow<String> = flow {
        mediaIds.forEach {
            if (blockedAction(ACTIONS.COMMENTS) || reachedLimit(ACTIONS.COMMENTS)) {
                return@flow
            }
            val commentText = commentList[Random.nextInt(0, commentList.size - 1)]
            if (comment(mediaId = it, commentText = commentText)) {
                emit(it)
            } else {
                return@flow
            }
        }
    }

    /**
     * Comment given amount of medias from timeline of user(self) with given comment text
     */
    suspend fun commentMediasByTimeline(commentList: List<String>, amountOfMedias: Int = 5): Flow<String> {
        println("Going to comment $amountOfMedias medias from users timeline")
        val mediaIds = getMediasByTimeline(amountOfMedias).map { it.get("pk").toString() }.toList()
        return commentMedias(mediaIds = mediaIds, commentList = commentList)
    }

    /**
     * Comment given amount of medias from explore page of user(self) with given comment text
     */
    suspend fun commentMediasByExplorePage(commentList: List<String>, amountOfMedias: Int): Flow<String> {
        println("Going to comment $amountOfMedias medias from explore page")
        val mediaIds = getMediasByExplorePage(amountOfMedias).map { it.get("pk").toString() }.toList()
        return commentMedias(mediaIds = mediaIds, commentList = commentList)
    }

    /**
     * Comment given amount of hashtag medias with given comment text
     */
    suspend fun commentMediasByHashTag(
            hashTag: String,
            commentList: List<String>,
            amountOfMedias: Int = 5
    ): Flow<String> {
        println("Going to comment $amountOfMedias medias having $hashTag hashtag")
        val mediaIds = getMediasByHashTag(hashTag, amountOfMedias).map { it.get("pk").toString() }.toList()
        return commentMedias(mediaIds = mediaIds, commentList = commentList)
    }

    /**
     * Comment given amount of medias of given user with given comment text
     */
    suspend fun commentMediasByUser(
            username: String,
            commentList: List<String>,
            amountOfMedias: Int = 5
    ): Flow<String> {
        println("Going to comment $amountOfMedias medias of $username")
        val mediaIds = getMediasByUser(username, amountOfMedias).map { it.get("pk").toString() }.toList()
        return commentMedias(mediaIds = mediaIds, commentList = commentList)
    }

    /**
     * Comment given amount of medias of given location with given comment text
     */
    suspend fun commentMediasByLocation(
            locationName: String,
            commentList: List<String>,
            amountOfMedias: Int = 5,
            amountOfLocation: Int = 5
    ): Flow<String> {
        println("Going to comment $amountOfMedias medias having $locationName location")
        val mediaIds =
            getMediasByLocation(locationName, amountOfMedias, amountOfLocation).map { it.get("pk").toString() }.toList()
        return commentMedias(mediaIds = mediaIds, commentList = commentList)
    }
    //endregion


    //region Follow/Unfollow related methods
    /**
     * Generic method to perform follow action
     */
    private suspend fun follow(userId: String): Boolean {
        sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
        println("Waiting $sleepTimeInMillis seconds before following a user")
        delay(sleepTimeInMillis * 1000L)

        api.follow(convertToUserId(userId))
        return when {
            api.lastJSON?.read<String>("$.message") == "feedback_required" -> {
                println("Follow action is blocked by Instagram. Please try again later.")
                blockAction[ACTIONS.FOLLOWS] = true
                writeBlockActionToFile()
                false
            }
            api.lastJSON?.read<String>("$.status") == "ok" -> {
                val content = "Followed user ID:$userId username:${getUserNameById(userId)}"
                println(content)
                totalActionPerformed[ACTIONS.FOLLOWS] = totalActionPerformed[ACTIONS.FOLLOWS]!! + 1
                writeTotalActionToFile()
                writeActionLog(FILES.FOLLOWED_FILE, content)
                true
            }
            else -> {
                false
            }
        }
    }

    /**
     * Follow given amount of users
     */
    fun followUsers(usernames: List<String>): Flow<String> = flow {
        usernames.forEach {
            if (blockedAction(ACTIONS.FOLLOWS) || reachedLimit(ACTIONS.FOLLOWS)) {
                return@flow
            }
            if (follow(it)) {
                emit(it)
            } else {
                return@flow
            }
        }
    }

    /**
     * Follow given amount of followers of given user
     */
    suspend fun followUserFollowers(
            username: String, amountOfFollowers: Int, isFilterPrivate: Boolean = false,
            isFilterVerified: Boolean = false
    ): Flow<String> {
        val followers = getUserFollowers(
            username = username, amountOfFollowers = amountOfFollowers,
            isFilterPrivate = isFilterPrivate, isFilterVerified = isFilterVerified
        ).toList()

        return followUsers(followers)
    }

    /**
     * Follow given amount of following of given user
     */
    suspend fun followUserFollowing(
            username: String, amountOfFollowing: Int, isFilterPrivate: Boolean = false,
            isFilterVerified: Boolean = false
    ): Flow<String> {
        val following = getUserFollowing(
            username = username, amountOfFollowing = amountOfFollowing,
            isFilterPrivate = isFilterPrivate, isFilterVerified = isFilterVerified
        ).toList()

        return followUsers(following)
    }

    /**
     * Follow given amount of users appeared on explore page of user(self)
     */
    suspend fun followExploreTabUsers(amountOfUsers: Int): Flow<String> {
        val users = getUsersByExplorePage(amountOfUsers).map { it.get("pk").toString() }.toList()
        return followUsers(users)
    }

    /**
     * Follow given amount of users having given location
     */
    suspend fun followUsersByLocation(locationName: String, amountOfUsers: Int): Flow<String> {
        val users = getUsersByLocation(locationName = locationName, amountOfUsers = amountOfUsers)
            .map { it.get("pk").toString() }.toList()

        return followUsers(users)
    }

    /**
     * Generic method to perform unfollow action
     */
    private suspend fun unfollow(userId: String): Boolean {
        sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
        println("Waiting $sleepTimeInMillis seconds before un-following a user")
        delay(sleepTimeInMillis * 1000L)

        api.unfollow(convertToUserId(userId))
        return when {
            api.lastJSON?.read<String>("$.message") == "feedback_required" -> {
                println("Unfollow action is blocked by Instagram. Please try again later.")
                blockAction[ACTIONS.UNFOLLOWS] = true
                writeBlockActionToFile()
                false
            }
            api.lastJSON?.read<String>("$.status") == "ok" -> {
                val content = "Unfollowed user ID:$userId username:${getUserNameById(userId)}"
                println(content)
                totalActionPerformed[ACTIONS.UNFOLLOWS] = totalActionPerformed[ACTIONS.UNFOLLOWS]!! + 1
                writeTotalActionToFile()
                writeActionLog(FILES.UNFOLLOWED_FILE, content)
                true
            }
            else -> {
                false
            }
        }
    }

    /**
     * Unfollow(previously followed) given amount of users
     */
    fun unfollowUsers(usernames: List<String>): Flow<String> = flow {
        usernames.forEach {
            if (blockedAction(ACTIONS.UNFOLLOWS) || reachedLimit(ACTIONS.UNFOLLOWS)) {
                return@flow
            }
            if (unfollow(it)) {
                emit(it)
            } else {
                return@flow
            }
        }
    }

    /**
     * Unfollow users who are not following back to user(self)
     */
    suspend fun unfollowNonFollowers(): Flow<String> {
        val nonFollowers = getSelfFollowing().toSet().subtract(getSelfFollowers().toSet()).toList()
        return unfollowUsers(nonFollowers)
    }
    //endregion


    //region Direct messaging (DM) related methods
    /**
     * Extract url from given string
     */
    private fun extractURL(text: String): String {
        val pattern =
            """((?:(?:http|https|Http|Https|rtsp|Rtsp)://(?:(?:[a-zA-Z0-9${'$'}\-\_\.\+\!\*\'\(\)\,\;\?\&\=]|(?:%[a-fA-F0-9]{2})){1,64}(?::(?:[a-zA-Z0-9${'$'}\-\_\.\+\!\*\'\(\)\,\;\?\&\=]|(?:%[a-fA-F0-9]{2})){1,25})?@)?)?(?:(?:(?:[a-zA-Z0-9\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF\_][a-zA-Z0-9\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF\_\-]{0,64}\.)+(?:(?:aero|arpa|asia|a[cdefgilmnoqrstuwxz])|(?:biz|b[abdefghijmnorstvwyz])|(?:cat|com|coop|c[acdfghiklmnoruvxyz])|d[ejkmoz]|(?:edu|e[cegrstu])|f[ijkmor]|(?:gov|g[abdefghilmnpqrstuwy])|h[kmnrtu]|(?:info|int|i[delmnoqrst])|(?:jobs|j[emop])|k[eghimnprwyz]|l[abcikrstuvy]|(?:mil|mobi|museum|m[acdeghklmnopqrstuvwxyz])|(?:name|net|n[acefgilopruz])|(?:org|om)|(?:pro|p[aefghklmnrstwy])|qa|r[eosuw]|s[abcdeghijklmnortuvyz]|(?:tel|travel|t[cdfghjklmnoprtvwz])|u[agksyz]|v[aceginu]|w[fs]|(?:\u03B4\u03BF\u03BA\u03B9\u03BC\u03AE|\u0438\u0441\u043F\u044B\u0442\u0430\u043D\u0438\u0435|\u0440\u0444|\u0441\u0440\u0431|\u05D8\u05E2\u05E1\u05D8|\u0622\u0632\u0645\u0627\u06CC\u0634\u06CC|\u0625\u062E\u062A\u0628\u0627\u0631|\u0627\u0644\u0627\u0631\u062F\u0646|\u0627\u0644\u062C\u0632\u0627\u0626\u0631|\u0627\u0644\u0633\u0639\u0648\u062F\u064A\u0629|\u0627\u0644\u0645\u063A\u0631\u0628|\u0627\u0645\u0627\u0631\u0627\u062A|\u0628\u06BE\u0627\u0631\u062A|\u062A\u0648\u0646\u0633|\u0633\u0648\u0631\u064A\u0629|\u0641\u0644\u0633\u0637\u064A\u0646|\u0642\u0637\u0631|\u0645\u0635\u0631|\u092A\u0930\u0940\u0915\u094D\u0937\u093E|\u092D\u093E\u0930\u0924|\u09AD\u09BE\u09B0\u09A4|\u0A2D\u0A3E\u0A30\u0A24|\u0AAD\u0ABE\u0AB0\u0AA4|\u0B87\u0BA8\u0BCD\u0BA4\u0BBF\u0BAF\u0BBE|\u0B87\u0BB2\u0B99\u0BCD\u0B95\u0BC8|\u0B9A\u0BBF\u0B99\u0BCD\u0B95\u0BAA\u0BCD\u0BAA\u0BC2\u0BB0\u0BCD|\u0BAA\u0BB0\u0BBF\u0B9F\u0BCD\u0B9A\u0BC8|\u0C2D\u0C3E\u0C30\u0C24\u0C4D|\u0DBD\u0D82\u0D9A\u0DCF|\u0E44\u0E17\u0E22|\u30C6\u30B9\u30C8|\u4E2D\u56FD|\u4E2D\u570B|\u53F0\u6E7E|\u53F0\u7063|\u65B0\u52A0\u5761|\u6D4B\u8BD5|\u6E2C\u8A66|\u9999\u6E2F|\uD14C\uC2A4\uD2B8|\uD55C\uAD6D|xn--0zwm56d|xn--11b5bs3a9aj6g|xn--3e0b707e|xn--45brj9c|xn--80akhbyknj4f|xn--90a3ac|xn--9t4b11yi5a|xn--clchc0ea0b2g2a9gcd|xn--deba0ad|xn--fiqs8s|xn--fiqz9s|xn--fpcrj9c3d|xn--fzc2c9e2c|xn--g6w251d|xn--gecrj9c|xn--h2brj9c|xn--hgbk6aj7f53bba|xn--hlcj6aya9esc7a|xn--j6w193g|xn--jxalpdlp|xn--kgbechtv|xn--kprw13d|xn--kpry57d|xn--lgbbat1ad8j|xn--mgbaam7a8h|xn--mgbayh7gpa|xn--mgbbh1a71e|xn--mgbc0a9azcg|xn--mgberp4a5d4ar|xn--o3cw4h|xn--ogbpf8fl|xn--p1ai|xn--pgbs0dh|xn--s9brj9c|xn--wgbh1c|xn--wgbl6a|xn--xkc2al3hye2a|xn--xkc2dl3a5ee0h|xn--yfro4i67o|xn--ygbi2ammx|xn--zckzah|xxx)|y[et]|z[amw]))|(?:(?:25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9])\.(?:25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\.(?:25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\.(?:25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[0-9])))(?::\d{1,5})?(?:/(?:(?:[a-zA-Z0-9\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF\;\/\?\:\@\&\=\#\~\-\.\+\!\*\'\(\)\,\_])|(?:%[a-fA-F0-9]{2}))*)?)(?:\b|${'$'})""".trimMargin()
        val matches = Regex(pattern).findAll(text)
        return matches.map { it.groupValues[1] }.toList().map { "\"$it\"" }.toString()
    }

    /**
     * Get inbox(direct messages) of user(self)
     */
    fun getInbox(): Flow<JSONObject> = flow {
        api.getInboxV2()
        api.lastJSON?.read<JSONObject>("$.inbox")?.read<JSONArray>("$.threads")?.forEach {
            emit(it as JSONObject)
        }
    }

    /**
     * Get all pending message requests of user(self)
     */
    fun getPendingThreadRequests(): Flow<JSONObject> {
        return api.getPendingThreads()
    }

    /**
     * Approve given pending message request of user(self)
     */
    private fun approvePendingThreadRequest(threadId: String): Boolean {
        return api.approvePendingThread(threadId)
    }

    /**
     * Hide given pending message request of user(self)
     */
    private fun hidePendingThreadRequest(threadId: String): Boolean {
        return api.hidePendingThread(threadId)
    }

    /**
     * Reject given pending message request of user(self)
     */
    private fun rejectPendingThreadRequest(threadId: String): Boolean {
        return api.rejectPendingThread(threadId)
    }

    /**
     * Approve all pending message request of user(self)
     */
    suspend fun approveAllPendingThreadRequests(): Flow<String> = flow {
        getPendingThreadRequests().collect {
            val threadId = it?.read<Long>("$.thread_id").toString()
            if (approvePendingThreadRequest(threadId)) {
                emit(threadId)
            } else {
                return@collect
            }
        }
    }

    /**
     * Hide all pending message request of user(self)
     */
    suspend fun hideAllPendingThreadRequests(): Flow<String> = flow {
        getPendingThreadRequests().collect {
            val threadId = it?.read<Long>("$.thread_id").toString()
            if (hidePendingThreadRequest(threadId)) {
                emit(threadId)
            } else {
                return@collect
            }
        }
    }

    /**
     * Reject all pending message request of user(self)
     */
    suspend fun rejectAllPendingThreadRequests(): Flow<String> = flow {
        getPendingThreadRequests().collect {
            val threadId = it?.read<Long>("$.thread_id").toString()
            if (rejectPendingThreadRequest(threadId)) {
                emit(threadId)
            } else {
                return@collect
            }
        }
    }

    /**
     * Generic method to send direct message (dm) action
     * List of single user will send message to given user,
     * List of multiple users will create group and send message to group)
     */
    private suspend fun sendDirectMessage(
            itemType: ITEMTYPE, usernames: List<String>, text: String? = null,
            mediaId: String? = null, hashTag: String? = null, profileId: String? = null,
            filePath: String? = null, threadId: String = ""
    ): Boolean {
        sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
        println("Waiting $sleepTimeInMillis seconds before sending direct message(${itemType}) to user(s)")
        delay(sleepTimeInMillis * 1000L)

        when (itemType) {
            ITEMTYPE.TEXT -> {
                val urls = extractURL(text!!)
                val type = if (urls != "[]") "link" else "text"

                api.sendDirectItem(
                    itemType = type, users = usernames.map { convertToUserId(it) },
                    options = mapOf("text" to text, "urls" to urls, "threadId" to threadId)
                )
            }

            ITEMTYPE.MEDIA -> {
                val media = getMediaInfo(mediaId!!)
                val mediaTye = media?.get("media_type").toString()
                val mediaID = media?.get("id").toString()

                api.sendDirectItem(
                    itemType = "media_share", users = usernames.map { convertToUserId(it) },
                    options = mapOf(
                        "text" to text!!, "threadId" to threadId,
                        "media_type" to mediaTye, "media_id" to mediaID
                    )
                )
            }

            ITEMTYPE.HASHTAG -> {
                api.sendDirectItem(
                    itemType = "hashtag", users = usernames.map { convertToUserId(it) },
                    options = mapOf("text" to text!!, "threadId" to threadId, "hashtag" to hashTag!!)
                )
            }

            ITEMTYPE.PROFILE -> {
                api.sendDirectItem(
                    itemType = "profile", users = usernames.map { convertToUserId(it) },
                    options = mapOf("text" to text!!, "threadId" to threadId, "profile_user_id" to profileId!!)
                )
            }

            ITEMTYPE.LIKE -> {
                api.sendDirectItem(
                    itemType = "like", users = usernames.map { convertToUserId(it) },
                    options = mapOf("threadId" to threadId)
                )
            }

            ITEMTYPE.PHOTO -> {
                api.sendDirectItem(
                    itemType = "photo", users = usernames.map { convertToUserId(it) },
                    options = mapOf("filePath" to filePath!!, "threadId" to threadId)
                )
            }
        }


        println(api.lastJSON)
        return when {
            api.lastJSON?.read<String>("$.message") == "feedback_required" -> {
                println("Direct message action is blocked by Instagram. Please try again later.")
                blockAction[ACTIONS.MESSAGES] = true
                writeBlockActionToFile()
                false
            }
            api.lastJSON?.read<String>("$.status") == "ok" -> {
                val content = "Sent direct message(${itemType}) to user(s): ${usernames.joinToString(" ")}"
                totalActionPerformed[ACTIONS.MESSAGES] = totalActionPerformed[ACTIONS.MESSAGES]!!.plus(1)
                writeTotalActionToFile()
                writeActionLog(FILES.MESSAGES_FILE, content)
                true
            }
            else -> {
                false
            }
        }
    }

    /**
     * Send direct messages to given users individually or in group
     */

    fun sendDirectMessageToUsers(usernames: List<String>, text: String, toGroup: Boolean = false): Flow<String> = flow {
        if (toGroup) {
            if (blockedAction(ACTIONS.MESSAGES) || reachedLimit(ACTIONS.MESSAGES)) {
                return@flow
            }
            if (sendDirectMessage(itemType = ITEMTYPE.TEXT, usernames = usernames, text = text)) {
                emit(usernames.joinToString(" "))
            } else {
                return@flow
            }
        } else {
            usernames.forEach {
                if (blockedAction(ACTIONS.MESSAGES) || reachedLimit(ACTIONS.MESSAGES)) {
                    return@flow
                }
                if (sendDirectMessage(itemType = ITEMTYPE.TEXT, usernames = listOf(it), text = text)) {
                    emit(it)
                } else {
                    return@flow
                }
            }
        }
    }

    /**
     * Send direct media to given users individually or in group
     */
    fun sendDirectMediaToUsers(
            usernames: List<String>,
            mediaId: String,
            text: String,
            toGroup: Boolean = false
    ): Flow<String> = flow {
        if (toGroup) {
            if (blockedAction(ACTIONS.MESSAGES) || reachedLimit(ACTIONS.MESSAGES)) {
                return@flow
            }
            if (sendDirectMessage(itemType = ITEMTYPE.MEDIA, usernames = usernames, text = text, mediaId = mediaId)) {
                emit(usernames.joinToString(" "))
            } else {
                return@flow
            }
        } else {
            usernames.forEach {
                if (blockedAction(ACTIONS.MESSAGES) || reachedLimit(ACTIONS.MESSAGES)) {
                    return@flow
                }
                if (sendDirectMessage(itemType = ITEMTYPE.MEDIA, usernames = listOf(it), text = text, mediaId = mediaId)) {
                    emit(it)
                } else {
                    return@flow
                }
            }
        }
    }


    /**
     * Send direct hashtag to given users individually or in group
     */
    fun sendDirectHashTagToUsers(
            usernames: List<String>,
            hashTag: String,
            text: String,
            toGroup: Boolean = false
    ): Flow<String> = flow {
        if (toGroup) {
            if (blockedAction(ACTIONS.MESSAGES) || reachedLimit(ACTIONS.MESSAGES)) {
                return@flow
            }
            if (sendDirectMessage(itemType = ITEMTYPE.HASHTAG, usernames = usernames, text = text, hashTag = hashTag)) {
                emit(usernames.joinToString(" "))
            } else {
                return@flow
            }
        } else {
            usernames.forEach {
                if (blockedAction(ACTIONS.MESSAGES) || reachedLimit(ACTIONS.MESSAGES)) {
                    return@flow
                }
                if (sendDirectMessage(itemType = ITEMTYPE.HASHTAG, usernames = listOf(it), text = text, hashTag = hashTag)) {
                    emit(it)
                } else {
                    return@flow
                }
            }
        }
    }

    /**
     * Send direct profile to given users individually or in group
     */
    fun sendDirectProfileToUsers(
            usernames: List<String>,
            profileId: String,
            text: String,
            toGroup: Boolean = false
    ): Flow<String> = flow {
        if (toGroup) {
            if (blockedAction(ACTIONS.MESSAGES) || reachedLimit(ACTIONS.MESSAGES)) {
                return@flow
            }
            if (sendDirectMessage(itemType = ITEMTYPE.PROFILE, usernames = usernames, text = text, profileId = profileId)) {
                emit(usernames.joinToString(" "))
            } else {
                return@flow
            }
        } else {
            usernames.forEach {
                if (blockedAction(ACTIONS.MESSAGES) || reachedLimit(ACTIONS.MESSAGES)) {
                    return@flow
                }
                if (sendDirectMessage(
                            itemType = ITEMTYPE.PROFILE,
                            usernames = listOf(it),
                            text = text,
                            profileId = profileId
                        )
                ) {
                    emit(it)
                } else {
                    return@flow
                }
            }
        }
    }

    /**
     * Send direct like to given users individually or in group
     */
    fun sendDirectLikeToUsers(usernames: List<String>, toGroup: Boolean = false): Flow<String> = flow {
        if (toGroup) {
            if (blockedAction(ACTIONS.MESSAGES) || reachedLimit(ACTIONS.MESSAGES)) {
                return@flow
            }
            if (sendDirectMessage(itemType = ITEMTYPE.LIKE, usernames = usernames)) {
                emit(usernames.joinToString(" "))
            } else {
                return@flow
            }
        } else {
            usernames.forEach {
                if (blockedAction(ACTIONS.MESSAGES) || reachedLimit(ACTIONS.MESSAGES)) {
                    return@flow
                }
                if (sendDirectMessage(itemType = ITEMTYPE.LIKE, usernames = listOf(it))) {
                    emit(it)
                } else {
                    return@flow
                }
            }
        }
    }


    /**
     * Send direct photo to given users individually or in group
     */
    fun sendDirectPhotoToUsers(usernames: List<String>, filePath: String, toGroup: Boolean = false): Flow<String> =
        flow {
            if (toGroup) {
                if (blockedAction(ACTIONS.MESSAGES) || reachedLimit(ACTIONS.MESSAGES)) {
                    return@flow
                }
                if (sendDirectMessage(itemType = ITEMTYPE.PHOTO, usernames = usernames, filePath = filePath)) {
                    emit(usernames.joinToString(" "))
                } else {
                    return@flow
                }
            } else {
                usernames.forEach {
                    if (blockedAction(ACTIONS.MESSAGES) || reachedLimit(ACTIONS.MESSAGES)) {
                        return@flow
                    }
                    if (sendDirectMessage(itemType = ITEMTYPE.PHOTO, usernames = listOf(it), filePath = filePath)) {
                        emit(it)
                    } else {
                        return@flow
                    }
                }
            }
        }
    //endregion


    //region Block/Unblock related methods
    /**
     * Generic method to perform block action
     */
    private suspend fun block(userId: String): Boolean {
        sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
        println("Waiting $sleepTimeInMillis seconds before blocking user")
        delay(sleepTimeInMillis * 1000L)

        api.block(convertToUserId(userId))
        return when {
            api.lastJSON?.read<String>("$.message") == "feedback_required" -> {
                println("Block action is blocked by Instagram. Please try again later.")
                blockAction[ACTIONS.BLOCKS] = true
                writeBlockActionToFile()
                false
            }
            api.lastJSON?.read<String>("$.status") == "ok" -> {
                val content = "Blocked user ID:$userId username:${getUserNameById(userId)}"
                println(content)
                totalActionPerformed[ACTIONS.BLOCKS] = totalActionPerformed[ACTIONS.BLOCKS]!! + 1
                writeTotalActionToFile()
                writeActionLog(FILES.BLOCKS_FILE, content)
                true
            }
            else -> {
                false
            }
        }
    }

    /**
     * Block given users
     */
    fun blockUsers(usernames: List<String>): Flow<String> = flow {
        usernames.forEach {
            if (blockedAction(ACTIONS.BLOCKS) || reachedLimit(ACTIONS.BLOCKS)) {
                return@flow
            }
            if (block(it)) {
                emit(it)
            } else {
                return@flow
            }
        }
    }

    /**
     * Generic method to perform un-block action
     */
    private suspend fun unblock(userId: String): Boolean {
        sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
        println("Waiting $sleepTimeInMillis seconds before unblocking user")
        delay(sleepTimeInMillis * 1000L)

        return if (api.unblock(convertToUserId(userId))) {
            val content = "Unblocked user ID:$userId username:${getUserNameById(userId)}"
            println(content)
            totalActionPerformed[ACTIONS.UNBLOCKS] = totalActionPerformed[ACTIONS.UNBLOCKS]!! + 1
            writeTotalActionToFile()
            writeActionLog(FILES.UNBLOCKS_FILE, content)
            true
        } else {
            println("Failed to unblock or user is already unblocked")
            blockAction[ACTIONS.UNBLOCKS] = true
            writeBlockActionToFile()
            false
        }
    }

    /**
     * Un-block(previously blocked) given users
     */
    fun unblockUsers(usernames: List<String>): Flow<String> = flow {
        usernames.forEach {
            if (blockedAction(ACTIONS.UNBLOCKS) || reachedLimit(ACTIONS.UNBLOCKS)) {
                return@flow
            }
            if (unblock(it)) {
                emit(it)
            } else {
                return@flow
            }
        }
    }
    //endregion


    //region Archive/Unarchive related methods
    /**
     * Generic method to perform archive(save media to collection) action
     */
    private fun archive(mediaId: String, undo: Boolean = false): Boolean {
        val media = getMediaInfo(mediaId)
        val mediaType = media?.read<Int>("$.media_type")!!
        if (api.archiveMedia(mediaId, mediaType, undo)) {
            if (!undo) {
                totalActionPerformed[ACTIONS.ARCHIVED] = totalActionPerformed[ACTIONS.ARCHIVED]!! + 1
            } else {
                totalActionPerformed[ACTIONS.UNARCHIVED] = totalActionPerformed[ACTIONS.UNARCHIVED]!! + 1
            }
            return true
        }
        return false
    }

    /**
     * Archive given medias
     */
    fun archiveMedias(mediaIds: List<String>): Flow<String> = flow {
        mediaIds.forEach {
            if (blockedAction(ACTIONS.ARCHIVED) || reachedLimit(ACTIONS.ARCHIVED)) {
                return@flow
            }
            if (archive(it, undo = false)) {
                emit(it)
            } else {
                return@flow
            }
        }
    }

    /**
     * Generic method to perform Un-archive(unsave media from collection) action
     */
    fun unArchiveMedias(mediaIds: List<String>): Flow<String> = flow {
        mediaIds.forEach {
            if (blockedAction(ACTIONS.UNARCHIVED) || reachedLimit(ACTIONS.UNARCHIVED)) {
                return@flow
            }
            if (archive(it, undo = true)) {
                emit(it)
            } else {
                return@flow
            }
        }
    }
    //endregion


    //region Pending follow requests related methods
    /**
     * Get all pending follow requests of user(self)
     */
    fun getPendingFollowRequests(): Flow<JSONObject> = flow {
        api.getPendingFriendRequests()
        api.lastJSON?.read<JSONArray>("$.users")?.forEach { emit(it as JSONObject) }
    }

    /**
     * Approve pending follow request of given user
     */
    fun approvePendingFollowRequest(username: String): Boolean {
        return api.approvePendingFollowRequest(convertToUserId(username))
    }

    /**
     * Reject pending follow request of given user
     */
    fun rejectPendingFollowRequest(username: String): Boolean {
        return api.rejectPendingFollowRequest(convertToUserId(username))
    }

    /**
     * Approve all pending follow requests
     */
    suspend fun approveAllPendingFollowRequests(): Flow<String> = flow {
        getPendingFollowRequests().collect {
            val username = it.read<String>("$.username")!!
            if (approvePendingFollowRequest(username)) {
                emit(username)
            }
        }
    }

    /**
     * Reject all pending follow requests
     */
    suspend fun rejectAllPendingFollowRequests(): Flow<String> = flow {
        getPendingFollowRequests().collect {
            val username = it.read<String>("$.username")!!
            if (rejectPendingFollowRequest(username)) {
                emit(username)
            }
        }
    }
    //endregion


    //region Delete Media/Comment related methods
    /**
     * Delete media by given media id
     */
    private fun deleteMedia(mediaId: String): Boolean {
        return api.deleteMedia(mediaId)
    }

    /**
     * Delete list of given medias
     */
    fun deleteMedias(mediaIds: List<String>): Flow<String> = flow {
        mediaIds.forEach {
            if (deleteMedia(it)) {
                emit(it)
            } else {
                return@flow
            }
        }
    }

    /**
     * Delete comment by given comment of given media
     */
    fun deleteComment(mediaId: String, commentId: String): Boolean {
        return api.deleteComment(mediaId, commentId)
    }
    //endregion


    //region Download media related methods
    /**
     * Get url and description of given media
     * It will return 3 values.
     * URL of media
     * Caption of media
     * Boolean indicating media is photo or video. true if media is photo, false if it's video
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

    /**
     * Generic method to download given media (story, photo, video)
     */
    suspend fun downloadMedia(url: String, username: String, folderName: String, fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            api.downloadMedia(url, username, folderName, fileName)
        }
    }

    /**
     * download stories of given user
     */
    fun downloadUserStories(username: String): Flow<String> = flow {
        getUserStoriesURL(username).collect {
            val filename = it.first.split("/").last().split(".").first()
            if (downloadMedia(it.first, username, "stories", "$filename.${it.second}")) {
                emit("$filename.${it.second}")
            }
        }
    }

    /**
     * Download given numbers of media of given users
     */
    suspend fun downloadUserMedias(
            username: String,
            amountOfMedias: Int,
            saveCaption: Boolean = false
    ): Flow<String> =
        flow {
            var needToSaveCaption = saveCaption
            getMediasByUser(username, amountOfMedias).collect { it ->
                getMediaURLAndDescription(it.read<Long>("$.pk").toString(), saveCaption).collect {
                    val filename = it.first.split("/").last().split(".").first()
                    val folderName = if (it.third) "photos" else "videos"
                    val fileType = if (it.third) ".jpg" else ".mp4"
                    if (downloadMedia(it.first, username, folderName, "$filename$fileType")) {
                        if (needToSaveCaption) {
                            File("$folderName/$username", "$filename.txt").printWriter().use { out ->
                                out.print(it.second)
                            }
                            needToSaveCaption = false
                        }
                        emit("$filename$fileType")
                    }
                }
                needToSaveCaption = saveCaption
            }
        }
    //endregion


    //region Stories related methods
    /**
     * Get url of stories of given user
     */
    private fun getUserStoriesURL(username: String): Flow<Pair<String, String>> = flow {
        api.getUserReel(convertToUserId(username))
        api.lastJSON?.read<Int>("$.media_count")?.let { it ->
            if (it > 0) {
                val items = api.lastJSON?.read<JSONArray>("$.items")
                items?.forEach {
                    if ((it as JSONObject).read<Int>("$.media_type") == 1) {
                        (it?.read<JSONObject>("$.image_versions2")?.read<JSONArray>("$.candidates")
                            ?.first() as JSONObject)?.read<String>(
                            "$.url"
                        )?.let {
                            emit(Pair(it, "jpg"))
                        }
                    } else if (it.read<Int>("$.media_type") == 2) {
                        (it?.read<JSONArray>("$.video_versions")?.first() as JSONObject)?.read<String>("$.url")
                            ?.let { emit(Pair(it, "mp4")) }
                    }
                }
            }
        }
    }

    /**
     * Get all info about stories of given users
     */
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

    /**
     * Get all items of stories of given users individually
     */
    fun getUsersStoriesItems(usernames: List<String>): Flow<JSONObject> = flow {
        getUsersStories(usernames).collect {
            it?.read<JSONArray>("$.items")?.forEach {
                emit(it as JSONObject)
            }
        }
    }

    /**
     * Get list of story viewers of user(self)
     */
    fun getSelfStoryViewers(): Flow<JSONObject> = flow {
        getUsersStoriesItems(listOf(username)).collect { it ->
            api.getSelfStoryViewers(it.get("id").toString())
            api.lastJSON?.read<JSONArray>("$.users")?.forEach {
                emit(it as JSONObject)
            }
        }
    }

    /**
     * Generic method to watch stories of given users
     * It will use randomly generated amount of users in each iteration to limit getting amount of stories
     */
    suspend fun watchUsersStories(usernames: List<String>): Flow<Int> = flow {
        val numberOfUsersInSingleIteration = Random.nextInt(20, 30)

        for (i in (usernames.indices) step numberOfUsersInSingleIteration) {
            val subList = if ((i + numberOfUsersInSingleIteration) <= usernames.size) {
                usernames.subList(i, i + numberOfUsersInSingleIteration)
            } else {
                usernames.subList(i, usernames.size)
            }

            val sleepTimeInMillis = Random.nextInt(minSleepTime, maxSleepTime)
            println("Sleeping $sleepTimeInMillis seconds before watching new stories")
            delay(sleepTimeInMillis * 1000L)

            val unseenReels = mutableListOf<JSONObject>()
            getUsersStories(subList).collect { it ->
                val lastReelSeenAt = if (it.has("seen")) it.read<Long>("$.seen")!! else 0
                it.read<JSONArray>("$.items")?.forEach {
                    if ((it as JSONObject).read<Long>("$.taken_at")!! > lastReelSeenAt) {
                        unseenReels.add(it)
                    }
                }
            }

            if (unseenReels.size == 0) return@flow

            println("Going to watch ${unseenReels.size} stories of $numberOfUsersInSingleIteration users")
            api.watchReels(reels = unseenReels)
            totalActionPerformed[ACTIONS.STORIES_VIEWED] =
                totalActionPerformed[ACTIONS.STORIES_VIEWED]!!.plus(unseenReels.size)
            writeTotalActionToFile()
            writeActionLog(FILES.STORIES_FILE, "Watched ${unseenReels.size} stories")
            emit(unseenReels.size)
        }
    }

    /**
     * Watch stories of users appeared in explore page
     */
    suspend fun watchExploreTabUsersStories(): Flow<Int> {
        val users = getAllUsersByExplorePage().map { it.get("pk").toString() }.toList()
        return watchUsersStories(users)
    }

    /**
     * Watch stories of users having given location
     */
    suspend fun watchLocationUsersStories(
            locationName: String,
            amountOfUsers: Int,
            amountOfLocation: Int = 5
    ): Flow<Int> {
        val users = getAllUsersByLocation(locationName, amountOfUsers, amountOfLocation).map { it.get("pk").toString() }
            .toList()
        return watchUsersStories(users)
    }
    //endregion


    //region Miscellaneous methods
    /**
     * Get list of locations matches to given location
     */
    private fun searchLocations(locationName: String, amount: Int = 5): Flow<JSONObject> = flow {
        api.searchLocations(locationName, amount)
        api.lastJSON?.read<JSONArray>("$.items")?.forEach { it ->
            (it as JSONObject)?.read<JSONObject>("$.location")?.let { emit(it) }
        }
    }
    //endregion
}