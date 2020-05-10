package samples.follow

import bot.InstagramBot
import com.nfeld.jsonpathlite.extension.read
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

    val locationName = "enter_location_name_here"
    val howManyFollowersYouWantToFollow = 10

    val usersByLocation = mutableListOf<String>()
    bot.getUsersByLocation(locationName, howManyFollowersYouWantToFollow).collect {
        usersByLocation.add(it?.read<String>("$.username").toString())
    }

    bot.followUsers(usersByLocation).collect { println(it) }
}