package samples.dm

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

    val yourFollowers = bot.getSelfFollowers(Int.MAX_VALUE, isUsername = true).toList()

    bot.sendLikeToUsersIndividually(yourFollowers).collect { println(it) }
}