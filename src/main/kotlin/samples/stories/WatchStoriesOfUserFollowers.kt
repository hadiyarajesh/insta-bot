package samples.stories

import bot.InstagramBot
import com.nfeld.jsonpathlite.extension.read
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

@ExperimentalCoroutinesApi
fun main() {

    val username = "your_instagram_username"
    val password = "your_instagram_password"

    val bot = InstagramBot()
    bot.prepare(username)
    bot.login(username, password)


    val user = "enter_username_whose_followers_stories_you_want_to_watch"
    val howManyFollowersYouWantWatchStories = 10

    runBlocking {
        bot.watchUsersStories(bot.getUserFollowers(user, howManyFollowersYouWantWatchStories).toList()).collect { println(it) }
    }
}