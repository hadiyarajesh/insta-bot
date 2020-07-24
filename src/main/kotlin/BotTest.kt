import bot.InstagramBot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

@InternalCoroutinesApi
@ExperimentalCoroutinesApi
fun main() {

    val username = "your_instagram_username"
    val password = "your_instagram_password"

    val bot = InstagramBot()
    bot.prepare(username)
    bot.login(username, password)

    /**
     * Minimum and maximum time to sleep before performing next action.
     * Actual time will be randomly generated each time between min and max time
     */

    bot.minSleepTime = 100
    bot.maxSleepTime = 200

    val commentsList = listOf("Sample comment 1", "Sample comment 2")
    val userList = listOf("username1", "username2")

    runBlocking {
        // GEt your own followers
        bot.getSelfFollowing(Int.MAX_VALUE).collect { println(it) }
        // Get 100 followers of given username
        bot.getUserFollowers("user_name", 100, isUsername = true).collect { println(it) }
        // Like 5 medias from explore page
        bot.likeMediasByExplorePage(5).collect { println(it) }
        // Comment 5 medias having given hashtag and given comment list
        bot.commentMediasByHashTag("hashtag_name", commentsList, 5).collect { println(it) }
        // Follow given list of users
        bot.followUsers(userList).collect { println(it) }
        // Approve all pending follow requests
        bot.approveAllPendingFollowRequests().collect { println(it) }
        // Watch stories of 200 users based on given location
        bot.watchLocationUsersStories("location_name", 200).collect { println(it) }
        // Download latest 5 medias of given username along with caption
        bot.downloadUserMedias("user_name", 5, true).collect { println(it) }
    }
}