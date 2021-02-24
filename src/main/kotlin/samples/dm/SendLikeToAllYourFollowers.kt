package samples.dm

import bot.InstagramBot
import com.nfeld.jsonpathlite.extension.read
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

    val yourFollowers = bot.getSelfFollowers().toList()
    bot.sendDirectLikeToUsers(yourFollowers).collect { println(it) }
}