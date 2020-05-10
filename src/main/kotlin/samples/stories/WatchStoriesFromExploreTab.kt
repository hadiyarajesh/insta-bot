package samples.stories

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

    val howManyUsersYouWantToWatchStories = 10

    val usersFromExploreTab = mutableListOf<String>()
    bot.getExploreTabUsers(howManyUsersYouWantToWatchStories).collect {
        usersFromExploreTab.add(it?.read<String>("$.username").toString())
    }

    println(bot.watchUsersStories(usersFromExploreTab))
}