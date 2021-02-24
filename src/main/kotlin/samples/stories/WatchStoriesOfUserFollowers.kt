package samples.stories

import Credentials
import bot.InstagramBot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

@ExperimentalCoroutinesApi
fun main() = runBlocking {

    val username = Credentials.USERNAME
    val password = Credentials.PASSWORD

    val bot = InstagramBot()
    bot.prepare(username)
    bot.login(username, password)


    val user = "enter_username_whose_followers_stories_you_want_to_watch"
    val howManyFollowersYouWantWatchStories = 10

    bot.watchUsersStories(
        bot.getUserFollowers(user, howManyFollowersYouWantWatchStories).toList()
    ).collect { println(it) }
}