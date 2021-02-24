package samples.follow

import Credentials
import bot.InstagramBot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

@ExperimentalCoroutinesApi
fun main() = runBlocking {

    val username = Credentials.USERNAME
    val password = Credentials.PASSWORD

    val bot = InstagramBot()
    bot.prepare(username)
    bot.login(username, password)

    val user = "enter_username_whose_followers_you_want_to_follow_here"
    val howManyFollowersYouWantToFollow = 10

    bot.followUserFollowers(user, howManyFollowersYouWantToFollow).collect { println(it) }
}