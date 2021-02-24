package samples.follow

import Credentials
import bot.InstagramBot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

@ExperimentalCoroutinesApi
fun main() = runBlocking {

    val username = Credentials.USERNAME
    val password = Credentials.PASSWORD

    val bot = InstagramBot()
    bot.prepare(username)
    bot.login(username, password)

    val locationName = "enter_location_name_here"
    val howManyFollowersYouWantToFollow = 10

    bot.followUsers(
        bot.getUsersByLocation(locationName, howManyFollowersYouWantToFollow)
            .map { it.get("pk").toString() }.toList()
    ).collect { println(it) }
}