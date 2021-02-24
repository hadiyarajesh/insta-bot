import bot.InstagramBot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

@InternalCoroutinesApi
@ExperimentalCoroutinesApi
fun main() = runBlocking {

    val username = Credentials.USERNAME
    val password = Credentials.PASSWORD

    val bot = InstagramBot()
    bot.prepare(username)
    bot.login(username, password)

    /**
     * Minimum and maximum time (in seconds) to wait before performing next action.
     * Actual time will be randomly generated each time between min and max time
     */

    bot.minSleepTime = 60
    bot.maxSleepTime = 120

    // Provide your comment list here
    val commentsList = listOf("Sample comment 1", "Sample comment 2")
    // Provide your username list here
    val userList = listOf("user_name_1", "user_name_2")

    // Get all following of you
    bot.getSelfFollowing(Int.MAX_VALUE).collect { println(it) }
    // Get 100 followers of given username
    bot.getUserFollowers("enter_user_name_here", 100, isUsername = true).collect { println(it) }
    // Like 5 medias from explore page
    bot.likeMediasByExplorePage(5).collect { println(it) }
    // Comment 5 medias having given hashtag and given comment list
    bot.commentMediasByHashTag("enter_hashtag_name_here", commentsList, 5).collect { println(it) }
    // Follow given list of users
    bot.followUsers(userList).collect { println(it) }
    // Approve all pending follow requests
    bot.approveAllPendingFollowRequests().collect { println(it) }
    // Watch stories of 200 users based on given location
    bot.watchLocationUsersStories("enter_location_name_here", 200).collect { println(it) }
    // Download latest 5 medias of given user along with caption
    bot.downloadUserMedias("enter_user_name_here", 5, true).collect { println(it) }
}