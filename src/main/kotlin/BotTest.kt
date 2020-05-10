import bot.InstagramBot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

@InternalCoroutinesApi
@ExperimentalCoroutinesApi
fun main() = runBlocking  {

    val username = "your_instagram_username"
    val password = "your_instagram_password"

    val bot = InstagramBot()
    bot.prepare(username, password)
    bot.login()

    /**
     * Minimum and maximum time to sleep before performing next action.
     * Actual time will be randomly generated each time between min and max time
     */

    bot.minSleepTime = 300
    bot.maxSleepTime = 500

    bot.getUserFollowers("user_name", 100, isUsername = true).collect { println(it) }
    bot.getSelfFollowing(Int.MAX_VALUE).collect { println(it) }
    bot.getExploreTabMedias(7).collect { println(it) }
    bot.getMediasByLocation("location_name", 5).collect { println(it) }
    bot.likeHashTagMedias("hashtag_name", 5).collect { println(it) }
    bot.commentHashTagMedias("hashtag_name", "This is an example of nice comment",5).collect { println(it) }
    bot.approveAllPendingFollowRequests().collect { println(it) }
    bot.commentTimelineMedias("This is an example of nice comment", 5)
    bot.downloadUserStories("user_name").collect { println(it) }
    bot.followUsers(listOf("user_name_1", "user_name_2")).collect { println(it) }
}