package samples.stories

import bot.InstagramBot
import com.nfeld.jsonpathlite.extension.read
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

@ExperimentalCoroutinesApi
fun main() = runBlocking {

    val username = "your_instagram_username"
    val password = "your_instagram_password"

    val bot = InstagramBot()
    bot.prepare(username, password)
    bot.login()


    val user = "enter_username_whose_followers_stories_you_want_to_watch"
    val howManyFollowersYouWantWatchStories = 10

    val users = bot.getUserFollowers(user, howManyFollowersYouWantWatchStories, isUsername = true).toList()

    println(bot.watchUsersStories(users))
}