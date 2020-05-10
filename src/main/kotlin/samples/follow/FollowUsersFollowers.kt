package samples.follow

import bot.InstagramBot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

@ExperimentalCoroutinesApi
fun main() = runBlocking {

    val username = "your_instagram_username"
    val password = "your_instagram_password"

    val bot = InstagramBot()
    bot.prepare(username, password)
    bot.login()

    val user = "enter_username_whose_followers_you_want_to_follow_here"
    val howManyFollowersYouWantToFollow = 10

    bot.followUserFollowers(user, howManyFollowersYouWantToFollow, true).collect { println(it) }
}