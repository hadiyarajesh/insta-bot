package samples.stories

import bot.InstagramBot
import com.nfeld.jsonpathlite.extension.read
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

@ExperimentalCoroutinesApi
fun main() {

    val username = "your_instagram_username"
    val password = "your_instagram_password"

    val bot = InstagramBot()
    bot.prepare(username)
    bot.login(username, password)

    val locationName = "enter_location_name_here"
    val howManyUsersYouWantToWatchStories = 10

    runBlocking {
        bot.watchLocationUsersStories(locationName, howManyUsersYouWantToWatchStories).collect { println(it) }
    }
}