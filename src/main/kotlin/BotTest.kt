import bot.InstagramBot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

@InternalCoroutinesApi
@ExperimentalCoroutinesApi
fun main() = runBlocking  {

    val username = "your_username"
    val password = "your_password"

    val bot = InstagramBot()
    bot.prepare(username, password)
    bot.login()

    bot.getUserFollowers("user_name", 100, isUsername = true).collect { println(it) }
    bot.getSelfFollowing(Int.MAX_VALUE).collect { println(it) }
    bot.getExploreTabMedias(7).collect { println(it) }
    bot.getMediasByLocation("location_name", 5).collect { println(it) }
    bot.likeHashTagMedias("cat", 5).collect { println(it) }
    bot.commentHashTagMedias("cat", "This is an exmaple of nice comment",5).collect { println(it) }
    bot.approveAllPendingFollowRequests().collect { println(it) }
    bot.commentTimelineMedias("This is an example of nice comment", 5)
    bot.downloadUserStories("username").collect { println(it) }
    bot.followUsers(listOf("username1", "username2")).collect { println(it) }
}